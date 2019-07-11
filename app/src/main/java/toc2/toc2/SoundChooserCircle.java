package toc2.toc2;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

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
        int desiredSize = dp_to_px(500) + (Math.max(getPaddingBottom()+getPaddingTop(), getPaddingLeft()+getPaddingRight()));

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

        Log.v("Metronome", "SoundChooserCircle:readAttributes");
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SoundChooserCircle);
        highlightButtonColor = ta.getColor(R.styleable.SoundChooserCircle_highlightColor, Color.BLUE);
        normalButtonColor = ta.getColor(R.styleable.SoundChooserCircle_normalColor, Color.BLACK);

        ta.recycle();
    }

    private void init() {
         Log.v("Metronome", "SoundChooserCircle:init()");
        final int buttonSize = dp_to_px(80);
        float cx = getWidth() / 2.0f + getLeft();
        float cy = getHeight() / 2.0f + getTop();
        float rad = Math.min(getWidth(),getHeight()) / 2.0f - buttonSize/2.0f;

        ViewGroup viewGroup = (ViewGroup) this.getParent();
        assert viewGroup != null;

        buttons = new ArrayList<>();

        for(int i = 0; i < Sounds.getNumSoundID(); ++i) {

            final int isound = i;
//        MoveableButton button = new MoveableButton(this.context, normalButtonColor, highlightButtonColor);
            final MoveableButton button = new MoveableButton(this.context, normalButtonColor, highlightButtonColor);

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
            button.setScaleType(ImageView.ScaleType.FIT_CENTER);
            button.setProperties(properties);
            ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(buttonSize, buttonSize);
            button.setLayoutParams(params);
            button.setElevation(24);

            int pad = dp_to_px(5);
            button.setPadding(pad, pad, pad, pad);

            viewGroup.addView(button);
            //addView(button);

            float bcx = cx - rad * (float) Math.sin(2.0*Math.PI / Sounds.getNumSoundID() * i);
            float bcy = cy - rad * (float) Math.cos(2.0*Math.PI / Sounds.getNumSoundID() * i);
            button.setTranslationX(bcx - buttonSize / 2.0f);
            button.setTranslationY(bcy - buttonSize / 2.0f);

            buttons.add(button);
        }
        setActiveSoundID(currentSoundID);
    }

    private int dp_to_px(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
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
