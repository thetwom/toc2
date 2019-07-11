package toc2.toc2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import androidx.annotation.Nullable;

import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

public class SpeedPanel extends ControlPanel {

    private Paint circlePaint;

    private float previous_x;
    private float previous_y;
    private int previous_speed;
    //final private int strokeWidth = dp_to_px(2);
    //final static public float innerRadiusRatio = 0.62f;
    private Path pathOuterCircle = null;

    private boolean changingSpeed = false;

    private int highlightColor;
    private int normalColor;
    private int labelColor;

    public interface SpeedChangedListener {
        void onSpeedChanged(int speed);
    }

    private SpeedChangedListener speedChangedListener;


    public SpeedPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
        speedChangedListener = null;

        ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int radius = getRadius();
                int cx = getCenterX();
                int cy = getCenterY();
                outline.setOval(cx - radius, cy - radius, cx + radius, cy + radius);
            }
        };
        setOutlineProvider(outlineProvider);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        circlePaint = new Paint();
        circlePaint.setAntiAlias(true);

        if(attrs == null)
            return;

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SpeedPanel);
        highlightColor = ta.getColor(R.styleable.SpeedPanel_highlightColor, Color.GRAY);
        normalColor = ta.getColor(R.styleable.SpeedPanel_normalColor, Color.GRAY);
        labelColor = ta.getColor(R.styleable.SpeedPanel_labelColor, Color.WHITE);

        ta.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int radius = getRadius();
        int cx = getCenterX();
        int cy = getCenterY();

        int innerRadius = getInnerRadius();

        //circlePaint.setColor(foregroundColor);
        circlePaint.setColor(normalColor);

        //circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStyle(Paint.Style.FILL);
        //circlePaint.setStrokeWidth(strokeWidth);

        if(pathOuterCircle == null)
            pathOuterCircle = new Path();
        pathOuterCircle.setFillType(Path.FillType.EVEN_ODD);

        pathOuterCircle.rewind();
        pathOuterCircle.addCircle(cx, cy, radius, Path.Direction.CW);

        canvas.drawPath(pathOuterCircle, circlePaint);
        pathOuterCircle.rewind();

        if(changingSpeed) {
            circlePaint.setColor(highlightColor);
        }
        else {
            circlePaint.setColor(labelColor);
        }
        circlePaint.setStyle(Paint.Style.STROKE);
        float growthFactor = 1.1f;
        float speedRad = 0.5f* (radius + innerRadius);
        float strokeWidth = 0.3f * (radius - innerRadius);
        circlePaint.setStrokeWidth(strokeWidth);
        float angleMax  = -50.0f;
        float angleMin = -130.0f;
        float dAngle = 3.0f;

        float angle = angleMax -dAngle + 2.0f;
        while (angle >= angleMin) {
            canvas.drawArc(cx - speedRad, cy - speedRad, cx + speedRad, cy + speedRad, angle, -2f, false, circlePaint);
            dAngle *= growthFactor;
            angle -= dAngle;
        }

        circlePaint.setStyle(Paint.Style.FILL);

        float radArrI = speedRad - 0.5f * strokeWidth;
        float radArrO = speedRad + 0.5f * strokeWidth;
        double angleMinRad = angleMin * Math.PI / 180.0f;
        double dArrAngle = strokeWidth / speedRad;
        pathOuterCircle.moveTo(cx + radArrI * (float) Math.cos(angleMinRad), cy + radArrI * (float) Math.sin(angleMinRad));
        pathOuterCircle.lineTo(cx + radArrO * (float) Math.cos(angleMinRad), cy + radArrO * (float) Math.sin(angleMinRad));
        pathOuterCircle.lineTo(cx + speedRad * (float) Math.cos(angleMinRad-dArrAngle), cy + speedRad * (float) Math.sin(angleMinRad-dArrAngle));
        canvas.drawPath(pathOuterCircle, circlePaint);
        pathOuterCircle.rewind();

        double angleMaxRad = angleMax * Math.PI / 180.0f;
        pathOuterCircle.moveTo(cx + radArrI * (float) Math.cos(angleMaxRad), cy + radArrI * (float) Math.sin(angleMaxRad));
        pathOuterCircle.lineTo(cx + radArrO * (float) Math.cos(angleMaxRad), cy + radArrO * (float) Math.sin(angleMaxRad));
        pathOuterCircle.lineTo(cx + speedRad * (float) Math.cos(angleMaxRad+dArrAngle), cy + speedRad * (float) Math.sin(angleMaxRad+dArrAngle));
        canvas.drawPath(pathOuterCircle, circlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int action = event.getActionMasked();
        float x = event.getX() - getCenterX();
        float y = event.getY() - getCenterY();

        int radius = getRadius();
        int innerRadius = getInnerRadius();
        float circum = getRadius() * (float)Math.PI;
        float factor = 20.0f;
        int radiusXY = (int) Math.round(Math.sqrt(x*x + y*y));

        switch(action) {
            case MotionEvent.ACTION_DOWN:
                if (radiusXY > radius*1.1) {
                 return false;
                }
                else {
                    previous_x = x;
                    previous_y = y;
                    previous_speed = 0;
                    changingSpeed = true;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if(changingSpeed) {
                    float dx = x - previous_x;
                    float dy = y - previous_y;
                    int speed = -(int) Math.round((dx * y - dy * x) / Math.sqrt(x * x + y * y) / circum * factor);

                    if (previous_speed != speed && speedChangedListener != null) {
                        speedChangedListener.onSpeedChanged(speed);
                        previous_x = x;
                        previous_y = y;
                    }
                    previous_speed = speed;
                }
                return true;
            case MotionEvent.ACTION_UP:
                changingSpeed = false;
                invalidate();
        }

        return true;
    }

    void setOnSpeedChangedListener(SpeedChangedListener listener){
        speedChangedListener = listener;
    }

//    private int getRadius(){
//        int width = getWidth();
//        int height = getHeight();
//        int widthNoPadding = width - getPaddingRight() - getPaddingLeft();
//        int heightNoPadding = height - getPaddingTop() - getPaddingBottom();
//        return (Math.min(widthNoPadding, heightNoPadding) - strokeWidth) / 2;
//    }
//
//
//    private int getInnerRadius() {
//        return Math.round(getRadius() * innerRadiusRatio);
//    }
//
//    private int getCenterX(){
//        return getWidth() / 2;
//    }
//    private int getCenterY(){
//        return getHeight() / 2;
//    }
//
//    private float pTX(double phi, double rad) {
//        return (float) (rad * Math.cos(phi)) + getCenterX();
//    }
//    private float pTY(double phi, double rad) {
//        return (float) (rad * Math.sin(phi)) + getCenterY();
//    }
//
//    private int dp_to_px(int dp) {
//        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
//    }
}
