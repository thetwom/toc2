package toc2.toc2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class IconListVisualizer extends LinearLayout {

    //    private ArrayList<Integer> icons;
    private ArrayList<Bundle> icons;

    private final ArrayList<MoveableButton> iconButtons = new ArrayList<>();
    private int normalColor;

    public IconListVisualizer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public IconListVisualizer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs){
//        setOrientation(LinearLayout.HORIZONTAL);

        //LinearLayout view = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.icon_list_visualizer, this);
//        LinearLayout view = (LinearLayout) inflate(context, R.layout.icon_list_visualizer, this);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.IconListVisualizer);
        normalColor = ta.getColor(R.styleable.IconListVisualizer_normalColor, Color.WHITE);
        ta.recycle();

        setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

        setDividerDrawable(ContextCompat.getDrawable(context, R.drawable.empty_divider));

//        setBackgroundColor(R.color.colorAccent);
        icons = new ArrayList<>();

//        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
//            @Override
//            public boolean onPreDraw() {
//                getViewTreeObserver().removeOnPreDrawListener(this);
//                drawIcons();
//                return true;
//            }
//        });
    }

//    public void setIcons(ArrayList<Integer> icons) {
//        this.icons = new ArrayList<>(icons);
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

    public void setIcons(ArrayList<Bundle> icons) {
        this.icons = new ArrayList<>(icons);

        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                drawIcons();
                return true;
            }
        });
    }

    private void drawIcons() {

        int maxButtonWidth = Math.round((getWidth() - getPaddingLeft() - getPaddingRight() - (icons.size()-1) * Utilities.dp_to_px(2)) / (float) icons.size());
        int defaultButtonWidth = getHeight() - getPaddingTop() - getPaddingBottom();
        int buttonWidth = Math.min(maxButtonWidth, defaultButtonWidth);

        for (MoveableButton button : iconButtons) {
            removeView(button);
        }
        iconButtons.clear();

        for(Bundle properties : icons){
            MoveableButton button = new MoveableButton(getContext(), normalColor, normalColor);
//            Bundle properties;
//            properties = new Bundle();
//            properties.putFloat("volume", 1.0f);
//            properties.putInt("soundid", icon);
            button.setProperties(properties, false);
            button.setLockPosition(true);
            MarginLayoutParams params = new MarginLayoutParams(buttonWidth, getHeight()-getPaddingTop()-getPaddingBottom());

            button.setLayoutParams(params);

            iconButtons.add(button);
            addView(button);
        }
    }
}
