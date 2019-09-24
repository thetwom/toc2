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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.core.content.ContextCompat;

public class VolumeControl extends ViewGroup {

    final private int default_width = Math.round(Utilities.dp_to_px(60));
    final private int default_length = Math.round(Utilities.dp_to_px(300));
    final private Rect rectInt = new Rect();
    final private RectF rect = new RectF();
    final private RectF rectPos = new RectF();

    private Drawable volMute;
    private Drawable volDown;
    private Drawable volUp;

    private int normalColor = Color.WHITE;
    private int sliderColor = Color.BLACK;
    private boolean vertical = false;

    private final float iSpace = Utilities.dp_to_px(2);
    private float pos = 0f;

    private final Paint contourPaint = new Paint();

    interface OnVolumeChangedListener {
        void onVolumeChanged(float volume);
    }

    private OnVolumeChangedListener onVolumeChangedListener = null;

    private final ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            float iWid = getMovableHeight();
            float cornerRad = Utilities.dp_to_px(Math.round(iWid));

            rectInt.set(Math.round(getPaddingLeft()), Math.round(getPaddingTop()),
                    Math.round(getWidth() - getPaddingRight()),
                    Math.round(getHeight() - getPaddingBottom()));

            outline.setRoundRect(rectInt, cornerRad);
        }
    };

//    public VolumeControl(Context context) {
//        init(context, null);
//    }

    public VolumeControl(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setOutlineProvider(outlineProvider);
        volMute = ContextCompat.getDrawable(context, R.drawable.ic_volume_mute);
        volDown = ContextCompat.getDrawable(context, R.drawable.ic_volume_down);
        volUp = ContextCompat.getDrawable(context, R.drawable.ic_volume_up);

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.VolumeControl);
            normalColor = ta.getColor(R.styleable.VolumeControl_normalColor, Color.WHITE);
            sliderColor = ta.getColor(R.styleable.VolumeControl_sliderColor, Color.BLACK);
            vertical = ta.getBoolean(R.styleable.VolumeControl_vertical, false);
            ta.recycle();
        }



    }
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //for(int i = 0; i < getChildCount(); ++i) {
        //    View v = getChildAt(i);
        //    v.measure(widthMeasureSpec, heightMeasureSpec);
        //}
        MarginLayoutParams layoutParams = (MarginLayoutParams) getLayoutParams();

        int desiredWidth = layoutParams.leftMargin + layoutParams.rightMargin;
        int desiredHeight = layoutParams.topMargin + layoutParams.bottomMargin;

        if(vertical) {
            desiredWidth += default_width;
            desiredHeight += default_length;
        }
        else{
            desiredWidth += default_length;
            // noinspection SuspiciousNameCombination
            desiredHeight += default_width;
        }

        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        float iLen = getMovableLength();
        float iWid = getMovableHeight();
        float cornerRad = Utilities.dp_to_px(Math.round(iWid));

        contourPaint.setAntiAlias(true);

        rect.set(getPaddingLeft(), getPaddingTop(),
                getWidth()-getPaddingRight(),
                getHeight()-getPaddingBottom());

        float cx = centerX();
        float cy = centerY();
        if(vertical) {
            rectPos.set(cx - iWid / 2.0f, cy - iLen / 2.0f, cx + iWid / 2.0f, cy + iLen / 2.0f);
        }
        else {
            rectPos.set(cx - iLen / 2.0f, cy - iWid / 2.0f, cx + iLen / 2.0f, cy + iWid / 2.0f);
        }


        contourPaint.setColor(normalColor);
        contourPaint.setStyle(Paint.Style.FILL);

        canvas.drawRoundRect(rect, cornerRad, cornerRad, contourPaint);

        contourPaint.setStyle(Paint.Style.FILL);
        contourPaint.setColor(sliderColor);
        canvas.drawRoundRect(rectPos, cornerRad, cornerRad, contourPaint);

//
//        contourPaint.setStyle(Paint.Style.STROKE);
//
//        canvas.drawRoundRect(rect, cornerRad, cornerRad, contourPaint);
        Drawable icon = volUp;
        if(pos < 0.01) {
            icon = volMute;
        }
        else if(pos < 0.6){
            icon = volDown;
        }

        icon.setBounds(Math.round(cx - iWid/2.0f), Math.round(cy - iWid/2.0f), Math.round(cx + iWid/2.0f), Math.round(cy + iWid/2.0f));
        icon.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (vertical)
                    pos = 1.0f - dxToDxPos(y - getMovableLength() / 2.0f);
                else
                    pos = dxToDxPos(x - getMovableLength() / 2.0f);
                pos = Math.min(1.0f, pos);
                pos = Math.max(0.0f, pos);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (onVolumeChangedListener != null)
                    onVolumeChangedListener.onVolumeChanged(pos);

                return true;
        }
        return false;
    }

    private float getMovableLength(){
        return 3.0f * getMovableHeight();
    }

    private float getMovableHeight(){
        if(vertical)
            return getWidth() - getPaddingLeft() - getPaddingRight() - 2*iSpace;
        else
            return getHeight() - getPaddingBottom() - getPaddingTop() - 2*iSpace;
    }

    private float dxToDxPos(float dx){
        float pos0 = iSpace;
        float pos1 = -getMovableLength() - iSpace;
        if(vertical){
            pos0 += getPaddingTop();
            pos1 += getHeight() - getPaddingBottom();
        }
        else {
            pos0 += getPaddingLeft();
            pos1 += getWidth() - getPaddingRight();
        }
        return dx / (pos1 - pos0);
    }

    private float centerX(){
        if(vertical) {
            return getPaddingLeft() + iSpace + getMovableHeight()/2.0f;
        }
        else {
            float pos0 = getPaddingLeft() + iSpace;
            float pos1 = getWidth() - getPaddingRight() - getMovableLength() - iSpace;
            return pos0 + pos * (pos1 - pos0) + getMovableLength() / 2.0f;
        }
    }

    private float centerY(){
        if(vertical) {
            float pos0 = getPaddingTop() + iSpace;
            float pos1 = getHeight() - getPaddingTop() - getMovableLength() - iSpace;
            return pos0 + (1.0f-pos) * (pos1 - pos0) + getMovableLength() / 2.0f;
        }
        else {
            return getPaddingTop() + iSpace + getMovableHeight() / 2.0f;
        }
    }

    public void setState(float pos) {
        this.pos = pos;
        invalidate();
    }

    public float getVolume(){
        return pos;
    }

    public void setOnVolumeChangedListener(OnVolumeChangedListener onVolumeChangedListener) {
        this.onVolumeChangedListener = onVolumeChangedListener;
    }

    public void setVertical(boolean vertical){
        this.vertical = vertical;
        invalidate();
    }

    public void setBackgroundColor(int color) {
        normalColor = color;
        invalidate();
    }

    public void setSliderColor(int color) {
        sliderColor = color;
        invalidate();
    }
}
