package de.moekadu.metronome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import java.util.Vector;

public class SpeedIndicator2 extends View {

    private final int defaultHeight = Math.round(Utilities.dp_to_px(4));
    private final int defaultWidth = Math.round(Utilities.dp_to_px(100));

    private Paint paint = null;

    private int color = Color.BLACK;

    private int positionIndex = 0;
    private float position = 0.0f;
    private final ValueAnimator animatePosition = ValueAnimator.ofFloat(0.0f, 1.0f);

    private final Vector<Float> markPositions = new Vector<>();

    public SpeedIndicator2(Context context) {
        super(context);
        init(context, null);
    }

    public SpeedIndicator2(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SpeedIndicator2(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

//    public SpeedIndicator2(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
//        super(context, attrs, defStyleAttr, defStyleRes);
//    }

    private void init(Context context, @Nullable AttributeSet attrs) {

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SpeedIndicator2);
            color = ta.getColor(R.styleable.SpeedIndicator2_normalColor, Color.BLACK);
            ta.recycle();
        }

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);


        animatePosition.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                position = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animatePosition.setInterpolator(new LinearInterpolator());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) getLayoutParams();

        int desiredWidth = defaultWidth + getPaddingStart() + getPaddingEnd();
        int desiredHeight = defaultHeight + getPaddingTop() + getPaddingBottom();

        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //float width = positionStart + (positionEnd-positionStart) * position;
        float secEnd = markPositions.size() == 0 ? 0 : markPositions.get(positionIndex);
        float secStart = positionIndex == 0 ? 0 : markPositions.get(positionIndex-1);
        float barEnd = secStart + (secEnd-secStart) * position;

//        float end = markPositions[positionIndex]

//        Log.v("Metronome", "pos: " +position);

        canvas.drawRect(0,0.0f*getHeight(), barEnd, 1.0f*getHeight(), paint);

//        Log.v("Metronome", "pos: "+ markPositions.size());
        for(Float mark : markPositions){
//            canvas.drawCircle(mark, getHeight()/2.0f, getHeight()/2.0f, markPaint);
            canvas.drawRect(mark, 0, mark+getHeight(), getHeight(), paint);
        }

    }

    public void animate(int positionIndex, float speed){

        this.positionIndex = positionIndex;
        animatePosition.setDuration(Utilities.speed2dt(speed));
        animatePosition.start();
    }

    public void setMarks(Vector<Float> positions){
        markPositions.clear();
        markPositions.addAll(positions);
        invalidate();
    }

    public void stopPlay(){
        animatePosition.pause();
        position = 0.0f;
        positionIndex = 0;
        invalidate();
    }
}
