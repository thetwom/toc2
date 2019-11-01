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
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;
// import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;


public class SoundChooserCircle extends FrameLayout {

     final private Context context;

     private int currentSoundID = 0;

     private ArrayList<MoveableButton> buttons = new ArrayList<>();

     interface OnSoundIDChangedListener {
         void onSoundIDChanged(int soundid);
     }

     private OnSoundIDChangedListener onSoundIDChangedListener = null;

    private int normalButtonColor = Color.BLACK;
    private int highlightButtonColor = Color.GRAY;
    private int volumeButtonColor = Color.RED;

     public SoundChooserCircle(Context context) {
        super(context);
        this.context = context;
    }

    public SoundChooserCircle(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        readAttributes(attrs);
    }

    public SoundChooserCircle(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        readAttributes(attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        post(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }

        @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        //int desiredWidth = Integer.MAX_VALUE;
        //int desiredHeight = Integer.MIN_VALUE;
        int desiredSize = Math.round(Utilities.dp_to_px(500) + (Math.max(getPaddingBottom()+getPaddingTop(), getPaddingLeft()+getPaddingRight())));

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


    private void readAttributes(AttributeSet attrs){
        if(attrs == null)
            return;

        // Log.v("Metronome", "SoundChooserCircle:readAttributes");
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SoundChooserCircle);
        highlightButtonColor = ta.getColor(R.styleable.SoundChooserCircle_highlightColor, Color.BLUE);
        normalButtonColor = ta.getColor(R.styleable.SoundChooserCircle_normalColor, Color.BLACK);
        volumeButtonColor = ta.getColor(R.styleable.SoundChooserCircle_volumeColor, Color.RED);
        ta.recycle();
    }

    private void init() {
         // Log.v("Metronome", "SoundChooserCircle:init()");
//        final float buttonSize = Utilities.dp_to_px(80);
        final float buttonSize = Math.min(getWidth(),getHeight()) / 5.0f;
        float width = getWidth()-getPaddingStart()-getPaddingEnd();
        float height = getHeight()-getPaddingTop()-getPaddingBottom();
        float cx = width / 2.0f + getLeft() + getPaddingLeft();
        float cy = height / 2.0f + getTop() + getPaddingTop();
        float rad = Math.min(width, height) / 2.0f - buttonSize/2.0f;

        ViewGroup viewGroup = (ViewGroup) this.getParent();
        assert viewGroup != null;

        buttons = new ArrayList<>();

        for(int i = 0; i < Sounds.getNumSoundID(); ++i) {

            final int isound = i;
//        MoveableButton button = new MoveableButton(this.context, normalButtonColor, highlightButtonColor);
            final MoveableButton button = new MoveableButton(this.context, normalButtonColor, highlightButtonColor, volumeButtonColor);

            button.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(onSoundIDChangedListener != null)
                        onSoundIDChangedListener.onSoundIDChanged(isound);
                }
            });

            Bundle properties;
            properties = new Bundle();
            properties.putFloat("volume", 0.0f);
            properties.putInt("soundid", isound);
//            button.setScaleType(ImageView.ScaleType.FIT_CENTER);

            button.setProperties(properties, false);
            ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(Math.round(buttonSize), Math.round(buttonSize));
            button.setLayoutParams(params);
            button.setElevation(24);

            int pad = Math.round(Utilities.dp_to_px(5));
            button.setPadding(pad, pad, pad, pad);

            viewGroup.addView(button);
            //addView(button);

            float bcx = cx - rad * (float) Math.sin(2.0*Math.PI / Sounds.getNumSoundID() * i);
            float bcy = cy - rad * (float) Math.cos(2.0*Math.PI / Sounds.getNumSoundID() * i);
            button.setTranslationX(bcx - buttonSize / 2.0f);
            button.setTranslationY(bcy - buttonSize / 2.0f);

            button.setLockPosition(true);

            buttons.add(button);
        }
        setActiveSoundID(currentSoundID);
    }

    public void setActiveSoundID(int soundID){
         currentSoundID = soundID;
         if(buttons.isEmpty())
             return;
         for(MoveableButton b : buttons){
             int bSoundID = b.getProperties().getInt("soundid");
             if(soundID == bSoundID)
                 b.highlight(true);
             else
                 b.highlight(false);
         }
    }

    void setOnSoundIDChangedListener(OnSoundIDChangedListener onSoundIDChangedListener){
         this.onSoundIDChangedListener = onSoundIDChangedListener;
    }

    int getCurrentSoundID(){
         return currentSoundID;
    }
}
