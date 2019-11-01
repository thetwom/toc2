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

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;

//public class MoveableButton extends AppCompatImageButton {
public class MoveableButton extends View {

    private final Paint volumePaint;
    private final int volumeColor;

    private boolean noBackgroundFlag = false;
    private final Paint backgroundPaint;
    private final int normalColor;
    private final int highlightColor;
    private int buttonColor;

    private final int cornerRadius = Math.round(Utilities.dp_to_px(4));

    private float viewPosX = 0;
    private float viewPosY = 0;
    private float viewPosZ = 0;

    private float viewPosStartX = 0;
    private float viewPosStartY = 0;

    private float touchPosStartX;
    private float touchPosStartY;

    private float localTouchPosX;
    private float localTouchPosY;

    private float rippleStatus = 1;

    private boolean isMoving = false;

    private boolean lockPosition = false;

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
    private final SpringAnimation springAnimationZ = new SpringAnimation(this, DynamicAnimation.Z).setSpring(
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

    public interface PositionAnimationListener {
        void onAnimationStartListener(MoveableButton button);

        void onAnimationEndListener(MoveableButton button);
    }

    public interface OnPropertiesChangedListener {
        void onPropertiesChanged(MoveableButton button);
    }

    private PositionChangedListener positionChangedListener = null;

    private PositionAnimationListener positionAnimationListener = null;

    private OnClickListener onClickListener = null;

    private OnPropertiesChangedListener onPropertiesChangedListener = null;

    private ViewOutlineProvider outlineProvider;

    MoveableButton(Context context, int normalColor, int highlightColor, int volumeColor){
        super(context);

        outlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, getWidth(), getHeight(), cornerRadius);
            }
        };
        setOutlineProvider(outlineProvider);
        this.normalColor = normalColor;
        this.highlightColor = highlightColor;
        this.buttonColor = normalColor;
        this.volumeColor = volumeColor;

        colorAnimation = ValueAnimator.ofArgb(highlightColor, normalColor);
        rippleAnimation = ValueAnimator.ofFloat(0, 1);
        rippleAnimation.setInterpolator(new LinearInterpolator());

        volumePaint = new Paint();
        volumePaint.setAntiAlias(true);

        backgroundPaint = new Paint();
        backgroundPaint.setAntiAlias(true);

        setElevation(Utilities.dp_to_px(8));

        springAnimationX.addEndListener(new DynamicAnimation.OnAnimationEndListener() {
            @Override
            public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity) {
                if(positionAnimationListener != null && !springAnimationY.isRunning())
                    positionAnimationListener.onAnimationEndListener(MoveableButton.this);
            }
        });
        springAnimationY.addEndListener(new DynamicAnimation.OnAnimationEndListener() {
            @Override
            public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity) {
                if(positionAnimationListener != null & !springAnimationX.isRunning())
                    positionAnimationListener.onAnimationEndListener(MoveableButton.this);
            }
        });

        colorAnimation.setDuration(400); // milliseconds
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

                if(onClickListener == null && lockPosition) {
                    return false;
                }
