package toc2.toc2;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.animation.DynamicAnimation;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.util.ArrayList;

public class SoundChooser extends FrameLayout {

    final private Context context;

    private int spacing = dp_to_px(2);
    private int defaultButtonHeight = dp_to_px(70);

    final private ArrayList<MoveableButton> buttons = new ArrayList<>();
    private ImageButton plusButton;

    private SpringAnimation springAnimationX;
    private SpringAnimation springAnimationY;

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
    }

    public SoundChooser(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
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

        plusButton = createPlusButton();

        springAnimationX = new SpringAnimation(plusButton, DynamicAnimation.X).setSpring(
                new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(SpringForce.STIFFNESS_HIGH)
        );
        springAnimationY = new SpringAnimation(plusButton, DynamicAnimation.Y).setSpring(
                new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(SpringForce.STIFFNESS_HIGH)
        );

    }

    private MoveableButton createButton(int pos) {
        MoveableButton button = new MoveableButton(this.context);

        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bundle properties;
        if(buttons.isEmpty()) {
            properties = new Bundle();
            properties.putFloat("volume", 1.0f);
            properties.putInt("soundid", 0);
        }
        else {
            properties = buttons.get(buttons.size()-1).getProperties();
        }
        //button.setImageResource(R.drawable.ic_hihat);
        button.setProperties(properties);

        //FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(getButtonWidth(), getButtonHeight());
        MarginLayoutParams params = new MarginLayoutParams(getButtonWidth(), getButtonHeight());
        // params.setMargins(1000, 1000, 1000, 1000);
        //params.topMargin = getPaddingTop() + Math.round(getY());
        //params.leftMargin = Math.round(indexToPosX(pos)) + Math.round(getX());

        button.setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimary));
        button.setLayoutParams(params);
        int pad = dp_to_px(5);
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
                if (buttonOverPlusButton(posX, posY))
                    plusButton.setBackgroundColor(ContextCompat.getColor(context, R.color.colorAccent));
                else
                    plusButton.setBackground(null);

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

        setCurrentButtonClickedListenerSingleButton(button);

        return button;
    }

    private void repositionButtons() {

        for (int i = 0; i < buttons.size(); ++i) {
            MoveableButton b = buttons.get(i);
            ViewGroup.LayoutParams params = b.getLayoutParams();
            params.width = getButtonWidth();
            b.setLayoutParams(params);
        }

        for (int i = 0; i < buttons.size(); ++i) {
            buttons.get(i).setNewPosition(indexToPosX(i) + getX(), getPaddingTop() + getY());
        }

        repositionPlusButton(indexToPosX(buttons.size()), getPaddingTop());

        if(soundChangedListener != null)
            soundChangedListener.onSoundChanged(getSounds());
    }

    private ImageButton createPlusButton() {

        ImageButton button = new ImageButton(this.context);

        button.setScaleType(ImageView.ScaleType.FIT_XY);
        button.setImageResource(R.drawable.ic_add);
        button.setBackground(null);

        int buttonHeight = getButtonHeight();
        int buttonWidth = Math.min(buttonHeight, getWidth() - getPaddingLeft() - getPaddingRight());
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

    private void repositionPlusButton(float posX, float posY) {
        springAnimationX.getSpring().setFinalPosition(posX);
        springAnimationY.getSpring().setFinalPosition(posY);
        springAnimationX.start();
        springAnimationY.start();
    }

    private void buttonStartsMoving(MoveableButton button, float posX, float posY) {
        plusButton.setImageResource(R.drawable.ic_delete);
    }

    private void buttonEndsMoving(MoveableButton button, float posX, float posY) {
        plusButton.setImageResource(R.drawable.ic_add);
        plusButton.setBackground(null);
        if (buttonOverPlusButton(posX, posY)) {
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

    private float indexToPosX(int buttonIndex) {
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

    //@Override
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

    private int dp_to_px(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    private int getButtonWidth() {
        int plusButtonWidth = plusButton.getWidth();
        int newButtonWidth = Math.round(
                (getWidth() - getPaddingLeft() - getPaddingRight() - plusButtonWidth)
                        / (float) buttons.size()) - spacing;
        newButtonWidth = Math.min(newButtonWidth, getButtonHeight());
        return newButtonWidth;
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

    public ArrayList<Bundle> getSounds() {
        ArrayList<Bundle> sounds = new ArrayList<>();
        for(MoveableButton b : buttons)
            sounds.add(b.getProperties());
        return sounds;
    }

    public void setSounds(ArrayList<Bundle> sounds) {
        boolean soundChanged = false;

        for(int i = 0; i < sounds.size(); ++i){

            if(i < buttons.size()){
                MoveableButton b = buttons.get(i);

                if(!SoundProperties.equal(b.getProperties(), sounds.get(i))){
                    b.setProperties(sounds.get(i));
                    soundChanged = true;
                }
            }
            else{
                MoveableButton b = createButton(i);
                b.setProperties(sounds.get(i));
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

    public void animateButton(int buttonidx) {
        if(buttonidx >= 0 && buttonidx < buttons.size()){
            buttons.get(buttonidx).animateColor();
        }
    }
}
