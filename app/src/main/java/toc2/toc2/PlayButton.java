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
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;

public class PlayButton extends ControlPanel {

    private Paint circlePaint;

    private Path pathPlayButton = null;
    private Path pathOuterCircle = null;
    final static public int STATUS_PLAYING = 1;
    final static public int STATUS_PAUSED = 2;
    private int buttonStatus = STATUS_PAUSED;

    private boolean clickInitiated = false;

    private double playPercentage = 0.0;

    private int highlightColor;
    private int normalColor;
    private int labelColor;

    private ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            int radius = getInnerRadius();
            int cx = getCenterX();
            int cy = getCenterY();
            outline.setOval(cx-radius, cy-radius, cx+radius, cy+radius);
        }
    };

    public interface ButtonClickedListener {
        //void onButtonClicked();
        void onPlay();

        void onPause();
    }

    private ButtonClickedListener buttonClickedListener;

    private final ValueAnimator animateToPlay = ValueAnimator.ofFloat(0.0f, 1.0f);
    private final ValueAnimator animateToPause = ValueAnimator.ofFloat(1.0f, 0.0f);

    public PlayButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
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

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PlayButton);
        highlightColor = ta.getColor(R.styleable.PlayButton_highlightColor, Color.GRAY);
        normalColor = ta.getColor(R.styleable.PlayButton_normalColor, Color.GRAY);
        labelColor = ta.getColor(R.styleable.PlayButton_labelColor, Color.WHITE);

        ta.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int cx = getCenterX();
        int cy = getCenterY();

        int innerRadius = getInnerRadius();

        circlePaint.setColor(normalColor);

        circlePaint.setStyle(Paint.Style.FILL);

        if(pathOuterCircle == null)
            pathOuterCircle = new Path();
        pathOuterCircle.setFillType(Path.FillType.EVEN_ODD);

        pathOuterCircle.rewind();
        pathOuterCircle.addCircle(cx, cy, innerRadius, Path.Direction.CW);
        canvas.drawPath(pathOuterCircle, circlePaint);

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

        if (pathPlayButton == null)
            pathPlayButton = new Path();
        pathPlayButton.setFillType(Path.FillType.EVEN_ODD);

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
    }

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
                if (radiusXY < innerRadius) {
                    clickInitiated = true;
                }
                else {
                    return false;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if(clickInitiated && radiusXY > innerRadius) {
                    clickInitiated = false;
                    invalidate();
                    return false;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if(clickInitiated && radiusXY < innerRadius){
                        performClick();
                }
                clickInitiated = false;
                invalidate();
        }

        return true;
    }

    void setOnButtonClickedListener(ButtonClickedListener listener){
        buttonClickedListener = listener;
    }

    @Override
    public boolean performClick() {
         if (buttonClickedListener != null) {
             if (buttonStatus == STATUS_PAUSED) {
                 Log.v("Metronome", "PlayButton:GestureTap:onSingleTapConfirmed() : trigger onPlay");
                 buttonClickedListener.onPlay();
             } else {
                 Log.v("Metronome", "PlayButton:GestureTap:onSingleTapConfirmed() : trigger onPause");
                 buttonClickedListener.onPause();
             }
         }
         return super.performClick();
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

}
