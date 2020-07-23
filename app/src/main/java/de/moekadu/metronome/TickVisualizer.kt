package de.moekadu.metronome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import kotlin.math.*

class TickVisualizer(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : ControlPanel(context, attrs, defStyleAttr) {

    constructor(context : Context, attrs : AttributeSet? = null) : this(context, attrs, R.attr.tickVisualizerStyle)

    private val paint = Paint()
    private val animator1 = ValueAnimator.ofFloat(0.0f, PI.toFloat())
    private val animator2 = ValueAnimator.ofFloat(0.0f, PI.toFloat())
    private var nextPoint = 1
    private var pointSize = 10f

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.TickVisualizer, defStyleAttr, R.style.Widget_AppTheme_TickVisualizerStyle)
            pointSize = ta.getDimension(R.styleable.TickVisualizer_pointSize, pointSize)
            ta.recycle()
        }
        paint.color = highlightColor
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        animator1.interpolator = LinearInterpolator()
        animator1.addUpdateListener { invalidate() }
        animator2.interpolator = LinearInterpolator()
        animator2.addUpdateListener { if (!animator1.isRunning) invalidate() }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        val circleRadius = pointSize / 2.0f
        val radiusPosition = radius - circleRadius

        val phi0 = asin(0.8f * circleRadius / radiusPosition)
        var phiMax1 = 80.0f / 180.0f * PI.toFloat() / Utilities.speed2dt(20f) * animator1.duration
        phiMax1 = min(phiMax1, 80.0f / 180.0f * PI.toFloat())
        phiMax1 = max(phiMax1, 20.0f / 180.0f * PI.toFloat())
        var phiMax2 = 80.0f / 180.0f * PI.toFloat() / Utilities.speed2dt(20f) * animator1.duration
        phiMax2 = min(phiMax2, 80.0f / 180.0f * PI.toFloat())
        phiMax2 = max(phiMax2, 20.0f / 180.0f * PI.toFloat())

        val dPhi1 = phiMax1 * sin(animator1.animatedValue as Float)
        val dPhi2 = phiMax2 * sin(animator2.animatedValue as Float)
        val cX1 = centerX + radiusPosition * sin(phi0 + dPhi1)
        val cX2 = centerX + radiusPosition * sin(-phi0 - dPhi2)
        val cY1 = centerY + radiusPosition * cos(phi0 + dPhi1)
        val cY2 = centerY + radiusPosition * cos(-phi0 - dPhi2)

        canvas?.drawCircle(cX1, cY1, circleRadius, paint)
        canvas?.drawCircle(cX2, cY2, circleRadius, paint)
    }

    fun tick(duration : Long) {
        if(nextPoint == 1) {
            animator1.end()
            animator1.duration = duration
            animator1.start()
            nextPoint = 2
        }
        else {
            animator2.end()
            animator2.duration = duration
            animator2.start()
            nextPoint = 1
        }
    }

    fun stop( ) {
        nextPoint = 1
    }
}