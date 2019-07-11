package toc2.toc2;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.AppCompatImageButton;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;

public class MoveableButton extends AppCompatImageButton {

    private final Paint volumePaint;
    private final int volumeColor;

    private final Paint backgroundPaint;
    private final int normalColor;
    private final int highlightColor;
    private int buttonColor;

    private final int cornerRadius = dp_to_px(4);

    private float posX = 0;
    private float posY = 0;

    private float dXstart;
    private float dYstart;

    private float rippleStatus = 1;

    private boolean isMoving = false;

    private Drawable icon = null;

    private final Bundle properties = new Bundle();

    private final SpringAnimation springAnimationX = new SpringAnimation(this, DynamicAnimation.X).setSpring(
         new SpringForce()
                 .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                 .setStiffness(SpringForce.STIFFNESS_HIGH));
    private final SpringAnimation springAnimationY = new SpringAnimation(this, DynamicAnimation.Y).setSpring(
         new SpringForce()
                 .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                 .setStiffness(SpringForce.STIFFNESS_HIGH));

    private final ValueAnimator colorAnimation;
    private final ValueAnimator rippleAnimation;
//            ContextCompat.getColor(getContext(), normalColor),
//            ContextCompat.getColor(getContext(), highlightColor),
//            ContextCompat.getColor(getContext(), normalColor));

    public interface PositionChangedListener {
        void onPositionChanged(MoveableButton button, float posX, float posY);

        void onStartMoving(MoveableButton button, float posX, float posY);

        void onEndMoving(MoveableButton button, float posX, float posY);
    }


    private PositionChangedListener positionChangedListener = null;

    private OnClickListener onClickListener = null;


    MoveableButton(Context context, int normalColor, int highlightColor){
        super(context);

        ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, getWidth(), getHeight(), cornerRadius);
            }
        };
        setOutlineProvider(outlineProvider);
        this.normalColor = normalColor;
        this.highlightColor = highlightColor;
        this.buttonColor = normalColor;

        colorAnimation = ValueAnimator.ofArgb(normalColor, highlightColor, normalColor);
        rippleAnimation = ValueAnimator.ofFloat(0, 1);
        rippleAnimation.setInterpolator(new LinearInterpolator());

        volumePaint = new Paint();
        volumePaint.setAntiAlias(true);

        volumeColor = Color.RED;

        backgroundPaint = new Paint();
        backgroundPaint.setAntiAlias(true);


        setElevation(dp_to_px(8));
        colorAnimation.setDuration(200); // milliseconds
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
            //    setBackgroundColor((int) animator.getAnimatedValue());
                buttonColor = (int) animator.getAnimatedValue();
                invalidate();
//                setBackgroundTintList(ColorStateList.valueOf((int) animator.getAnimatedValue()));

            }
        });

        rippleAnimation.setDuration(100);
        rippleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                rippleStatus = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
//        mTapDetector = new GestureDetector(context, new GestureTap());

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
//
//                boolean clicked = mTapDetector.onTouchEvent(event);
//                if(clicked)
//                    v.performClick();

                switch(action){
                    case MotionEvent.ACTION_DOWN:
                        posX = v.getX();
                        posY = v.getY();
                        dXstart = v.getX() - event.getRawX();
                        dYstart = v.getY() - event.getRawY();
                        rippleStatus = 0;
                        Log.v("Metronome", "pos: " + (-dXstart) + " " + (-dYstart));
                        invalidate();
                        isMoving = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float newX = event.getRawX() + dXstart;
                        float newY = event.getRawY() + dYstart;
                        if (isMoving) {
                            v.animate()
                                    .x(newX)
                                    .y(newY)
                                    .setDuration(0)
                                    .start();
                            if (positionChangedListener != null)
                                positionChangedListener.onPositionChanged(MoveableButton.this, getX(), getY());
                        } else {
                            float dx = posX - newX;
                            float dy = posY - newY;
                            final float resistanceDist = dp_to_px(10);
                            if (dx * dx + dy * dy > resistanceDist * resistanceDist) {
                                isMoving = true;
//                                setElevation(10);
                                setTranslationZ(dp_to_px(4));
                                if(positionChangedListener != null)
                                    positionChangedListener.onStartMoving(MoveableButton.this, getX(), getY());
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        rippleAnimation.start();
                        if(isMoving) {
                            isMoving = false;
//                            setElevation(2);
                            setTranslationZ(0);
                            setNewPosition(posX, posY);
                            //clearColorFilter();
                            if (positionChangedListener != null)
                                positionChangedListener.onEndMoving(MoveableButton.this, getX(), getY());
                        }
                        else {
                            if(onClickListener != null)
                                onClickListener.onClick(v);
//                            animateColor();
                            v.performClick();
                        }
                        break;
                }

                return true;
            }
        });
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        super.setLayoutParams(params);
        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) params;
        posX = marginLayoutParams.leftMargin;
        posY = marginLayoutParams.topMargin;
    }

    @Override
    public void setOnClickListener(OnClickListener onClickListener){
        this.onClickListener = onClickListener;
    }

    void setNewPosition(float newX, float newY) {
        posX = newX;
        posY = newY;

        if (isMoving) {
            return;
        }

        int dx = Math.round(posX - getX());
        int dy = Math.round(posY - getY());
        if(dx == 0 && dy == 0)
            return;

        springAnimationX.getSpring().setFinalPosition(posX);
        springAnimationY.getSpring().setFinalPosition(posY);

        springAnimationX.start();
        springAnimationY.start();
    }

    public void setOnPositionChangedListener(PositionChangedListener positionChangedListener){
        this.positionChangedListener = positionChangedListener;
    }

    public void animateColor() {
        colorAnimation.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        backgroundPaint.setAlpha(1000);
        backgroundPaint.setColor(buttonColor);
        backgroundPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius, backgroundPaint);

        volumePaint.setColor(volumeColor);
        volumePaint.setStyle(Paint.Style.FILL);
        float volume = properties.getFloat("volume", 1.0f);
        canvas.drawRect(getWidth()-dp_to_px(2),(getHeight()-2*cornerRadius)*(1.0f-volume)+cornerRadius,getWidth(),
                getHeight()-cornerRadius, volumePaint);

        if(icon != null) {
            icon.setBounds(Math.round(getWidth()/2.0f-getHeight()/2.0f), 0,
                    Math.round(getWidth()/2.0f + getHeight()/2.0f), Math.round(getHeight()));
            icon.draw(canvas);
        }

        super.onDraw(canvas);

        if(rippleStatus < 1){
            backgroundPaint.setColor(Color.BLACK);
            backgroundPaint.setAlpha(Math.round((1-rippleStatus) * 100));
            canvas.drawCircle(-dXstart, -dYstart-getHeight(), dp_to_px(30) + rippleStatus*getWidth(), backgroundPaint);
        }
    }

    public void highlight(boolean value){
        if(value)
            buttonColor = highlightColor;
        else
            buttonColor = normalColor;
        invalidate();
    }

    public Bundle getProperties(){
        return properties;
    }

    public void setProperties(Bundle newProperties) {

        properties.putAll(newProperties);
        icon = ContextCompat.getDrawable(getContext(), Sounds.getIconID(properties.getInt("soundid", 0)));

        invalidate();
        Log.v("Metronome", "Setting new button properties " + properties.getFloat("volume",-1));
    }

    private int dp_to_px(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }
}
