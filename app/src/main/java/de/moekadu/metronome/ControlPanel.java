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
import android.util.AttributeSet;
import android.view.View;

public class ControlPanel extends View {

    private final static float innerRadiusRatio = 0.62f;

    public ControlPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        //int desiredWidth = Integer.MAX_VALUE;
        //int desiredHeight = Integer.MIN_VALUE;
        int desiredSize = Utilities.dp_to_px(200) + (Math.max(getPaddingBottom()+getPaddingTop(), getPaddingLeft()+getPaddingRight()));

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

    int getRadius(){
        int width = getWidth();
        int height = getHeight();
        int widthNoPadding = width - getPaddingRight() - getPaddingLeft();
        int heightNoPadding = height - getPaddingTop() - getPaddingBottom();
        return Math.min(widthNoPadding, heightNoPadding) / 2;
    }

    int getInnerRadius() {
        return Math.round(getRadius() * innerRadiusRatio);
    }

    int getCenterX(){
        return getWidth() / 2;
    }
    int getCenterY(){
        return getHeight() / 2;
    }

    float pTX(double phi, double rad) {
        return (float) (rad * Math.cos(phi)) + getCenterX();
    }
    float pTY(double phi, double rad) {
        return (float) (rad * Math.sin(phi)) + getCenterY();
    }
}
