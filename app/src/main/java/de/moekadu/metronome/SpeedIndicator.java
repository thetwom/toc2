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

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

public class SpeedIndicator extends ControlPanel {

    private Paint circlePaint;

    private Path pathOuterCircle = null;

    private int highlightColor;
//    private int normalColor;
//    private int labelColor;

    private boolean stopped = true;
    private float position = 0.0f;
    private final int nPoints = 12;
    private float speed = 100.0f;

    private final ValueAnimator animatePosition = ValueAnimator.ofFloat(0.0f, 360.0f / nPoints);

    public SpeedIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);

        animatePosition.setDuration(getDt(100.0f));
        animatePosition.setInterpolator(new LinearInterpolator());

        animatePosition.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                position = (float) animation.getAnimatedValue();
                invalidate();
            }
        });

    }


    private long getDt(float speed) {
        return Math.round(1000.0 * 60.0 / speed);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        circlePaint = new Paint();
        circlePaint.setAntiAlias(true);

        if (attrs == null)
            return;

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SpeedIndicator);
        highlightColor = ta.getColor(R.styleable.SpeedIndicator_highlightColor, Color.GRAY);
//        normalColor = ta.getColor(R.styleable.SpeedPanel_normalColor, Color.GRAY);
//        labelColor = ta.getColor(R.styleable.SpeedPanel_labelColor, Color.WHITE);

        ta.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int cx = getCenterX();
        int cy = getCenterY();

        float rad = 0.0f * getRadius() + 1.0f * getInnerRadius();

        circlePaint.setColor(highlightColor);
        circlePaint.setStrokeWidth(Utilities.dp_to_px(2));
        circlePaint.setStyle(Paint.Style.FILL);

        if (pathOuterCircle == null)
            pathOuterCircle = new Path();
        pathOuterCircle.setFillType(Path.FillType.EVEN_ODD);

        for (int i = 0; i < nPoints; ++i) {
            double ang = (position + i * 360.0f / nPoints) * Math.PI / 180.0;
            float pointSize = Utilities.dp_to_px(5);

            double scaleDist = 7.0 * Math.PI / 180.0 * speed / 80.0;
            if (ang < scaleDist && !stopped)
                pointSize = pointSize * (1.0f + 4.0f * (float) Math.sin(ang / scaleDist * Math.PI));
            //if(ang < scaleDist && !stopped)
            //    pointSize = pointSize * (1.0f + 4.0f * (float) Math.cos(ang / scaleDist * Math.PI/2.0));
            //if(ang > 2.0*Math.PI - scaleDist && !stopped)
            //    pointSize = pointSize * (1.0f + 4.0f * (float) Math.cos((2.0*Math.PI - ang) / scaleDist * Math.PI/2.0));

            //canvas.drawArc(cx - rad, cy - rad, cx + rad, cy + rad, -90.0f, position, false, circlePaint);
            canvas.drawCircle(cx + rad * (float) Math.sin(ang), cy - rad * (float) Math.cos(ang), pointSize, circlePaint);
        }

    }

    public void stopPlay() {
        animatePosition.pause();
        position = 0.0f;
        stopped = true;
        invalidate();
    }

    public void animatePosition() {
        stopped = false;
        animatePosition.start();
    }

    public void setSpeed(float speed) {
        animatePosition.setDuration(getDt(speed));
        this.speed = speed;
    }
}
