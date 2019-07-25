package toc2.toc2;

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
