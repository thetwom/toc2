package toc2.toc2;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.annotation.Nullable;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class SpeedPanel extends View {

    private Paint circlePaint;
    private int circleColor;

    private float previous_x;
    private float previous_y;
    private int previous_speed;
    final static private int strokeWidth = 10;
    final static private float innerRadiusRatio = 0.6f;
    private Path pathPlayButton = null;
    final static public int STATUS_PLAYING = 1;
    final static public int STATUS_PAUSED = 2;
    private int buttonStatus = STATUS_PAUSED;

    private double playPercentage = 0.0;

    private final GestureDetector mTapDetector;

    private class GestureTap extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if(buttonClickedListener != null) {
                if(buttonStatus == STATUS_PAUSED){
                    Log.v("Metronome", "SpeedPanel:GestureTap:onSingleTapConfirmed() : trigger onPlay");
                    buttonClickedListener.onPlay();
                }
                else{
                    Log.v("Metronome", "SpeedPanel:GestureTap:onSingleTapConfirmed() : trigger onPause");
                    buttonClickedListener.onPause();
                }
                //buttonClickedListener.onButtonClicked();
                return true;
            }

            return false;
        }
    }

    public interface SpeedChangedListener {
        void onSpeedChanged(int speed);
    }

    public interface ButtonClickedListener {
        //void onButtonClicked();
        void onPlay();

        void onPause();
    }

    private SpeedChangedListener speedChangedListener;
    private ButtonClickedListener buttonClickedListener;

    private final ValueAnimator animateToPlay = ValueAnimator.ofFloat(0.0f, 1.0f);
    private final ValueAnimator animateToPause = ValueAnimator.ofFloat(1.0f, 0.0f);

    public SpeedPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
        speedChangedListener = null;
        buttonClickedListener = null;
        mTapDetector = new GestureDetector(context, new GestureTap());

        animateToPause.setDuration(200);
        animateToPlay.setDuration(200);

        animateToPause.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                playPercentage = (float) animation.getAnimatedValue();
                invalidate();
            }
        });

        animateToPlay.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                playPercentage = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
    }

    private void init(@Nullable AttributeSet attrs) {
        circlePaint = new Paint();
        circlePaint.setAntiAlias(true);

        if(attrs == null)
            return;

        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.SpeedPanel);
        circleColor = ta.getColor(R.styleable.SpeedPanel_circle_stroke, Color.GREEN);

        ta.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        //int desiredWidth = Integer.MAX_VALUE;
        //int desiredHeight = Integer.MIN_VALUE;
        int desiredSize = dp_to_px(200) + (Math.max(getPaddingBottom()+getPaddingTop(), getPaddingLeft()+getPaddingRight()));

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int height;
        int width;

        if(widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY){
            width = widthSize;
            height = heightSize;
        }
        else if(widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.AT_MOST){
            width = widthSize;
            height = Math.min(heightSize, widthSize);
        }
        else if(widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.EXACTLY){
            width = Math.min(widthSize, heightSize);
            height = heightSize;
        }
        else if(widthMode == MeasureSpec.EXACTLY){
            width = widthSize;
            height = widthSize;
        }
        else if(heightMode == MeasureSpec.EXACTLY){
            width = heightSize;
            height = heightSize;
        }
        else if(widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST){
            int size = Math.min(desiredSize, Math.min(widthSize, heightSize));
            width = size;
            height = size;
        }
        else if(widthMode == MeasureSpec.AT_MOST){
            int size = Math.min(desiredSize, widthSize);
            width = size;
            height = size;
        }
        else if(heightMode == MeasureSpec.AT_MOST){
            int size = Math.min(desiredSize, heightSize);
            width = size;
            height = size;
        }
        else{
            width = desiredSize;
            height = desiredSize;
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int radius = getRadius();
        int cx = getCenterX();
        int cy = getCenterY();

        int innerRadius = Math.round(radius * innerRadiusRatio);

        circlePaint.setColor(circleColor);

        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(strokeWidth);
        canvas.drawCircle(cx, cy, radius, circlePaint);

        circlePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, innerRadius, circlePaint);


        float triRad = innerRadius * 0.7f;

        float xShift = innerRadius * 0.1f;
        float rectWidth = innerRadius * 0.4f;
        float rectHeight = innerRadius * 1f;

        double phiPauseOuter = Math.atan((0.5f * rectHeight) / (xShift + rectWidth));
        double phiPauseInner = Math.atan((0.5f * rectHeight) / xShift);
        double radPauseOuter = Math.sqrt(Math.pow(xShift + rectWidth, 2) + Math.pow(0.5f * rectHeight, 2));
        double radPauseInner = Math.sqrt(Math.pow(xShift, 2) + Math.pow(0.5f * rectHeight, 2));

        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setColor(Color.WHITE);

        if (pathPlayButton == null)
            pathPlayButton = new Path();
        pathPlayButton.setFillType(Path.FillType.EVEN_ODD);

        double phi = playPercentage * phiPauseOuter + (1 - playPercentage) * 2.0 * Math.PI;
        double r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad;
        pathPlayButton.moveTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * phiPauseInner + (1 - playPercentage) * 2.0 * Math.PI;
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad;
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * (-phiPauseInner) + (1 - playPercentage) * 2.0 * Math.PI / 3.0;
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad;
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * (-phiPauseOuter) + (1 - playPercentage) * 4.0 * Math.PI / 3.0;
        r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad;
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        canvas.drawPath(pathPlayButton, circlePaint);
        pathPlayButton.rewind();


        phi = playPercentage * (Math.PI - phiPauseOuter) + (1 - playPercentage) * 2.0 * Math.PI;
        r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad;
        pathPlayButton.moveTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * (Math.PI - phiPauseInner) + (1 - playPercentage) * 2.0 * Math.PI;
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad;
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * (phiPauseInner - Math.PI) + (1 - playPercentage) * 4.0 * Math.PI / 3.0;
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad;
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * (phiPauseOuter - Math.PI) + (1 - playPercentage) * 2.0 * Math.PI / 3.0;
        r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad;
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        canvas.drawPath(pathPlayButton, circlePaint);
        pathPlayButton.rewind();

        float angleMax = 65.0f / 180.0f * (float) Math.PI;
        float angleMin = -65.0f / 180.0f * (float) Math.PI;
        float dAngle = 3.5f / 180.0f * (float) Math.PI;
        float growthFactor = 1.05f;
        float radSpeedInner = innerRadius + 0.35f * (radius - innerRadius);
        float radSpeedOuter = radius - 0.35f * (radius - innerRadius);
        float strokeWidthSpeed = (radius + innerRadius) * (float) Math.PI / 120.0f;

        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(strokeWidthSpeed);
        circlePaint.setColor(circleColor);

        float angle = angleMax;
        while (angle >= angleMin) {
            canvas.drawLine(
                    cx + radSpeedInner * (float) Math.sin(angle),
                    cy - radSpeedInner * (float) Math.cos(angle),
                    cx + radSpeedOuter * (float) Math.sin(angle),
                    cy - radSpeedOuter * (float) Math.cos(angle),
                    circlePaint
            );
            dAngle *= growthFactor;
            angle -= dAngle;
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

        boolean clicked = mTapDetector.onTouchEvent(event);
        if(clicked)
            performClick();

        switch(action) {
            case MotionEvent.ACTION_DOWN:
                int radiusXY = (int) Math.round(Math.sqrt(x*x + y*y));
                if (radiusXY > radius*1.1){
                 return false;
                }
                previous_x = x;
                previous_y = y;
                previous_speed = 0;

                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = x - previous_x;
                float dy = y - previous_y;
                int speed = -(int)Math.round((dx * y - dy * x) / Math.sqrt(x*x + y*y) / circum * factor);

                if (previous_speed != speed && speedChangedListener != null) {
                    speedChangedListener.onSpeedChanged(speed);
                    previous_x = x;
                    previous_y = y;
                }
                previous_speed = speed;
        }

        return true;
    }

    void setOnSpeedChangedListener(SpeedChangedListener listener){
        speedChangedListener = listener;
    }
    void setOnButtonClickedListener(ButtonClickedListener listener){
        buttonClickedListener = listener;
    }


    private int getRadius(){
        int width = getWidth();
        int height = getHeight();
        int widthNoPadding = width - getPaddingRight() - getPaddingLeft();
        int heightNoPadding = height - getPaddingTop() - getPaddingBottom();
        return (Math.min(widthNoPadding, heightNoPadding) - strokeWidth) / 2;
    }

    private int getCenterX(){
        return getWidth() / 2;
    }
    private int getCenterY(){
        return getHeight() / 2;
    }

    public void changeStatus(int status){
        if(buttonStatus == status)
            return;

        buttonStatus = status;
        if(status == STATUS_PAUSED) {
            //playPercentage = 0.0;
            animateToPause.start();
        }
        else if(status == STATUS_PLAYING) {
            // playPercentage = 1.0;
            animateToPlay.start();
        }
        invalidate();
    }

    public int getStatus(){
        return buttonStatus;
    }

    private float pTX(double phi, double rad) {
        return (float) (rad * Math.cos(phi)) + getCenterX();
    }
    private float pTY(double phi, double rad) {
        return (float) (rad * Math.sin(phi)) + getCenterY();
    }

    private int dp_to_px(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }
}
