package de.moekadu.metronome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Vector;

public class VolumeSliders extends FrameLayout {

    private float defaultHeight = Utilities.dp_to_px(300);
    private float desiredTunerHeight = Utilities.dp_to_px(200);
    private float desiredTunerWidth = Utilities.dp_to_px(35);
    private float tunerWidth = desiredTunerWidth;
    private float buttonTunerSpacing = Utilities.dp_to_px(8);
    private float tunerSpacing = Utilities.dp_to_px(4);
    private float elementPadding = Utilities.dp_to_px(4);
    private int buttonHeight = Math.round(Utilities.dp_to_px(40));
    private boolean folded = true;
    private float foldingValue = 0.0f;

    final ArrayList<VolumeControl> volumeControls = new ArrayList<>();
    ImageButton button;
    ImageButton background;

    int interactiveColor;
    int onInteractiveColor;
    int elementBackgroundColor;
    int backgroundColor;

    ValueAnimator unfoldAnimator = ValueAnimator.ofFloat(0, 1);

    private VolumeChangedListener volumeChangedListener = null;

    interface VolumeChangedListener {
        void onVolumeChanged(int sliderIdx, float volume);
    }

    public VolumeSliders(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
//        Log.v("Metronome", "VolumeSliders" + getLeft());
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.VolumeSliders);
            interactiveColor = ta.getColor(R.styleable.VolumeSliders_interactiveColor, Color.BLACK);
            onInteractiveColor = ta.getColor(R.styleable.VolumeSliders_onInteractiveColor, Color.WHITE);
            elementBackgroundColor = ta.getColor(R.styleable.VolumeSliders_elementBackgroundColor, Color.WHITE);
            backgroundColor = ta.getColor(R.styleable.VolumeSliders_backgroundColor, Color.BLACK);
            ta.recycle();

            button = new ImageButton(getContext(), attrs);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        MarginLayoutParams layoutParams = (MarginLayoutParams) getLayoutParams();

        int desiredWidth = Integer.MAX_VALUE;
        int desiredHeight = Math.round(layoutParams.topMargin + layoutParams.bottomMargin + defaultHeight);

        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
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


    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
//        Log.v("Metronome", "VolumeSliders:onSaveInstanceState");

        Bundle bundle = new Bundle();
        bundle.putParcelable("superState", super.onSaveInstanceState());
        bundle.putBoolean("foldedState", folded);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
//        Log.v("Metronome", "VolumeSliders:onRestoreInstanceState " + (state instanceof Bundle));

        if (state instanceof Bundle) // implicit null check
        {
            Bundle bundle = (Bundle) state;
            folded = bundle.getBoolean("foldedState");
//            Log.v("Metronome", "Folded: " + folded);
            foldingValue = folded ? 0.0f : 1.0f;
            state = bundle.getParcelable("superState");
        }
        super.onRestoreInstanceState(state);

    }
    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        dispatchFreezeSelfOnly(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }


    void init() {
//        ViewGroup viewGroup = (ViewGroup) this.getParent();
//        if (viewGroup == null)
//            return;
        setTranslationZ(Utilities.dp_to_px(24));

//        Log.v("Metronome", "aaaaaaaabaaaa " + getPaddingLeft());
        unfoldAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                foldingValue = (float) animation.getAnimatedValue();
//                button.setTranslationZ(value * Utilities.dp_to_px(8));
                button.setTranslationY(foldingValue * (getUnfoldedButtonTop()) + (1-foldingValue) * (getFoldedButtonTop()));

                for(VolumeControl v : volumeControls)
                    v.setTranslationY(getTunerTop());
                background.setTranslationY(foldingValue * getTop() + (1-foldingValue) * getBottom());
            }
        });


        MarginLayoutParams params = new MarginLayoutParams(Math.round(Utilities.dp_to_px(90)), buttonHeight);
        button.setLayoutParams(params);
        int pad = Math.round(Utilities.dp_to_px(0));
        button.setPadding(pad, pad, pad, pad);
        button.setElevation(Utilities.dp_to_px(2));
