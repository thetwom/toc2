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

import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
// import android.util.Log;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

public class SoundChooser extends FrameLayout {

    final private Context context;

    private final float spacing = Utilities.dp_to_px(2);
    private final int defaultButtonHeight = Math.round(Utilities.dp_to_px(70));

    final private ArrayList<MoveableButton> buttons = new ArrayList<>();
    private PlusButton plusButton;

    private int normalButtonColor = Color.BLACK;
    private int highlightButtonColor = Color.GRAY;
    private int volumeButtonColor = Color.RED;

    private ButtonClickedListener buttonClickedListener = null;

    public interface ButtonClickedListener {
        void onButtonClicked(MoveableButton button);
    }

    private SoundChangedListener soundChangedListener = null;

    public interface SoundChangedListener {
        void onSoundChanged(ArrayList<Bundle> sounds);
    }

    public SoundChooser(Context context) {
        super(context);
        this.context = context;
    }

    public SoundChooser(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        readAttributes(attrs);
    }

    public SoundChooser(Context context, AttributeSet attrs, int defStyleAttr) {
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

    private void init() {
//        Log.v("Metronome", "SoundChooser:init" + getLeft());

        plusButton = createPlusButton();
    }

    private void readAttributes(AttributeSet attrs){
        if(attrs == null)
            return;

        // Log.v("Metronome", "SoundChooser:readAttributes");
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SoundChooser);
        highlightButtonColor = ta.getColor(R.styleable.SoundChooser_highlightColor, Color.BLUE);
        normalButtonColor = ta.getColor(R.styleable.SoundChooser_normalColor, Color.BLACK);
        volumeButtonColor = ta.getColor(R.styleable.SoundChooser_volumeColor, Color.RED);

        ta.recycle();
    }

    private MoveableButton createButton(int pos) {
        MoveableButton button = new MoveableButton(this.context, normalButtonColor, highlightButtonColor, volumeButtonColor);
//        button.setId(View.generateViewId());
        Bundle properties;
        if(buttons.isEmpty()) {
            properties = new Bundle();
            properties.putFloat("volume", 1.0f);
            properties.putInt("soundid", 0);
        }
        else {
            properties = buttons.get(buttons.size()-1).getProperties();
        }
//        button.setImageResource(R.drawable.ic_hihat);
        button.setProperties(properties, true);

        //FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(getButtonWidth(), getButtonHeight());
        MarginLayoutParams params = new MarginLayoutParams(getButtonWidth(), getButtonHeight());
        // params.setMargins(1000, 1000, 1000, 1000);
        //params.topMargin = getPaddingTop() + Math.round(getY());
        //params.leftMargin = Math.round(indexToPosX(pos)) + Math.round(getX());

        // button.setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimary));

        button.setLayoutParams(params);
        int pad = Math.round(Utilities.dp_to_px(5));
        button.setPadding(pad, pad, pad, pad);

        ViewGroup viewGroup = (ViewGroup) this.getParent();
        if (viewGroup == null)
            return null;

        viewGroup.addView(button);

        button.setTranslationX(Math.round(indexToPosX(pos)) + Math.round(getX()));
        button.setTranslationY(getPaddingTop() + Math.round(getY()));

        MoveableButton.PositionChangedListener positionChangedListener = new MoveableButton.PositionChangedListener() {
            @Override
            public void onPositionChanged(MoveableButton button, float posX, float posY) {
                if (buttonOverPlusButton(posX, posY)) {
//                if(plusButton.contains(posX, posY)) {
                    plusButton.setBackgroundColor(highlightButtonColor);
                }
                else {
                    plusButton.setBackground(null);
                }

                reorderButtons(button, posX);  // this means onSoundChangeListener must be called, which is done in "repositionButtons"
                repositionButtons();
            }

            @Override
            public void onStartMoving(MoveableButton button, float posX, float posY) {
                buttonStartsMoving(button, posX, posY);
            }

            @Override
            public void onEndMoving(MoveableButton button, float posX, float posY) {
                buttonEndsMoving(button, posX, posY);
            }
        };

        button.setOnPositionChangedListener(positionChangedListener);

        button.setOnPropertiesChangedListener(new MoveableButton.OnPropertiesChangedListener() {
            @Override
            public void onPropertiesChanged(MoveableButton button) {
//                Log.v("Metronome","SoundChooser:onPropertiesChanged");
                if(soundChangedListener != null) {
//                    Log.v("Metronome","SoundChooser:onPropertiesChanged: Calling soundChangedListener");
                    soundChangedListener.onSoundChanged(getSounds());
                }
            }
        });

        setCurrentButtonClickedListenerSingleButton(button);

        return button;
    }

