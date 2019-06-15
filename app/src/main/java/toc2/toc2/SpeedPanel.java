package toc2.toc2;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import androidx.annotation.Nullable;

import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

public class SpeedPanel extends View {

    private Paint circlePaint;

    private float previous_x;
    private float previous_y;
    private int previous_speed;
    final private int strokeWidth = dp_to_px(2);
    final static private float innerRadiusRatio = 0.57f;
    private Path pathPlayButton = null;
    private Path pathOuterCircle = null;
    final static public int STATUS_PLAYING = 1;
    final static public int STATUS_PAUSED = 2;
    private int buttonStatus = STATUS_PAUSED;

    private boolean clickInitiated = false;
    private boolean changingSpeed = false;

    private double playPercentage = 0.0;

    private int highlightColor;
    private int normalColor;
    private int labelColor;

    private ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            int radius = getRadius();
            int cx = getCenterX();
            int cy = getCenterY();
            outline.setOval(cx-radius, cy-radius, cx+radius, cy+radius);
        }
    };

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
        init(context, attrs);
        speedChangedListener = null;
        buttonClickedListener = null;

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

        setOutlineProvider(outlineProvider);
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

        //TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.SpeedPanel);
        ta.recycle();
        //TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.SpeedPanel);
        //ta.recycle();
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
            //noinspection SuspiciousNameCombination
            height = widthSize;
        }
        else if(heightMode == MeasureSpec.EXACTLY){
            //noinspection SuspiciousNameCombination
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

        int innerRadius = getInnerRadius();

        //circlePaint.setColor(foregroundColor);
        circlePaint.setColor(normalColor);

        //circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStyle(Paint.Style.FILL);
        //circlePaint.setStrokeWidth(strokeWidth);

        if(pathOuterCircle == null)
            pathOuterCircle = new Path();
        pathOuterCircle.setFillType(Path.FillType.EVEN_ODD);

        pathOuterCircle.rewind();
        pathOuterCircle.addCircle(cx, cy, radius, Path.Direction.CW);
        pathOuterCircle.addCircle(cx, cy, innerRadius+strokeWidth/2.0f, Path.Direction.CCW);

        canvas.drawPath(pathOuterCircle, circlePaint);

        pathOuterCircle.rewind();
        pathOuterCircle.addCircle(cx, cy, innerRadius-strokeWidth/2.0f, Path.Direction.CW);
        canvas.drawPath(pathOuterCircle, circlePaint);
        //canvas.drawCircle(cx, cy, radius, circlePaint);

        float someRad2 = innerRadius-strokeWidth/2.0f;
        //RectF rect = new RectF(cx-someRad2*10000f, cy-someRad2*10000f, cx+someRad2*10000f, cy+someRad2*10000f);
        //pathOuterCircle.rewind();
        //pathOuterCircle.addCircle(cx, cy, someRad2, Path.Direction.CW);
        //pathOuterCircle.addRect(rect, Path.Direction.CCW);
        //pathOuterCircle.addArc(cx-someRad2, cy-someRad2, cx+someRad2, cy+someRad2, 0f, 270f);
        //canvas.drawPath(pathOuterCircle, circlePaint);


        //canvas.drawCircle(cx, cy, innerRadius, circlePaint);

        float triRad = innerRadius * 0.7f;

        float xShift = innerRadius * 0.1f;
        float rectWidth = innerRadius * 0.4f;
        float rectHeight = innerRadius * 1f;

        double phiPauseOuter = Math.atan((0.5f * rectHeight) / (xShift + rectWidth));
        double phiPauseInner = Math.atan((0.5f * rectHeight) / xShift);
        double radPauseOuter = Math.sqrt(Math.pow(xShift + rectWidth, 2) + Math.pow(0.5f * rectHeight, 2));
        double radPauseInner = Math.sqrt(Math.pow(xShift, 2) + Math.pow(0.5f * rectHeight, 2));

        circlePaint.setStyle(Paint.Style.FILL);
        if(clickInitiated) {
            circlePaint.setColor(highlightColor);
        }
        else {
            circlePaint.setColor(labelColor);
        }
        //circlePaint.setColor(getTextColors().getDefaultColor());

        if (pathPlayButton == null)
            pathPlayButton = new Path();
        pathPlayButton.setFillType(Path.FillType.EVEN_ODD);

        //pathPlayButton.addCircle(cx, cy, innerRadius, Path.Direction.CW);

        double phi = playPercentage * phiPauseOuter + (1 - playPercentage) * 2.0 * Math.PI;
        double r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad;
        pathPlayButton.moveTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * phiPauseInner + (1 - playPercentage) * 2.0 * Math.PI;
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad;
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * (-phiPauseInner) + (1 - playPercentage) * 2.0 * Math.PI / 2.0;
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad * Math.cos(2.0*Math.PI / 3.0 + Math.PI);
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * (-phiPauseOuter) + (1 - playPercentage) * 4.0 * Math.PI / 3.0;
        r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad;
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        pathPlayButton.close();

        phi = playPercentage * (Math.PI - phiPauseOuter) + (1 - playPercentage) * 2.0 * Math.PI;
        r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad;
        pathPlayButton.moveTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * (Math.PI - phiPauseInner) + (1 - playPercentage) * 2.0 * Math.PI;
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad;
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * (phiPauseInner - Math.PI) + (1 - playPercentage) * 4.0 * Math.PI / 4.0;
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad * Math.cos(4.0*Math.PI / 3.0 + Math.PI);
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        phi = playPercentage * (phiPauseOuter - Math.PI) + (1 - playPercentage) * 2.0 * Math.PI / 3.0;
        r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad;
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r));

        pathPlayButton.close();

        canvas.drawPath(pathPlayButton, circlePaint);
        pathPlayButton.rewind();

        circlePaint.setStyle(Paint.Style.STROKE);
        //circlePaint.setColor(getIconTint().getDefaultColor());
        if(changingSpeed) {
            circlePaint.setColor(highlightColor);
        }
        else {
            circlePaint.setColor(labelColor);
        }

        float growthFactor = 1.06f;
        float speedRad = 0.5f* (radius + innerRadius);
        circlePaint.setStrokeWidth(0.3f * (radius-innerRadius));
        float angle = -5.0f;
        float angleMin = -175.0f;
        float dAngle = 5.5f;

        while (angle >= angleMin) {
            canvas.drawArc(cx - speedRad, cy - speedRad, cx + speedRad, cy + speedRad, angle, -5f, false, circlePaint);
            dAngle *= growthFactor;
            angle -= dAngle;
        }
    }

    //@Override
    //public ViewOutlineProvider getOutlineProvider() {
    //    return outlineProvider;
    //}

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int action = event.getActionMasked();
        float x = event.getX() - getCenterX();
        float y = event.getY() - getCenterY();

        int radius = getRadius();
        int innerRadius = getInnerRadius();
        float circum = getRadius() * (float)Math.PI;
        float factor = 20.0f;
        int radiusXY = (int) Math.round(Math.sqrt(x*x + y*y));

        switch(action) {
            case MotionEvent.ACTION_DOWN:
                if (radiusXY > radius*1.1) {
                 return false;
                }
                else if (radiusXY < innerRadius) {
                    clickInitiated = true;
                }
                else {
                    previous_x = x;
                    previous_y = y;
                    previous_speed = 0;
                    changingSpeed = true;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if(changingSpeed) {
                    float dx = x - previous_x;
                    float dy = y - previous_y;
                    int speed = -(int) Math.round((dx * y - dy * x) / Math.sqrt(x * x + y * y) / circum * factor);

                    if (previous_speed != speed && speedChangedListener != null) {
                        speedChangedListener.onSpeedChanged(speed);
                        previous_x = x;
                        previous_y = y;
                    }
                    previous_speed = speed;
                }
                else if(clickInitiated && radiusXY > innerRadius) {
                    clickInitiated = false;
                    changingSpeed = false;
                    invalidate();
                    return false;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if(clickInitiated && radiusXY < innerRadius){
                        performClick();
                }
                changingSpeed = false;
                clickInitiated = false;
                invalidate();
        }

        return true;
    }

    void setOnSpeedChangedListener(SpeedChangedListener listener){
        speedChangedListener = listener;
    }
    void setOnButtonClickedListener(ButtonClickedListener listener){
        buttonClickedListener = listener;
    }

    @Override
    public boolean performClick() {
         if (buttonClickedListener != null) {
             if (buttonStatus == STATUS_PAUSED) {
                 Log.v("Metronome", "SpeedPanel:GestureTap:onSingleTapConfirmed() : trigger onPlay");
                 buttonClickedListener.onPlay();
             } else {
                 Log.v("Metronome", "SpeedPanel:GestureTap:onSingleTapConfirmed() : trigger onPause");
                 buttonClickedListener.onPause();
             }
         }
         return super.performClick();
    }

    private int getRadius(){
        int width = getWidth();
        int height = getHeight();
        int widthNoPadding = width - getPaddingRight() - getPaddingLeft();
        int heightNoPadding = height - getPaddingTop() - getPaddingBottom();
        return (Math.min(widthNoPadding, heightNoPadding) - strokeWidth) / 2;
    }


    private int getInnerRadius() {
        return Math.round(getRadius() * innerRadiusRatio);
    }

    private int getCenterX(){
        return getWidth() / 2;
    }
    private int getCenterY(){
        return getHeight() / 2;
    }

    public void changeStatus(int status, boolean animate){
        if(buttonStatus == status)
            return;

        Log.v("Metronome", "changeStatus: changing button status");
        buttonStatus = status;
        if(status == STATUS_PAUSED) {
            //playPercentage = 0.0;
            if(animate) {
                animateToPause.start();
            }
            else {
                playPercentage = 0.0;
            }
        }
        else if(status == STATUS_PLAYING) {
            // playPercentage = 1.0;
            if(animate) {
                animateToPlay.start();
            }
            else {
                playPercentage = 1.0;
            }
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
