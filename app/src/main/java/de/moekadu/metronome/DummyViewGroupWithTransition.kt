package de.moekadu.metronome

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

class DummyViewGroupWithTransition(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    private val dummyView = View(context)

    constructor(context: Context, attrs: AttributeSet? = null)
            : this(context, attrs, 0)

    init {
        addView(dummyView)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpec = MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY)
        dummyView.measure(widthSpec, heightSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        dummyView.layout(0, 0, 1, 1)
    }

    fun dummyTransition() {
        Log.v("Metronome", "DummyViewGroupWithTransition.dummyTransition")
        TransitionManager.beginDelayedTransition(this,
                AutoTransition().apply {
                    duration = 1L
                }
        )
        if (dummyView.visibility == View.VISIBLE)
            dummyView.visibility = View.GONE
        else
            dummyView.visibility = View.VISIBLE
    }
}