//        button.setTranslationX(elementPadding);
        button.setTranslationX(Utilities.dp_to_px(4));
        button.setTranslationY(folded ? getFoldedButtonTop() : getUnfoldedButtonTop());
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setImageResource(folded ? R.drawable.ic_tune_arrow : R.drawable.ic_tune_arrow_down);
        button.setColorFilter(onInteractiveColor);

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (folded) {
                    unfoldAnimator.start();
                    button.setImageResource(R.drawable.ic_tune_arrow_down);
                    folded = false;
                }
                else {
                    unfoldAnimator.reverse();
                    button.setImageResource(R.drawable.ic_tune_arrow);
                    folded = true;
                }
            }
        });

//        viewGroup.addView(button);
        addView(button);

        background = new ImageButton(getContext());
        background.setBackgroundColor(backgroundColor);
        params.height = getHeight();
        params.width = getWidth();
        background.setLayoutParams(params);
        background.setTranslationY(folded ? getBottom() : getTop());
        background.setAlpha(0.7f);
        addView(background);
    }

    private float getUnfoldedButtonTop() {
        return getBottom() - (elementPadding + getTunerHeight() + buttonTunerSpacing + buttonHeight);
    }

    private float getFoldedButtonTop() {
        return getBottom() - elementPadding - buttonHeight;
    }

    private float getTunerHeight() {
        float maxHeight = getHeight() - elementPadding - elementPadding - buttonHeight - buttonTunerSpacing;
        return Math.min(maxHeight, desiredTunerHeight);
    }

    private float getUnfoldedTunerTop() {
        return getBottom() - (elementPadding + getTunerHeight());
    }

    private float getFoldedTunerTop() {
        return Math.max(getFoldedButtonTop() + buttonHeight + buttonTunerSpacing, getBottom());
    }

    private float getTunerTop() {
        return foldingValue * getUnfoldedTunerTop() + (1-foldingValue) * getFoldedTunerTop();
    }

    public void setTunersAt(Vector<Float> positions, Vector<Float> volume){
        if(positions.size() < 2)
            tunerWidth = desiredTunerWidth;
        else
            tunerWidth = Math.min(desiredTunerWidth, positions.get(1) - positions.get(0) - tunerSpacing);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Math.round(tunerWidth), Math.round(getTunerHeight()));

        if(BuildConfig.DEBUG && (positions.size() != volume.size()))
            throw new AssertionError("positions and volumeControls have different size");

        for(int i = positions.size(); i < volumeControls.size(); ++i) {
            VolumeControl vC = volumeControls.get(i);
            vC.setVisibility(GONE);
        }

        float top = getTunerTop();
        for(int i = 0; i < positions.size(); ++i) {
            VolumeControl vC;
            boolean addToView = false;
            if(volumeControls.size() <= i) {
//                vC = new VolumeControl(getContext(), null);
                vC = createVolumeControl();
                volumeControls.add(vC);
                vC.setTranslationY(top);
                final int currentIdx = volumeControls.size()-1;

                vC.setOnVolumeChangedListener(new VolumeControl.OnVolumeChangedListener() {
                    @Override
                    public void onVolumeChanged(float volume) {
                        if(volumeChangedListener != null)
                            volumeChangedListener.onVolumeChanged(currentIdx, volume);
                    }
                });

                addToView = true;
            }
            else {
                vC = volumeControls.get(i);
            }

            vC.setTranslationX(positions.get(i) - tunerWidth/2.0f);
            vC.setVisibility(VISIBLE);
            vC.setState(volume.get(i));
            vC.setLayoutParams(params);

            if(addToView)
                addView(vC);
        }

    }

    private VolumeControl createVolumeControl() {

        VolumeControl volumeControl = new VolumeControl(getContext(), null);
//        volumeControl.setTranslationX(getLeft() + elementPadding);
        volumeControl.setTranslationY(getTunerTop());
        volumeControl.setElevation(Utilities.dp_to_px(2));
        volumeControl.setVertical(true);
        volumeControl.setBackgroundColor(elementBackgroundColor);
        volumeControl.setSliderColor(interactiveColor);
        MarginLayoutParams params = new MarginLayoutParams(Math.round(tunerWidth), Math.round(getTunerHeight()));
        volumeControl.setPadding(0,0,0,0);
        volumeControl.setLayoutParams(params);
        return volumeControl;
    }

    public void setVolumeChangedListener(VolumeChangedListener volumeChangedListener) {
        this.volumeChangedListener = volumeChangedListener;
    }
}
