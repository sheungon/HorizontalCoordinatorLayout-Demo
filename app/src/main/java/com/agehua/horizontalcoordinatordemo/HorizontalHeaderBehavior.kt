/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.agehua.horizontalcoordinatordemo

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.core.math.MathUtils
import androidx.core.view.ViewCompat

/**
 * See [HeaderScrollingViewBehavior].
 * Support horizontal touch scrolling.
 * For View on touch moving event and fling events.
 */
abstract class HorizontalHeaderBehavior<V : View>
    : ViewOffsetBehavior<V> {

    var mScroller: OverScroller? = null
    private var mFlingRunnable: Runnable? = null
    private var mIsBeingDragged = false
    private var mActivePointerId = INVALID_POINTER
    private var mLastMotionX = 0
    private var mTouchSlop = -1
    private var mVelocityTracker: VelocityTracker? = null

    constructor()
    constructor(context: Context?, attrs: AttributeSet?) : super(
        context,
        attrs
    )

    override fun onInterceptTouchEvent(
        parent: HorizontalCoordinatorLayout,
        child: V,
        ev: MotionEvent
    ): Boolean {
        if (mTouchSlop < 0) {
            mTouchSlop = ViewConfiguration.get(parent.context).scaledTouchSlop
        }
        val action = ev.action

        // Shortcut since we're being dragged
        if (action == MotionEvent.ACTION_MOVE && mIsBeingDragged) {
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mIsBeingDragged = false
                val x = ev.x.toInt()
                val y = ev.y.toInt()
                if (canDragView(child) && parent.isPointInChildBounds(child, x, y)) {
                    mLastMotionX = x
                    mActivePointerId = ev.getPointerId(0)
                    ensureVelocityTracker()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val activePointerId = mActivePointerId
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                } else {
                    val pointerIndex = ev.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = ev.getX(pointerIndex).toInt()
                        val xDiff = Math.abs(x - mLastMotionX)
                        if (xDiff > mTouchSlop) {
                            mIsBeingDragged = true
                            mLastMotionX = x
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                mIsBeingDragged = false
                mActivePointerId = INVALID_POINTER
                mVelocityTracker?.recycle()
                mVelocityTracker = null
            }
        }
        mVelocityTracker?.addMovement(ev)
        return mIsBeingDragged
    }

    override fun onTouchEvent(
        parent: HorizontalCoordinatorLayout,
        child: V,
        ev: MotionEvent
    ): Boolean {
        if (mTouchSlop < 0) {
            mTouchSlop = ViewConfiguration.get(parent.context).scaledTouchSlop
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x.toInt()
                val y = ev.y.toInt()
                if (parent.isPointInChildBounds(child, x, y) && canDragView(child)) {
                    mLastMotionX = x
                    mActivePointerId = ev.getPointerId(0)
                    ensureVelocityTracker()
                } else {
                    return false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val activePointerIndex = ev.findPointerIndex(mActivePointerId)
                if (activePointerIndex == -1) {
                    return false
                }
                val x = ev.getX(activePointerIndex).toInt()
                var dx = mLastMotionX - x
                if (!mIsBeingDragged && Math.abs(dx) > mTouchSlop) {
                    mIsBeingDragged = true
                    if (dx > 0) {
                        dx -= mTouchSlop
                    } else {
                        dx += mTouchSlop
                    }
                }
                if (mIsBeingDragged) {
                    mLastMotionX = x
                    // We're being dragged so scroll the ABL
                    scroll(parent, child, dx, getMaxDragOffset(child), 0)
                }
            }
            MotionEvent.ACTION_UP -> {
                mVelocityTracker?.let { velocityTracker ->
                    velocityTracker.addMovement(ev)
                    velocityTracker.computeCurrentVelocity(1000)
                    val xvel = velocityTracker.getXVelocity(mActivePointerId)
                    fling(parent, child, -getScrollRangeForDragFling(child), 0, xvel)
                }
                run {
                    mIsBeingDragged = false
                    mActivePointerId = INVALID_POINTER
                    mVelocityTracker?.recycle()
                    mVelocityTracker = null
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                mIsBeingDragged = false
                mActivePointerId = INVALID_POINTER
                mVelocityTracker?.recycle()
                mVelocityTracker = null
            }
        }
        mVelocityTracker?.addMovement(ev)
        return true
    }

    fun setHeaderLeftRightOffset(
        parent: HorizontalCoordinatorLayout,
        header: V,
        newOffset: Int
    ): Int = setHeaderLeftRightOffset(
        parent,
        header,
        newOffset,
        Int.MIN_VALUE,
        Int.MAX_VALUE
    )

    open fun setHeaderLeftRightOffset(
        parent: HorizontalCoordinatorLayout,
        header: V,
        newOffset: Int,
        minOffset: Int,
        maxOffset: Int
    ): Int {
        var offset = newOffset
        val curOffset: Int = leftAndRightOffset
        var consumed = 0
        if (minOffset != 0 && curOffset >= minOffset && curOffset <= maxOffset) {
            // If we have some scrolling range, and we're currently within the min and max
            // offsets, calculate a new offset
            offset = MathUtils.clamp(offset, minOffset, maxOffset)
            if (curOffset != offset) {
                setLeftAndRightOffset(offset)
                // Update how much dy we have consumed
                consumed = curOffset - offset
            }
        }
        return consumed
    }

    open fun getLeftRightOffsetForScrollingSibling(): Int {
        return leftAndRightOffset
    }

    fun scroll(
        horizontalCoordinatorLayout: HorizontalCoordinatorLayout,
        header: V,
        dx: Int,
        minOffset: Int,
        maxOffset: Int
    ): Int = setHeaderLeftRightOffset(
        horizontalCoordinatorLayout, header,
        leftAndRightOffset - dx, minOffset, maxOffset
    )

    fun fling(
        horizontalCoordinatorLayout: HorizontalCoordinatorLayout,
        layout: V,
        minOffset: Int,
        maxOffset: Int,
        velocityX: Float
    ): Boolean {
        if (mFlingRunnable != null) {
            layout.removeCallbacks(mFlingRunnable)
            mFlingRunnable = null
        }
        if (mScroller == null) {
            mScroller = OverScroller(layout.context)
        }
        mScroller?.fling(
            leftAndRightOffset, 0,  // curr
            Math.round(velocityX), 0,  // velocity.
            minOffset, maxOffset,  // x
            0, 0 // y
        )
        return if (mScroller?.computeScrollOffset() == true) {
            mFlingRunnable =
                FlingRunnable(
                    horizontalCoordinatorLayout,
                    layout
                )
            ViewCompat.postOnAnimation(layout, mFlingRunnable)
            true
        } else {
            onFlingFinished(horizontalCoordinatorLayout, layout)
            false
        }
    }

    /**
     * Called when a fling has finished, or the fling was initiated but there wasn't enough
     * velocity to start it.
     */
    open fun onFlingFinished(parent: HorizontalCoordinatorLayout, layout: V) {
        // no-op
    }

    /**
     * Return true if the view can be dragged.
     */
    open fun canDragView(view: V): Boolean = false

    /**
     * Returns the maximum px offset when `view` is being dragged.
     */
    open fun getMaxDragOffset(view: V): Int = -view.width

    open fun getScrollRangeForDragFling(view: V): Int = view.width

    private fun ensureVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
    }

    private inner class FlingRunnable internal constructor(
        private val mParent: HorizontalCoordinatorLayout,
        layout: V
    ) : Runnable {
        private val mLayout: V?
        override fun run() {
            val scroller = mScroller
            val layout = mLayout
            if (layout != null && scroller != null) {
                if (scroller.computeScrollOffset()) {
                    setHeaderLeftRightOffset(mParent, layout, scroller.currX)
                    // Post ourselves so that we run on the next animation
                    ViewCompat.postOnAnimation(layout, this)
                } else {
                    onFlingFinished(mParent, layout)
                }
            }
        }

        init {
            mLayout = layout
        }
    }

    companion object {
        private const val INVALID_POINTER = -1
    }
}