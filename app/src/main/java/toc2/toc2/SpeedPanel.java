package toc2.toc2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

public class SpeedPanel extends View {

    private Paint circlePaint;
    // private int circleColor;

    private float previous_x;
    private float previous_y;
    private int previous_speed;
    private int strokeWidth = 10;
    private float innerRadiusRatio = 0.6f;

    private GestureDetector mTapDetector;

    Toast infoToast;

    private class GestureTap extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if(buttonClickedListener != null)
                buttonClickedListener.onButtonClicked();
            Log.v("Metronome", "SpeedPanel:GestureTap:onSingleTapConfirmed");
            return true;
        }
    }

    public interface SpeedChangedListener {
        public void onSpeedChanged(int speed);
    }

    public interface ButtonClickedListener {
        public void onButtonClicked();
    }

    private SpeedChangedListener speedChangedListener;
    private ButtonClickedListener buttonClickedListener;

    public SpeedPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
        speedChangedListener = null;
        buttonClickedListener = null;
        infoToast = Toast.makeText(context, "speed", Toast.LENGTH_LONG);
        mTapDetector = new GestureDetector(context, new GestureTap());
    }

    private void init(@Nullable AttributeSet attrs) {
        circlePaint = new Paint();
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setColor(Color.BLACK);
        circlePaint.setAntiAlias(true);
        circlePaint.setStrokeWidth(strokeWidth);

        if(attrs == null)
            return;

        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.SpeedPanel);
        int circleColor = ta.getColor(R.styleable.SpeedPanel_circle_stroke, Color.GREEN);
        circlePaint.setColor(circleColor);

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

        circlePaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(cx, cy,radius, circlePaint);

        circlePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy,innerRadius, circlePaint);

        float triRad = innerRadius * 0.8f;
        Point p1 = new Point(Math.round(triRad)+cx, cy);
        Point p2 = new Point((int) Math.round(cx-Math.cos(Math.PI/3.0)*triRad), (int) Math.round(cy+Math.sin(Math.PI/3.0)*triRad));
        Point p3 = new Point((int) Math.round(cx-Math.cos(Math.PI/3.0)*triRad), (int) Math.round(cy-Math.sin(Math.PI/3.0)*triRad));
        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(p1.x,p1.y);
        path.lineTo(p2.x,p2.y);
        path.lineTo(p3.x,p3.y);
        path.close();
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setColor(Color.WHITE);
        canvas.drawPath(path, circlePaint);
        path.reset();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int action = event.getActionMasked();
        float x = event.getX() - getCenterX();
        float y = event.getY() - getCenterY();

        int radius = getRadius();
        float circum = getRadius() * (float)Math.PI;
        float factor = 50.0f;

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
}
