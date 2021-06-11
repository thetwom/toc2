package de.moekadu.metronome

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import kotlin.math.min

//class SoundChooserControlButtonManager(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
//    : ViewGroup(context, attrs, defStyleAttr) {
//
//    constructor(context: Context, attrs: AttributeSet? = null) : this(
//        context,
//        attrs,
//        R.attr.soundChooserControlButtonManagerStyle
//    )
//
//    private val controlButtons = ArrayList<SoundChooserControlButton>()
//
//    private val noteViewBoundingBox = Rect()
//    private val boundingBox = Rect()
//
//    private var runningTransition = TransitionStatus.Finished
//
//    init {
//        val ta = context.obtainStyledAttributes(
//            attrs, R.styleable.SoundChooserControlButtonManager,
//            defStyleAttr, R.style.Widget_AppTheme_SoundChooserControlButtonManagerStyle
//        )
//    }
//
//    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        for (i in controlButtons.indices) {
//            NoteView.computeBoundingBox(
//                i,
//                controlButtons.size,
//                noteViewBoundingBox.width(),
//                noteViewBoundingBox.height(),
//                boundingBox
//            )
////                Log.v("Metronome", "SoundChooser.measureChoiceBase: boundingBox=$boundingBox, controlButtons[n]=${controlButtons[n]}")
//            controlButtons[i].measure(
//                MeasureSpec.makeMeasureSpec(
//                    min(boundingBox.width(), boundingBox.height()),
//                    MeasureSpec.EXACTLY
//                ),
//                MeasureSpec.makeMeasureSpec(boundingBox.height(), MeasureSpec.EXACTLY)
//            )
//        }
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
//    }
//
//    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
//        for (i in controlButtons.indices) {
//            NoteView.computeBoundingBox(
//                i,
//                controlButtons.size,
//                noteViewBoundingBox.width(),
//                noteViewBoundingBox.height(),
//                boundingBox
//            )
//            boundingBox.offset(noteViewBoundingBox.left - l, noteViewBoundingBox.top - t)
//            val c = controlButtons[i]
//            // don't re-layout for base or dynamic case since this would clash with the current translationX/Y
//            if (c.translationXTarget == 0f && c.translationYTarget == 0f) {
//                val cL = (boundingBox.centerX() - 0.5f * c.measuredWidth).toInt()
//                c.layout(
//                    cL,
//                    boundingBox.top,
//                    cL + c.measuredWidth,
//                    boundingBox.top + c.measuredHeight
//                )
//            }
//        }
//    }
//
//    override fun onTouchEvent(event: MotionEvent?): Boolean {
////        Log.v("Metronome", "SoundChooserControlButtonManager.onTouchEvent: translationZ = $translationZ, elevation = $elevation, choiceStatus=$choiceStatus")
//        // it messes up to much if we allow input during a transition
//        if (runningTransition != TransitionStatus.Finished)
//            return true
//
//        if (event == null)
//            return super.onTouchEvent(event)
//
//        super.onTouchEvent(event)
//
//        val action = event.actionMasked
//        val x = event.x
//        val y = event.y
//
//        val controlButton = activeControlButton ?: return false
//
//        when(action) {
//            MotionEvent.ACTION_DOWN -> {
//
//                val noteViewLeftLocal = noteViewBoundingBox.left - left
//                val noteViewRightLocal = noteViewBoundingBox.right - left
//                val downOverNoteView = (y >= noteViewBoundingBox.top - top
//                        && x >= noteViewLeftLocal && x <= noteViewRightLocal)
//
//                if (downOverNoteView)
//                    parent.requestDisallowInterceptTouchEvent(true)
//
////                Log.v("Metronome", "SoundChooser.onTouchEvent: ACTION_DOWN, x=$x, y=$y")
//                if (choiceStatus == Status.Static)
//                    return false
//
//                controlButton.eventXOnDown = x
//                controlButton.eventYOnDown = y
//                controlButton.translationXInit = controlButton.translationX
//                controlButton.translationYInit = controlButton.translationY
//
//                if(choiceStatus != Status.Static && downOverNoteView) {
//                    activateBaseLayout()
//                    triggerStaticChooserOnUp = true
//                    return true
//                }
//            }
//            MotionEvent.ACTION_MOVE -> {
////                Log.v("Metronome", "SoundChooser.onTouchEvent: ACTION_MOVE, x=$x, y=$y, choiceStatus=$choiceStatus, inActiveBoundingBoxCheck=${inActiveBoundingBoxCheck()}, activeBoxLeft=$activeBoxLeft, activeBoxRight=$activeBoxRight")
//                val tX = controlButton.translationXInit + x - controlButton.eventXOnDown
//                val tY = controlButton.translationYInit + y - controlButton.eventYOnDown
//                if(choiceStatus == Status.Dynamic || choiceStatus == Status.Base) {
//                    controlButton.translationX = tX
//                    controlButton.translationY = tY
//
//                    if(!inActiveBoundingBoxCheck() && controlButton.width > 0)
//                        moveActiveNoteToNewBoundingBoxIfRequired()
//                }
//
//                if(choiceStatus == Status.Base && controlButton.width > 0) {
//                    if (tY + 0.5f * (controlButton.top + controlButton.bottom) < noteViewBoundingBox.top - top + 0.3 * noteViewBoundingBox.height())
//                        activateDynamicChoices()
//                }
//
//                if(choiceStatus == Status.Dynamic) {
//                    val tXC = tX + controlButton.right + elementPadding
//                    for(c in choiceButtons)
//                        c.translationX = tXC
//                    repositionDynamicChoices()
//                }
//            }
//            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                if(triggerStaticChooserOnUp) {
//                    activateStaticChoices()
//                }
//                else {
//                    if(deleteButton.isPressed) {
//                        activeControlButton?.let { cB ->
//                            cB.translationXTarget = 0.5f * (deleteButton.left + deleteButton.right) - 0.5f * (cB.left + cB.right)
//                            cB.translationYTarget = 0.5f * (deleteButton.top + deleteButton.bottom) - 0.5f * (cB.top + cB.bottom)
//                        }
//                        deleteActiveNoteIfPossible()
//                    }
////                    Log.v("Metronome", "SoundChooser.onTouchEvent: ACTION_UP and not static choices")
//                    deactivate()
//                }
//                return true
//            }
//        }
//        return false
//    }
//
//    enum class TransitionStatus {
//        Finished, Deactivating, ActivatingStatic
//    }
//}