    private void repositionButtons() {
        //Log.v("Metronome", "SoundChooser:repositionButtons " + getButtonWidth());
        for (int i = 0; i < buttons.size(); ++i) {
            MoveableButton b = buttons.get(i);
            ViewGroup.LayoutParams params = b.getLayoutParams();
            params.width = getButtonWidth();
            b.setLayoutParams(params);
        }

        for (int i = 0; i < buttons.size(); ++i) {
            buttons.get(i).setNewPosition(indexToPosX(i) + getX(), getPaddingTop() + getY());
        }

        plusButton.reposition(indexToPosX(buttons.size()), getPaddingTop());
//        repositionPlusButton(indexToPosX(buttons.size()), getPaddingTop());

        if(soundChangedListener != null)
            soundChangedListener.onSoundChanged(getSounds());
    }

    private PlusButton createPlusButton() {

        PlusButton button = new PlusButton(this.context);

//        button.setScaleType(ImageView.ScaleType.CENTER_CROP);
//        button.setImageResource(R.drawable.ic_add);
//        button.setBackground(null);

        int buttonHeight = getButtonHeight();
        int buttonWidth = determinePlusButtonWidth();
        LayoutParams params = new LayoutParams(buttonWidth, buttonHeight);
        params.topMargin = getPaddingTop();
        params.leftMargin = getPaddingLeft();

        addView(button, params);

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = buttons.size();
                MoveableButton newButton = createButton(pos);
                buttons.add(pos, newButton);  // this means onSoundChangeListener must be called, which is done in "repositionButtons"
                repositionButtons();
            }
        });

        return button;
    }

    private void buttonStartsMoving(MoveableButton button, float posX, float posY) {
        plusButton.setImageResource(R.drawable.ic_delete);
    }

    private void buttonEndsMoving(MoveableButton button, float posX, float posY) {
        plusButton.resetAppearance();
//        plusButton.setBackground(null);

        if (buttonOverPlusButton(posX, posY)) {
            // TODO: Use contains function of plusButtion here, but therefore we might have to work on rawX, rawY
//        if (plusButton.contains(posX, posY)) {
            buttons.remove(button);
            ViewGroup viewGroup = (ViewGroup) getParent();
            if (viewGroup != null)
                viewGroup.removeView(button);  // this means onSoundChangeListener must be called, which is done in "repositionButtons"
            repositionButtons();
        }
    }

    private boolean buttonOverPlusButton(float posX, float posY) {
        float absPosX = posX - getX();
        float absPosY = posY - getY();

        ViewGroup.LayoutParams params = plusButton.getLayoutParams();
        float plusButtonWidth = params.width;
        float plusButtonHeight = params.height;
        return (absPosX < plusButton.getX() + plusButtonWidth - 0.5 * plusButtonWidth
                && absPosX > plusButton.getX() - 0.5 * plusButtonWidth
                && absPosY < plusButton.getY() + 1 * plusButtonHeight
                && absPosY > plusButton.getY() - 1 * plusButtonHeight);
    }

    public float indexToPosX(int buttonIndex) {
        return getPaddingLeft() + buttonIndex * (getButtonWidth() + spacing);
    }

    private int getClosestButtonLocationIndex(float posX) {

        int pos = Math.round((posX + getPaddingLeft() - spacing / 2.0f) / (getButtonWidth() + spacing));

        pos = Math.min(pos, buttons.size() - 1);
        pos = Math.max(pos, 0);
        return pos;
    }

    private void reorderButtons(MoveableButton button, float posX) {

        int oldIdx = buttons.indexOf(button);
        int idxTheory = getClosestButtonLocationIndex(posX);
        if (oldIdx == idxTheory)
            return;

        float idxPosX = indexToPosX(idxTheory);
        float buttonWidth = getButtonWidth();
        if (posX < idxPosX - 0.3 * buttonWidth || posX > idxPosX + 0.3 * buttonWidth)
            return;

        // the next two command require to call the onSoundChangedListener, however, we assume that
        // "repositionButtons" is called later on which calles the onSoundChangedListener for us.
        buttons.remove(button);
        buttons.add(idxTheory, button);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //for(int i = 0; i < getChildCount(); ++i) {
        //    View v = getChildAt(i);
        //    v.measure(widthMeasureSpec, heightMeasureSpec);
        //}
        MarginLayoutParams layoutParams = (MarginLayoutParams) getLayoutParams();

        int desiredWidth = Integer.MAX_VALUE;
        int desiredHeight = layoutParams.topMargin + layoutParams.bottomMargin + defaultButtonHeight;

        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }


    public int getButtonWidth() {
        int plusButtonWidth = determinePlusButtonWidth();
//        // Log.v("Metronome", "SoundChooser:plusbuttonwidth " + plusButtonWidth);
        int newButtonWidth = Math.round(
                (getWidth() - getPaddingLeft() - getPaddingRight() - plusButtonWidth)
                        / (float) buttons.size() - spacing);
        newButtonWidth = Math.min(newButtonWidth, getButtonHeight());
        return newButtonWidth;
    }

    private int determinePlusButtonWidth() {
        return Math.min(getButtonHeight(), getWidth() - getPaddingLeft() - getPaddingRight());
    }

    private int getButtonHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    public void setButtonClickedListener(final ButtonClickedListener buttonClickedListener) {
        this.buttonClickedListener = buttonClickedListener;

        for (final MoveableButton button : buttons) {
            setCurrentButtonClickedListenerSingleButton(button);
        }
    }

    private void setCurrentButtonClickedListenerSingleButton(final MoveableButton button) {

        if (buttonClickedListener == null) {
            button.setOnClickListener(null);
        } else {
            button.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    buttonClickedListener.onButtonClicked(button);
                }
            });
        }
    }

    public void setSoundChangedListener(final SoundChangedListener soundChangedListener) {
        this.soundChangedListener = soundChangedListener;
    }

    private ArrayList<Bundle> getSounds() {
        ArrayList<Bundle> sounds = new ArrayList<>();
        for(MoveableButton b : buttons)
            sounds.add(b.getProperties());
        return sounds;
    }

    public int numSounds() {
        return buttons.size();
    }

    public void setSounds(List<Bundle> sounds) {
        boolean soundChanged = false;

        for(int i = 0; i < sounds.size(); ++i){

            if(i < buttons.size()){
                MoveableButton b = buttons.get(i);

                if(!SoundProperties.equal(b.getProperties(), sounds.get(i))){
                    b.setProperties(sounds.get(i), true);
                    soundChanged = true;
                }
            }
            else {
                MoveableButton b = createButton(i);
                assert b != null;
                if(!SoundProperties.equal(b.getProperties(), sounds.get(i)))
                    b.setProperties(sounds.get(i), true);
//                b.setProperties(sounds.get(i));
                buttons.add(i, b);
                soundChanged = true;
            }
        }

        while(buttons.size() > sounds.size()) {
            MoveableButton b = buttons.get(buttons.size() - 1);
            buttons.remove(b);
            ViewGroup viewGroup = (ViewGroup) getParent();
            if (viewGroup != null)
                viewGroup.removeView(b);
            soundChanged = true;
        }

        if(soundChanged) {
            repositionButtons(); // this will also call the SoundChangedListener
        }
    }

    public void animateButton(int buttonidx, long duration) {
        if(buttonidx >= 0 && buttonidx < buttons.size()){
            buttons.get(buttonidx).animateColor(duration);
        }
    }

    public float getButtonVolume(int buttonidx) {
        return buttons.get(buttonidx).getProperties().getFloat("volume",0);
    }
}
