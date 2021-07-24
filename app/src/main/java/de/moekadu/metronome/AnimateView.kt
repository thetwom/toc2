package de.moekadu.metronome

import android.util.Log
import android.view.View

class AnimateView {
    companion object {
        fun emerge(view: View, animationDuration: Long, alphaEnd: Float = 1f, name: String? = null) {
//            if (name != null)
//                Log.v("Metronome", "AnimateView.emergeView: $name")
            val animationDurationCorrected =
                if (view.visibility == View.VISIBLE && view.alpha == alphaEnd) 0L else animationDuration
            if (view.visibility != View.VISIBLE)
                view.alpha = 0f
            view.animate().setDuration(animationDurationCorrected)
                .withStartAction {
                    view.visibility = View.VISIBLE
//                    Log.v(
//                        "Metronome",
//                        "AnimateView.emergeView.STARTACTION: $name, (d=$animationDuration)"
//                    )
                }
                .withEndAction {
//                    Log.v(
//                        "Metronome",
//                        "AnimateView.emergeView.ENDACTION: $name, (d=$animationDuration)"
//                    )
                }
                .alpha(alphaEnd)
        }

        fun hide(view: View, animationDuration: Long, name: String? = null) {
//            if (name != null)
//                Log.v("Metronome", "AnimateView.hideView: $name")
            val animationDurationCorrected =
                if (view.visibility == View.VISIBLE) animationDuration else 0L
            view.animate().setDuration(animationDurationCorrected)
                .alpha(0f)
                .withEndAction {
                    view.visibility = View.GONE
//                    Log.v(
//                        "Metronome",
//                        "AnimateView.hideView.ENDACTION: $name, (d=$animationDuration)"
//                    )
                }
        }

    }
}