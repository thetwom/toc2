package toc2.toc2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.annotation.Nullable;
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
            }

            return true;
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

    public SpeedPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
        speedChangedListener = null;
        buttonClickedListener = null;
        mTapDetector = new GestureDetector(context, new GestureTap());
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

        int desiredWidth = Integer.MAX_VALUE;
        int desiredHeight = Integer.MIN_VALUE;


    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);

    int width;
    int height;
    int widthNoPadding = widthSize - getPaddingLeft() - getPaddingRight();
    int heightNoPadding = heightSize - getPaddingTop() - getPaddingBottom();
    int smallerDim = Math.min(widthNoPadding, heightNoPadding);

    //Measure Width
    if (widthMode == MeasureSpec.EXACTLY) {
        width = widthSize;
        //width = smallerDim;
    } else if (widthMode == MeasureSpec.AT_MOST) {
        width = smallerDim + getPaddingLeft() + getPaddingRight();
    } else {
        //Be whatever you want
        width = desiredWidth;
    }

    //Measure Height
    if (heightMode == MeasureSpec.EXACTLY) {
        height = heightSize;
        //height = smallerDim;
    } else if (heightMode == MeasureSpec.AT_MOST) {
        height = smallerDim + getPaddingTop() + getPaddingBottom();
    } else {
        height = desiredHeight;
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
        canvas.drawCircle(cx, cy,radius, circlePaint);

        circlePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy,innerRadius, circlePaint);

        if (buttonStatus == STATUS_PAUSED) {
            float triRad = innerRadius * 0.7f;

            if(pathPlayButton == null)
                pathPlayButton = new Path();
            pathPlayButton.setFillType(Path.FillType.EVEN_ODD);

            pathPlayButton.moveTo(
                    Math.round(triRad) + cx,
                    cy
            );
            pathPlayButton.lineTo(
                    (int) Math.round(cx - Math.cos(Math.PI / 3.0) * triRad),
                    (int) Math.round(cy + Math.sin(Math.PI / 3.0) * triRad)
            );
            pathPlayButton.lineTo(
                    (int) Math.round(cx - Math.cos(Math.PI / 3.0) * triRad),
                    (int) Math.round(cy - Math.sin(Math.PI / 3.0) * triRad)
            );
            pathPlayButton.close();
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(Color.WHITE);
            canvas.drawPath(pathPlayButton, circlePaint);
            pathPlayButton.rewind();
        }
        else if (buttonStatus == STATUS_PLAYING) {
            float xShift = innerRadius * 0.1f;
            float rectWidth  = innerRadius * 0.4f;
            float rectHeight = innerRadius * 1f;
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(Color.WHITE);
            canvas.drawRect(
                    cx - xShift -rectWidth,
                    cy + rectHeight/2.0f,
                    cx - xShift,
                    cy - rectHeight/2.0f,
                    circlePaint);

            canvas.drawRect(
                    cx + xShift +rectWidth,
                    cy + rectHeight/2.0f,
                    cx + xShift,
                    cy - rectHeight/2.0f,
                    circlePaint);
        }

        float angleMax = 65.0f / 180.0f * (float) Math.PI ;
        float angleMin = -65.0f / 180.0f * (float) Math.PI;
        float dAngle = 3.5f / 180.0f * (float) Math.PI;
        float growthFactor = 1.05f;
        float radSpeedInner = innerRadius + 0.35f*(radius-innerRadius);
        float radSpeedOuter = radius - 0.35f*(radius-innerRadius);
        float strokeWidthSpeed = (radius+innerRadius) * (float) Math.PI / 120.0f;

        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(strokeWidthSpeed);
        circlePaint.setColor(circleColor);

        float angle = angleMax;
        while(angle >= angleMin) {
            canvas.drawLine(
                    cx + radSpeedInner * (float)Math.sin(angle),
                    cy - radSpeedInner * (float)Math.cos(angle),
                    cx + radSpeedOuter * (float)Math.sin(angle),
                    cy - radSpeedOuter * (float)Math.cos(angle),
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

        mTapDetector.onTouchEvent(event);

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
        buttonStatus = status;
        invalidate();
    }

    public int getStatus(){
        return buttonStatus;
    }
}
