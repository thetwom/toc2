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
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.util.Arrays;

public class SpeedPanel extends ControlPanel {

    private Paint circlePaint;

    private float previous_x;
    private float previous_y;
//    private int previous_speed;
    //final private int strokeWidth = dp_to_px(2);
    //final static public float innerRadiusRatio = 0.62f;
    private Path pathOuterCircle = null;
    private Path textPath = null;

    private boolean changingSpeed = false;
//    private boolean highlightTapIn = false;

    private int highlightColor;
    private int normalColor;
    private int labelColor;
    private int textColor;

    private final ValueAnimator tapInAnimation = ValueAnimator.ofFloat(0, 1);
    private float tapInAnimationValue = 1.0f;

    private final ValueAnimator changingSpeedAnimation = ValueAnimator.ofFloat(1, 1.7f);
    private float changingSpeedAnimationValue = 1.0f;

    private int numTapInTimes = 3;
    private long[] tapInTimes;

    public interface SpeedChangedListener {
        void onSpeedChanged(int dSpeed);
        void onAbsoluteSpeedChanged(int newSpeed, long nextKlickTimeInMillis);
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

        changingSpeedAnimation.setDuration(150);
        changingSpeedAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                changingSpeedAnimationValue = (float) animation.getAnimatedValue();
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

        tapInTimes = new long[numTapInTimes];
        Arrays.fill(tapInTimes, -1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

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
            textPath = new Path();
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
        float angleMax  = -90.0f + 40 * changingSpeedAnimationValue;
        float angleMin = -90.0f - 40 * changingSpeedAnimationValue;
        float dAngle = 3.0f * changingSpeedAnimationValue;

        float angle = angleMax -dAngle + 2.0f * changingSpeedAnimationValue;
        while (angle >= angleMin) {
            canvas.drawArc(cx - speedRad, cy - speedRad, cx + speedRad, cy + speedRad, angle, -2f*changingSpeedAnimationValue, false, circlePaint);
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

        textPath.addArc(cx - speedRad, cy - speedRad, cx + speedRad, cy + speedRad, 265, -350);
//        circlePaint.setStrokeWidth(1);
//        circlePaint.setStyle(Paint.Style.STROKE);
//        canvas.drawPath(textPath, circlePaint);
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setTextAlign(Paint.Align.CENTER);
        final float tapInTextSize = Utilities.sp_to_px(22);
        circlePaint.setTextSize(tapInTextSize);

        circlePaint.setColor(textColor);

        canvas.drawTextOnPath(getContext().getString(R.string.tap_in), textPath, 0, tapInTextSize/2.0f, circlePaint);

        if(tapInAnimationValue <= 0.99999) {
            circlePaint.setColor(highlightColor);
            circlePaint.setAlpha(Math.round(255*(1-tapInAnimationValue)));
            float highlightTextSize = tapInTextSize * (1 + 10*tapInAnimationValue);
            circlePaint.setTextSize(highlightTextSize);
            canvas.drawTextOnPath(getContext().getString(R.string.tap_in), textPath, 0, tapInTextSize / 2.0f, circlePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int action = event.getActionMasked();
        float x = event.getX() - getCenterX();
        float y = event.getY() - getCenterY();

        int radius = getRadius();
        float circum = getRadius() * (float)Math.PI;
        float factor = 20.0f;
        int radiusXY = (int) Math.round(Math.sqrt(x*x + y*y));

        switch(action) {
            case MotionEvent.ACTION_DOWN:
                if (radiusXY > radius*1.1) {
                    return false;
                }

                double angle = 180.0 * Math.atan2(y, x) / Math.PI;
                if(angle > 60 && angle < 120) {
//                    highlightTapIn = true;
                    System.arraycopy(tapInTimes, 1, tapInTimes, 0, numTapInTimes-1);
                    tapInTimes[numTapInTimes-1] = SystemClock.uptimeMillis();
                    evaluateTapInTimes();
                    tapInAnimation.start();
                }
                else {
                    previous_x = x;
                    previous_y = y;
//                    previous_speed = 0;
                    changingSpeed = true;
                    changingSpeedAnimation.start();
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if(changingSpeed) {
                    float dx = x - previous_x;
                    float dy = y - previous_y;
                    int dSpeed = -(int) Math.round((dx * y - dy * x) / Math.sqrt(x * x + y * y) / circum * factor);

//                    if (previous_speed != dSpeed && speedChangedListener != null) {
                    if (Math.abs(dSpeed) > 0 && speedChangedListener != null) { // TODO: make dSpeed to float and then compare against a threshhold instead of 0
                        speedChangedListener.onSpeedChanged(dSpeed);
                        previous_x = x;
                        previous_y = y;
                    }
//                    previous_speed = dSpeed;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if(changingSpeed) {
                    changingSpeed = false;
                    changingSpeedAnimation.reverse();
                }
//                highlightTapIn = false;
                invalidate();
        }

        return true;
    }

    private void evaluateTapInTimes(){
        if(tapInTimes[numTapInTimes-1] == -1)
            return;

        final double std_max = 0.2;
        double mean = 0;
        double std = 0;
        for(int i = 1; i < numTapInTimes; ++i)
            mean += tapInTimes[i] - tapInTimes[i-1];
        mean /= numTapInTimes-1;

        for(int i = 1; i < numTapInTimes; ++i) {
            double dev = tapInTimes[i] - tapInTimes[i-1] - mean;
            std += dev * dev;
        }
        std = Math.sqrt(std / (numTapInTimes-1)) / mean;
        Log.v("Metronome", "SpeedPanel:evaluateTapInTimes: speed=" + (int) Math.round(60.0 * 1000.0 / mean) + " ;  std="+std);
        if(std <= std_max){
            int speed = (int) Math.round(60.0 * 1000.0 / mean);

            int shiftMillis = -50; // Shift next klick time a little bit, since it feels more natural

            if(speedChangedListener != null)
                speedChangedListener.onAbsoluteSpeedChanged(speed,
                        SystemClock.uptimeMillis() + tapInTimes[numTapInTimes-1] - tapInTimes[numTapInTimes-2]+shiftMillis);
        }
    }

    void setOnSpeedChangedListener(SpeedChangedListener listener){
        speedChangedListener = listener;
    }

//    private int getRadius(){
//        int width = getWidth();
//        int height = getHeight();
//        int widthNoPadding = width - getPaddingRight() - getPaddingLeft();
//        int heightNoPadding = height - getPaddingTop() - getPaddingBottom();
//        return (Math.min(widthNoPadding, heightNoPadding) - strokeWidth) / 2;
//    }
//
//
//    private int getInnerRadius() {
//        return Math.round(getRadius() * innerRadiusRatio);
//    }
//
//    private int getCenterX(){
//        return getWidth() / 2;
//    }
//    private int getCenterY(){
//        return getHeight() / 2;
//    }
//
//    private float pTX(double phi, double rad) {
//        return (float) (rad * Math.cos(phi)) + getCenterX();
//    }
//    private float pTY(double phi, double rad) {
//        return (float) (rad * Math.sin(phi)) + getCenterY();
//    }
//
//    private int dp_to_px(int dp) {
//        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
//    }
}