//
//                boolean clicked = mTapDetector.onTouchEvent(event);
//                if(clicked)
//                    v.performClick();
                localTouchPosX = event.getX();
                localTouchPosY = event.getY();

                switch(action){
                    case MotionEvent.ACTION_DOWN:
                        viewPosX = v.getX();
                        viewPosY = v.getY();
                        viewPosStartX = viewPosX;
                        viewPosStartY = viewPosY;
//                        touchPosStartX = v.getX() - event.getRawX();
//                        touchPosStartY = v.getY() - event.getRawY();
                        touchPosStartX = event.getRawX();
                        touchPosStartY = event.getRawY();
                        rippleStatus = 0;
                        invalidate();
                        isMoving = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchPosStartX;
                        float dy = event.getRawY() - touchPosStartY;
                        if (isMoving) {
                            float newX = viewPosStartX + dx;
                            float newY = viewPosStartY + dy;
                            v.animate()
                                    .x(newX)
                                    .y(newY)
                                    .setDuration(0)
                                    .start();
                            if (positionChangedListener != null)
                                positionChangedListener.onPositionChanged(MoveableButton.this, getX(), getY());
                        } else {
                            final float resistanceDist = Utilities.dp_to_px(1);
                            if(lockPosition){
                                invalidate();
                            }
                            else if (dx * dx + dy * dy > resistanceDist * resistanceDist) {
                                isMoving = true;
//                                setElevation(10);
                                setTranslationZ(Utilities.dp_to_px(18));
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
                            setNewPosition(viewPosX, viewPosY);
                            //clearColorFilter();
                            if (positionChangedListener != null)
                                positionChangedListener.onEndMoving(MoveableButton.this, getX(), getY());
                        }
                        else {
//                            float relX = -v.getX() + event.getRawX();
//                            float relY = -v.getY() + event.getRawY() - getHeight();
                              float x = event.getX();
                              float y = event.getY();
//                            if(relX > 0 && relX < getWidth() && relY > 0 && relY < getHeight()) {
                            if(x > 0 && x < getWidth() && y > 0 && y < getHeight()) {
                                if (onClickListener != null)
                                    onClickListener.onClick(v);
//                            animateColor();
                                v.performClick();
                            }
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
        viewPosX = marginLayoutParams.leftMargin;
        viewPosY = marginLayoutParams.topMargin;
    }

    @Override
    public void setOnClickListener(OnClickListener onClickListener){
        this.onClickListener = onClickListener;
    }

    void setNewPosition(float newX, float newY) {
//        Log.v("Metronome", "MoveableButton:setNewPosition " + properties.getFloat("volume"));

        springAnimationX.cancel();
        springAnimationY.cancel();

        viewPosX = newX;
        viewPosY = newY;

        if (isMoving) {
            return;
        }

        int dx = Math.round(viewPosX - getX());
        int dy = Math.round(viewPosY - getY());
        if(dx == 0 && dy == 0)
            return;



        springAnimationX.getSpring().setFinalPosition(viewPosX);
        springAnimationY.getSpring().setFinalPosition(viewPosY);

        springAnimationX.start();
        springAnimationY.start();

        if(positionAnimationListener != null)
            positionAnimationListener.onAnimationStartListener(this);
    }

    void setNewPosition(float newX, float newY, float newZ) {
        springAnimationZ.cancel();

        setNewPosition(newX, newY);

        viewPosZ = newZ;
        int dz = Math.round(viewPosZ - getZ());
        if(dz == 0)
            return;


        springAnimationZ.getSpring().setFinalPosition(viewPosZ);
        springAnimationZ.start();
    }

    public void setOnPositionChangedListener(PositionChangedListener positionChangedListener){
        this.positionChangedListener = positionChangedListener;
    }

    public void setOnPositionAnimationListener(PositionAnimationListener positionAnimationListener){
        this.positionAnimationListener = positionAnimationListener;
    }

    public void setOnPropertiesChangedListener(OnPropertiesChangedListener onPropertiesChangedListener){
        this.onPropertiesChangedListener = onPropertiesChangedListener;
    }

    public void animateColor(long duration) {
        colorAnimation.setDuration(duration);
        colorAnimation.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        backgroundPaint.setAlpha(1000);
        backgroundPaint.setColor(buttonColor);
        backgroundPaint.setStyle(Paint.Style.FILL);
        if(!noBackgroundFlag)
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius, backgroundPaint);

        volumePaint.setColor(volumeColor);
        volumePaint.setStyle(Paint.Style.FILL);
        float volume = properties.getFloat("volume", 1.0f);
        canvas.drawRect(getWidth()-Utilities.dp_to_px(2),(getHeight()-2*cornerRadius)*(1.0f-volume)+cornerRadius,getWidth(),
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
            canvas.drawCircle(localTouchPosX, localTouchPosY, Utilities.dp_to_px(30) + rippleStatus*getWidth(), backgroundPaint);
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

    public void setProperties(Bundle newProperties, boolean suppressOnPropertiesChangedListener) {

        properties.putAll(newProperties);
        icon = ContextCompat.getDrawable(getContext(), Sounds.getIconID(properties.getInt("soundid", Sounds.defaultSound())));
        invalidate();
        if(onPropertiesChangedListener != null && !suppressOnPropertiesChangedListener) {
//            Log.v("Metronome", "MoveableButton:setProperties: calling onPropertiesChangedListener");
            onPropertiesChangedListener.onPropertiesChanged(this);
        }
//         Log.v("Metronome", "Setting new button properties " + properties.getFloat("volume",-1));
    }

    @SuppressWarnings("SameParameterValue")
    void setLockPosition(boolean lock){
        lockPosition = lock;
    }

    public void setSpringStiffness(float stiffness) {
        springAnimationX.getSpring().setStiffness(stiffness);
        springAnimationY.getSpring().setStiffness(stiffness);
        springAnimationZ.getSpring().setStiffness(stiffness);
    }

    float getCenterX() {
        return getX() + getWidth()/2.0f;
    }

    float getCenterY() {
        return getY() + getHeight()/2.0f;
    }

    void setNoBackground(boolean flag) {
        noBackgroundFlag = flag;
        if(noBackgroundFlag)
             setOutlineProvider(null);
        else
            setOutlineProvider(outlineProvider);
        invalidate();
    }

//    boolean contains(float posX, float posY) {
//         return contains(posX, posY, 0, 0);
//    }

    boolean contains(float posX, float posY, float xTol, float yTol) {

        return (posX > getX()-xTol && posX < getX() + getWidth()+xTol
                && posY > getY()-yTol && posY < getY() + getHeight() +yTol);
    }

}
