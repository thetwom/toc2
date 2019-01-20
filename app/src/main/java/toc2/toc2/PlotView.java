package toc2.toc2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * TODO: document your custom view class.
 */
public class PlotView extends View {


    public PlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    //@Override
    //protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

    //}

    private void init(AttributeSet attrs) {
        // Load attributes

    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(10f);

        int width = getWidth();
        int height = getHeight();

        canvas.drawCircle(width/2,height/2,height/2,paint);
    }
}
