package com.luck.picture.lib.widget;

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luck.picture.lib.R
import kotlinx.android.synthetic.main.item_fast_scroller.view.*
import kotlin.math.max
import kotlin.math.min


/*************************
 * This is the best one
 * https://github.com/kevinstyle/Bubbly
 *************************/
class RecyclerFastScroller @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {

    private val onFastScrollListener: OnFastScrollListener = OnFastScrollListener()
    private var handleStateListener: HandleStateListener? = null
    private var scrollerHeight: Int = 0
    private var recyclerView: RecyclerView? = null
    private var currentAnimator: ObjectAnimator? = null

    companion object {
        private const val BUBBLE_ANIMATION_DURATION: Long = 300
        private const val TRACK_SNAP_RANGE: Int = 5
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.item_fast_scroller, this, true)
        bubble.visibility = View.GONE

        orientation = HORIZONTAL
        clipChildren = false
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (height != 0) {
            scrollerHeight = height
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val action = event?.action ?: return false
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x < handle.x - ViewCompat.getPaddingStart(handle)) return false
                currentAnimator?.cancel()

                if (bubble.visibility == View.GONE) {
                    showBubble()
                }
                handle.isSelected = true
                val y = event.y
                setFastScrollerPosition(y)
                setRecyclerViewPosition(y)
                // kevin
                handleStateListener?.onEngaged()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val y = event.y
                setFastScrollerPosition(y)
                setRecyclerViewPosition(y)
                handleStateListener?.onDragged(y, 0) //TODO correct position
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handle.isSelected = false
                hideBubble()
                // kevin
                val y = event.y
                handleStateListener?.onReleased(getPosition(y))
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        recyclerView?.removeOnScrollListener(onFastScrollListener)
        super.onDetachedFromWindow()
    }

    fun setRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        this.recyclerView?.addOnScrollListener(onFastScrollListener)
    }

    fun setHandleStateListener(handleStateListener: HandleStateListener) {
        this.handleStateListener = handleStateListener
    }

    private fun showBubble() {
        bubble.visibility = View.VISIBLE
        currentAnimator?.cancel()
        currentAnimator = ObjectAnimator.ofFloat(bubble, "alpha", 0f, 1f).apply {
            duration = BUBBLE_ANIMATION_DURATION
            start()
        }
    }

    private fun hideBubble() {
        currentAnimator?.cancel()
        currentAnimator = ObjectAnimator.ofFloat(bubble, "alpha", 1f, 0f).apply {
            duration = BUBBLE_ANIMATION_DURATION
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    super.onAnimationEnd(animation)
                    hide()
                }

                override fun onAnimationCancel(animation: Animator?) {
                    super.onAnimationCancel(animation)
                    hide()
                }

                private fun hide() {
                    bubble.visibility = View.GONE
                    currentAnimator = null
                }
            })

            start()
        }
    }

    private fun updateFastScrollerPosition() {
        if (handle.isSelected) return

        recyclerView?.let {
            val verticalScrollOffset = it.computeVerticalScrollOffset()
            val verticalScrollRange = it.computeVerticalScrollRange()
            val verticalScrollExtent = it.computeVerticalScrollExtent()
            val proportion = verticalScrollOffset.toFloat() / (verticalScrollRange - verticalScrollExtent)
            setFastScrollerPosition(scrollerHeight * proportion)
        }
    }

    private fun setFastScrollerPosition(y: Float) {
        val handleHeight = handle.height
        handle.y = getValueInRange(0, scrollerHeight - handleHeight, (y - (handleHeight / 2)).toInt())

        val bubbleHeight = bubble.height
        bubble.y = getValueInRange(0, scrollerHeight - bubbleHeight - handleHeight / 2, (y - bubbleHeight).toInt())
    }

    private fun setRecyclerViewPosition(y: Float) {
        recyclerView?.let {
            val itemCount = it.adapter?.itemCount ?: 0
            val proportion = when {
                handle.y == 0f -> 0f
                handle.y + handle.height >= scrollerHeight - TRACK_SNAP_RANGE -> 1f
                else -> y / scrollerHeight.toFloat()
            }

            val targetPosition = getValueInRange(0, itemCount - 1, (proportion * itemCount.toFloat()).toInt()).toInt()

            // Kevin, add no position condition
            if (targetPosition != RecyclerView.NO_POSITION) {

                if (it.layoutManager is LinearLayoutManager) {
                    (it.layoutManager as LinearLayoutManager).scrollToPosition(targetPosition)
                } else {
                    it.layoutManager?.scrollToPosition(targetPosition)
                }

                if (it.adapter is FastScrollable) {
                    bubble.text = (it.adapter as FastScrollable).setBubbleText(targetPosition)
                }
            }
        }
    }

    private fun getPosition(y: Float): Int {
        val itemCount = recyclerView?.adapter?.itemCount ?: 0
        val proportion = when {
            handle.y == 0f -> 0f
            handle.y + handle.height >= scrollerHeight - TRACK_SNAP_RANGE -> 1f
            else -> y / scrollerHeight.toFloat()
        }

        val targetPosition = getValueInRange(0, itemCount - 1, (proportion * itemCount.toFloat()).toInt()).toInt()
        return if (targetPosition != RecyclerView.NO_POSITION) {
            targetPosition
        } else {
            0
        }
    }

    private fun getValueInRange(min: Int, max: Int, adjust: Int): Float {
        val minimum = max(min, adjust)
        return min(minimum, max).toFloat()
    }

    private inner class OnFastScrollListener : RecyclerView.OnScrollListener() {

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            updateFastScrollerPosition()
        }
    }

    interface FastScrollable {
        fun setBubbleText(position: Int): String
    }

    interface HandleStateListener {
        /**
         * Called when the handle is pressed and engaged for fastscroll behavior
         **/
        fun onEngaged() {}

        /**
         * Called when the handle is pressed and dragging for fastscroll behavior
         **/
        fun onDragged(offset: Float, position: Int)

        /**
         * Called when the handled is released, this marks the end of the fast scroll operation.
         * */
        fun onReleased(position: Int) {}
    }
}