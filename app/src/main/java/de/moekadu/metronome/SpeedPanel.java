/*
 * Copyright 2019 Michael Moessner
 *
 * This file is part of Metronome.
 *
 * Metronome is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Metronome is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Metronome.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.moekadu.metronome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import androidx.annotation.Nullable;

import android.os.SystemClock;
import android.util.AttributeSet;
// import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

public class SpeedPanel extends ControlPanel {

    private Paint circlePaint;

    private float integratedDistance;
    private float previous_x;
    private float previous_y;
//    private int previous_speed;
    //final private int strokeWidth = dp_to_px(2);
    //final static public float innerRadiusRatio = 0.62f;
    private Path pathOuterCircle = null;
    private Path tapInPath = null;
    private Path plusStepPath = null;
    private Path minusStepPath = null;

    private boolean changingSpeed = false;

    private float speedSensitivity = InitialValues.speedSensitivity; // steps per cm
//    private boolean highlightTapIn = false;

    private int highlightColor;
    private int normalColor;
    private int labelColor;
    private int textColor;

    private final ValueAnimator tapInAnimation = ValueAnimator.ofFloat(0, 1);
    private float tapInAnimationValue = 1.0f;

    private final ValueAnimator backToZeroAnimation = ValueAnimator.ofFloat(1.0f, 0.0f);
    private float backToZeroAnimationValue = 1.0f;
//    private int numTapInTimes = 3;
//    private long[] tapInTimes;

    private long lastTap = 0;
    private long predictNextTap = 0;
    private int nTapSamples = 0;
    private float facTapInfty = 0.15f;
    private float maxTapErr = 0.3f;
    private long tapDelay = 10;
    private float dt;

    final private float tapInAngleStart = 60;
    final private float tapInAngleEnd = 120;

    private float plusStepAngleStart = -90;
    private float plusStepAngleEnd = 0;
    private float minusStepAngleStart = -180;
    private float minusStepAngleEnd = -90;

    private boolean plusStepInitiated;
    private boolean minusStepInitiated;

    private float stepCounter = 0;
    private float stepCounterMax = 5;

    private float speedIncrement = Utilities.speedIncrements[InitialValues.speedIncrementIndex];

    public interface SpeedChangedListener {
        void onSpeedChanged(float dSpeed);
        void onAbsoluteSpeedChanged(float newSpeed, long nextKlickTimeInMillis);
    }

    private SpeedChangedListener speedChangedListener;


    public SpeedPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
        speedChangedListener = null;

        ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int radius = getRadius();
                int cx = getCenterX();
                int cy = getCenterY();
                outline.setOval(cx - radius, cy - radius, cx + radius, cy + radius);
            }
        };
        setOutlineProvider(outlineProvider);

        tapInAnimation.setDuration(500);
        tapInAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                tapInAnimationValue = (float) animation.getAnimatedValue();
                invalidate();
            }
        });

        backToZeroAnimation.setDuration(100);
        backToZeroAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                backToZeroAnimationValue = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
//        tapInAnimation.setInterpolator(new LinearInterpolator());
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        circlePaint = new Paint();
        circlePaint.setAntiAlias(true);

        if(attrs == null)
            return;

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SpeedPanel);
        highlightColor = ta.getColor(R.styleable.SpeedPanel_highlightColor, Color.GRAY);
        normalColor = ta.getColor(R.styleable.SpeedPanel_normalColor, Color.GRAY);
        labelColor = ta.getColor(R.styleable.SpeedPanel_labelColor, Color.WHITE);
        textColor = ta.getColor(R.styleable.SpeedPanel_textColor, Color.BLACK);

        ta.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float maxShift = 10; // degree
//        double shiftSpeedStrokesAngle = maxShift * 2.0 / Math.PI * Math.atan(0.5 * stepCounter);
        double shiftSpeedStrokesAngle = maxShift * stepCounter / stepCounterMax * backToZeroAnimationValue;


        int radius = getRadius();
        int cx = getCenterX();
        int cy = getCenterY();

        int innerRadius = getInnerRadius();

        //circlePaint.setColor(foregroundColor);
        circlePaint.setColor(normalColor);

        //circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStyle(Paint.Style.FILL);
        //circlePaint.setStrokeWidth(strokeWidth);

        if(pathOuterCircle == null) {
            pathOuterCircle = new Path();
            tapInPath = new Path();
            plusStepPath = new Path();
            minusStepPath = new Path();
        }
        pathOuterCircle.setFillType(Path.FillType.EVEN_ODD);

        pathOuterCircle.rewind();
        pathOuterCircle.addCircle(cx, cy, radius, Path.Direction.CW);

        canvas.drawPath(pathOuterCircle, circlePaint);
        pathOuterCircle.rewind();

        if(changingSpeed) {
            circlePaint.setColor(highlightColor);
        }
        else {
            circlePaint.setColor(labelColor);
        }
        circlePaint.setStyle(Paint.Style.STROKE);
        float growthFactor = 1.1f;
        float speedRad = 0.5f* (radius + innerRadius);
        float strokeWidth = 0.3f * (radius - innerRadius);
        circlePaint.setStrokeWidth(strokeWidth);

        float angleMax  = -90.0f + 40.0f + (float) shiftSpeedStrokesAngle;
        float angleMin = -90.0f - 40.0f + (float) shiftSpeedStrokesAngle;
        float dAngle = 3.0f;

        float angle = angleMax -dAngle;

        while (angle >= angleMin) {
            canvas.drawArc(cx - speedRad, cy - speedRad, cx + speedRad, cy + speedRad, angle, -2f, false, circlePaint);

            dAngle *= growthFactor;
            angle -= dAngle;
        }

        circlePaint.setStyle(Paint.Style.FILL);

        float radArrI = speedRad - 0.5f * strokeWidth;
        float radArrO = speedRad + 0.5f * strokeWidth;
        double angleMinRad = angleMin * Math.PI / 180.0f;
        double dArrAngle = strokeWidth / speedRad;
        pathOuterCircle.moveTo(cx + radArrI * (float) Math.cos(angleMinRad), cy + radArrI * (float) Math.sin(angleMinRad));
        pathOuterCircle.lineTo(cx + radArrO * (float) Math.cos(angleMinRad), cy + radArrO * (float) Math.sin(angleMinRad));
        pathOuterCircle.lineTo(cx + speedRad * (float) Math.cos(angleMinRad-dArrAngle), cy + speedRad * (float) Math.sin(angleMinRad-dArrAngle));
        canvas.drawPath(pathOuterCircle, circlePaint);
        pathOuterCircle.rewind();

        double angleMaxRad = angleMax * Math.PI / 180.0f;
        pathOuterCircle.moveTo(cx + radArrI * (float) Math.cos(angleMaxRad), cy + radArrI * (float) Math.sin(angleMaxRad));
        pathOuterCircle.lineTo(cx + radArrO * (float) Math.cos(angleMaxRad), cy + radArrO * (float) Math.sin(angleMaxRad));
        pathOuterCircle.lineTo(cx + speedRad * (float) Math.cos(angleMaxRad+dArrAngle), cy + speedRad * (float) Math.sin(angleMaxRad+dArrAngle));
        canvas.drawPath(pathOuterCircle, circlePaint);

        tapInPath.rewind();
        tapInPath.addArc(cx - speedRad, cy - speedRad, cx + speedRad, cy + speedRad, 265, -350);

//        circlePaint.setStrokeWidth(1);
//        circlePaint.setStyle(Paint.Style.STROKE);
//        canvas.drawPath(tapInPath, circlePaint);
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setTextAlign(Paint.Align.CENTER);
//        final float tapInTextSize = Utilities.sp_to_px(22);
        final float tapInTextSize = strokeWidth;
        circlePaint.setTextSize(tapInTextSize);

        circlePaint.setColor(textColor);

        canvas.drawTextOnPath(getContext().getString(R.string.tap_in), tapInPath, 0, tapInTextSize/2.0f, circlePaint);

        plusStepPath.rewind();
        plusStepPath.addArc(cx - speedRad, cy - speedRad, cx + speedRad, cy + speedRad, angleMax+20, 180);
        circlePaint.setTextAlign(Paint.Align.LEFT);
        if(plusStepInitiated)
            circlePaint.setColor(highlightColor);
        else
            circlePaint.setColor(textColor);
        canvas.drawTextOnPath("+ "+Utilities.getBpmString(speedIncrement, speedIncrement), plusStepPath, 0, tapInTextSize/2.0f, circlePaint);

        minusStepPath.rewind();
        minusStepPath.addArc(cx - speedRad, cy - speedRad, cx + speedRad, cy + speedRad, angleMin-20-180, 180);
        circlePaint.setTextAlign(Paint.Align.RIGHT);
        if(minusStepInitiated)
            circlePaint.setColor(highlightColor);
        else
            circlePaint.setColor(textColor);
        canvas.drawTextOnPath("- "+Utilities.getBpmString(speedIncrement, speedIncrement), minusStepPath, 0, tapInTextSize/2.0f, circlePaint);

        if(tapInAnimationValue <= 0.99999) {
            circlePaint.setTextAlign(Paint.Align.CENTER);
            circlePaint.setColor(highlightColor);
            circlePaint.setAlpha(Math.round(255*(1-tapInAnimationValue)));
            float highlightTextSize = tapInTextSize * (1 + 10*tapInAnimationValue);
            circlePaint.setTextSize(highlightTextSize);
            canvas.drawTextOnPath(getContext().getString(R.string.tap_in), tapInPath, 0, tapInTextSize / 2.0f, circlePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int action = event.getActionMasked();
        float x = event.getX() - getCenterX();
        float y = event.getY() - getCenterY();

        int radius = getRadius();
//        float circum = getRadius() * (float)Math.PI;
//        float factor = 20.0f;
        int radiusXY = (int) Math.round(Math.sqrt(x*x + y*y));

        switch(action) {
            case MotionEvent.ACTION_DOWN:
                if (radiusXY > radius*1.1) {
                    return false;
                }

                double angle = 180.0 * Math.atan2(y, x) / Math.PI;
                plusStepInitiated = false;
                minusStepInitiated = false;

                if(angle > tapInAngleStart && angle < tapInAngleEnd) {
                    evaluateTapInTimes();
                    tapInAnimation.start();
                }
                else if(angle > plusStepAngleStart && angle < plusStepAngleEnd) {
                    plusStepInitiated = true;
                }
                else if(angle > minusStepAngleStart && angle < minusStepAngleEnd) {
                    minusStepInitiated = true;
                }
                else {
//                    previous_speed = 0;
                    changingSpeed = true;
                }

                backToZeroAnimation.cancel();
                backToZeroAnimationValue = 1.0f;
                stepCounter = 0;
                integratedDistance = 0.0f;
                previous_x = x;
                previous_y = y;

                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = x - previous_x;
                float dy = y - previous_y;
//                    double dSpeed = - (dx * y - dy * x) / Math.sqrt(x * x + y * y) / circum * factor;
                double dSpeed = - (dx * y - dy * x) / Math.sqrt(x * x + y * y);
                integratedDistance += dSpeed;
                previous_x = x;
                previous_y = y;
                float speedSteps = speedSensitivity * Utilities.px2cm(integratedDistance);

                if(changingSpeed) {
//                    Log.v("Metronome", "SpeedPanel:onTouchEvent: integratedDistance="+integratedDistance + "  " + Utilities.px2cm(integratedDistance));
                    if(Math.abs(speedSteps) >= 1 && speedChangedListener != null) {
                        speedChangedListener.onSpeedChanged(speedSteps * speedIncrement);
                        integratedDistance = 0.0f;
                        stepCounter += speedSteps;
                        stepCounter = Math.min(stepCounter, stepCounterMax);
                        stepCounter = Math.max(stepCounter, -stepCounterMax);
                        invalidate();
                    }
//                    if (Math.abs(dSpeed) >= speedIncrement && speedChangedListener != null) {
//                        speedChangedListener.onSpeedChanged((float) dSpeed);
//                        previous_x = x;
//                        previous_y = y;
//                    }
                }
                else if (Math.abs(speedSteps) >= 1){
                    changingSpeed = true;
                    plusStepInitiated = false;
                    minusStepInitiated = false;
                }
                return true;
            case MotionEvent.ACTION_UP:
               if(plusStepInitiated){
                   speedChangedListener.onSpeedChanged(speedIncrement);
               }
               else if(minusStepInitiated){
                   speedChangedListener.onSpeedChanged(-speedIncrement);
               }

                changingSpeed = false;
                plusStepInitiated = false;
                minusStepInitiated = false;
                backToZeroAnimation.start();
//                stepCounter = 0;
//                highlightTapIn = false;
                invalidate();
        }

        return true;
    }

    private void evaluateTapInTimes() {

        long currentTap = SystemClock.uptimeMillis();
        nTapSamples += 1;

        float currentDt = currentTap - lastTap;
        lastTap = currentTap;

        if(nTapSamples == 1) {
            dt = currentDt;
            return;
        }

//        Log.v("Metronome", "SpeedPanel:computeSpeedFromTapIn:  err=" + ((currentDt - dt) / dt));
        if(Math.abs(currentDt - dt) / dt > maxTapErr){
            nTapSamples = 2;
        }

        float fac = facTapInfty + (1.0f - facTapInfty) / (nTapSamples - 1);
//        float fac = 1.0f;
        dt = fac * currentDt + (1.0f - fac) * dt;

//        float facT = facTapInfty + (1.0f - facTapInfty) / (nTapSamples - 1);

        predictNextTap = Math.round(fac * (currentTap - predictNextTap) + predictNextTap + dt);
//        Log.v("Metronome", "Speedpanel:computeSpeedFromTapIn:  fac=" + fac + "  dt=" + dt);

        if(nTapSamples >= 3 && speedChangedListener != null) {
            speedChangedListener.onAbsoluteSpeedChanged(Utilities.dt2speed(Math.round(dt)),
                                                        Math.round(predictNextTap + tapDelay));
        }

    }


    void setOnSpeedChangedListener(SpeedChangedListener listener){
        speedChangedListener = listener;
    }

    void setSpeedIncrement(float speedIncrement){
//         Log.v("Metronome","SpeedPanel:setIncrement");
        this.speedIncrement = speedIncrement;
        invalidate();
    }

    void setSensitivity(float speedSensitivity){

//        Log.v("Metronome","SpeedPanel:setSensitivity");
        this.speedSensitivity = speedSensitivity;
    }
}
