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

//package de.moekadu.metronome;
//
//import android.content.Context;
//import android.content.res.TypedArray;
//import android.graphics.Color;
//import android.os.Bundle;
//import android.util.AttributeSet;
//import android.view.ViewTreeObserver;
//import android.widget.LinearLayout;
//
//import androidx.annotation.Nullable;
//import androidx.core.content.ContextCompat;
//
//import java.util.ArrayList;
//
//public class IconListVisualizer extends LinearLayout {
//
//    //    private ArrayList<Integer> icons;
//    private NoteListItem[] icons = null;
//
//    private final ArrayList<MoveableButton> iconButtons = new ArrayList<>();
//    private int normalColor;
//    private int volumeColor;
//
//    public IconListVisualizer(Context context, @Nullable AttributeSet attrs) {
//        super(context, attrs);
//        init(context, attrs);
//    }
//
//    public IconListVisualizer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
//        super(context, attrs, defStyleAttr);
//        init(context, attrs);
//    }
//
//    private void init(Context context, AttributeSet attrs){
////        setOrientation(LinearLayout.HORIZONTAL);
//
//        //LinearLayout view = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.icon_list_visualizer, this);
////        LinearLayout view = (LinearLayout) inflate(context, R.layout.icon_list_visualizer, this);
//
//        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.IconListVisualizer);
//        normalColor = ta.getColor(R.styleable.IconListVisualizer_normalColor, Color.WHITE);
//        volumeColor = ta.getColor(R.styleable.IconListVisualizer_volumeColor, Color.RED);
//        ta.recycle();
//
//        setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
//
//        setDividerDrawable(ContextCompat.getDrawable(context, R.drawable.empty_divider));
//
////        setBackgroundColor(R.color.colorAccent);
//        //icons = new ArrayList<>();
//
////        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
////            @Override
////            public boolean onPreDraw() {
////                getViewTreeObserver().removeOnPreDrawListener(this);
////                drawIcons();
////                return true;
////            }
////        });
//    }
//
////    public void setIcons(ArrayList<Integer> icons) {
////        this.icons = new ArrayList<>(icons);
////
////        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
////            @Override
////            public boolean onPreDraw() {
////                getViewTreeObserver().removeOnPreDrawListener(this);
////                drawIcons();
////                return true;
////            }
////        });
////    }
//
//    public void setIcons(NoteListItem[] icons) {
//        this.icons = new NoteListItem[icons.length];
//        for(int i = 0; i < icons.length; ++i)
//            this.icons[i] = icons[i].clone();
//
//        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
//            @Override
//            public boolean onPreDraw() {
//                getViewTreeObserver().removeOnPreDrawListener(this);
//                drawIcons();
//                return true;
//            }
//        });
//    }
//
//    private void drawIcons() {
//
//        for (MoveableButton button : iconButtons) {
//            removeView(button);
//        }
//        iconButtons.clear();
//
//        if(icons == null)
//            return;
//
//        int maxButtonWidth = Math.round((getWidth() - getPaddingLeft() - getPaddingRight() - (icons.length-1) * Utilities.Companion.dp2px(2)) / (float) icons.length);
//        int defaultButtonWidth = getHeight() - getPaddingTop() - getPaddingBottom();
//        int buttonWidth = Math.min(maxButtonWidth, defaultButtonWidth);
//
//        for(NoteListItem properties : icons){
//            MoveableButton button = new MoveableButton(getContext(), normalColor, normalColor, volumeColor);
////            Bundle properties;
////            properties = new Bundle();
////            properties.putFloat("volume", 1.0f);
////            properties.putInt("soundid", icon);
//            button.setProperties(properties, false);
//            button.setLockPosition(true);
//            MarginLayoutParams params = new MarginLayoutParams(buttonWidth, getHeight()-getPaddingTop()-getPaddingBottom());
//
//            button.setLayoutParams(params);
//
//            iconButtons.add(button);
//            addView(button);
//        }
//    }
//}
