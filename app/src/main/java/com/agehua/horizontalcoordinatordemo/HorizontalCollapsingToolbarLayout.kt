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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import androidx.core.math.MathUtils
import androidx.core.util.ObjectsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class HorizontalCollapsingToolbarLayout
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var mStatusBarScrim: Drawable? = null
    var mCurrentOffset = 0
    var mLastInsets: WindowInsetsCompat? = null
    private var mRefreshToolbar = true
    private val mToolbarId: Int
    private var mToolbar: Toolbar? = null
    private var mToolbarDirectChild: View? = null
    private var mDummyView: View? = null
    private val mCollapsingTitleEnabled: Boolean
    private var mDrawCollapsingTitle = false
    private val mScrimAlpha = 0
    private val mScrimsAreShown = false
    private val mScrimAnimator: ValueAnimator? = null
    /**
     * Returns the duration in milliseconds used for scrim visibility animations.
     */
    /**
     * Set the duration used for scrim visibility animations.
     *
     * @param duration the duration to use in milliseconds
     * @attr ref android.support.design.R.styleable#CollapsingToolbarLayout_scrimAnimationDuration
     */
    var scrimAnimationDuration: Long
    private var mScrimVisibleHeightTrigger = -1
    private var mOnOffsetChangedListener: HorizontalAppBarLayout.OnOffsetChangedListener? =
        null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // Add an OnOffsetChangedListener if possible
        val parent = parent
        if (parent is HorizontalAppBarLayout) {
            // Copy over from the ABL whether we should fit system windows
            fitsSystemWindows = ViewCompat.getFitsSystemWindows(parent)
            if (mOnOffsetChangedListener == null) {
                mOnOffsetChangedListener =
                    OffsetUpdateListener()
            }
            parent.addOnOffsetChangedListener(mOnOffsetChangedListener)

            // We're attached, so lets request an inset dispatch
            // There is only one state, and can be consumed by one View.
            // When requestApplyInsets, it will return a WindowInsets.
            // And OnApplyWindowInsetsListener will be invoked.
            ViewCompat.requestApplyInsets(this)
        }
    }

    override fun onDetachedFromWindow() {
        // Remove our OnOffsetChangedListener if possible and it exists
        val parent = parent
        if (mOnOffsetChangedListener != null && parent is HorizontalAppBarLayout) {
            parent.removeOnOffsetChangedListener(
                mOnOffsetChangedListener
            )
        }
        super.onDetachedFromWindow()
    }

    fun onWindowInsetChanged(insets: WindowInsetsCompat): WindowInsetsCompat {
        var newInsets: WindowInsetsCompat? = null
        if (ViewCompat.getFitsSystemWindows(this)) {
            // If we're set to fit system windows, keep the insets
            newInsets = insets
        }

        // If our insets have changed, keep them and invalidate the scroll ranges...
        if (!ObjectsCompat.equals(mLastInsets, newInsets)) {
            mLastInsets = newInsets
            requestLayout()
        }

        // Consume the insets. This is done so that child views with fitSystemWindows=true do not
        // get the default padding functionality from View
        return insets.consumeSystemWindowInsets()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // If we don't have a toolbar, the scrim will be not be drawn in drawChild() below.
        // Instead, we draw it here, before our collapsing text.
        ensureToolbar()

        // Now draw the status bar scrim
        if (mStatusBarScrim != null && mScrimAlpha > 0) {
            val leftInset = mLastInsets?.systemWindowInsetLeft ?: 0
            if (leftInset > 0) {
                mStatusBarScrim?.setBounds(
                    0, -mCurrentOffset, width,
                    leftInset - mCurrentOffset
                )
                mStatusBarScrim?.mutate()?.alpha = mScrimAlpha
                mStatusBarScrim?.draw(canvas)
            }
        }
    }

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long
    ): Boolean {
        // This is a little weird. Our scrim needs to be behind the Toolbar (if it is present),
        // but in front of any other children which are behind it. To do this we intercept the
        // drawChild() call, and draw our scrim just before the Toolbar is drawn
        val invalidated = false
        return super.drawChild(canvas, child, drawingTime) || invalidated
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
    }

    private fun ensureToolbar() {
        if (!mRefreshToolbar) {
            return
        }

        // First clear out the current Toolbar
        mToolbar = null
        mToolbarDirectChild = null
        if (mToolbarId != -1) {
            // If we have an ID set, try and find it and it's direct parent to us
            mToolbar = findViewById<Toolbar>(mToolbarId)?.also {
                mToolbarDirectChild = findDirectChild(it)
            }
        }
        if (mToolbar == null) {
            // If we don't have an ID, or couldn't find a Toolbar with the correct ID, try and find
            // one from our direct children
            var toolbar: Toolbar? = null
            var i = 0
            val count = childCount
            while (i < count) {
                val child = getChildAt(i)
                if (child is Toolbar) {
                    toolbar = child
                    break
                }
                i++
            }
            mToolbar = toolbar
        }
        updateDummyView()
        mRefreshToolbar = false
    }

    private fun isToolbarChild(child: View): Boolean =
        if (mToolbarDirectChild == null || mToolbarDirectChild === this) child === mToolbar
        else child === mToolbarDirectChild

    /**
     * Returns the direct child of this layout, which itself is the ancestor of the
     * given view.
     */
    private fun findDirectChild(descendant: View): View? {
        var directChild = descendant
        var p = descendant.parent
        while (p !== this && p != null) {
            if (p is View) {
                directChild = p
            }
            p = p.parent
        }
        return directChild
    }

    private fun updateDummyView() {
        if (!mCollapsingTitleEnabled && mDummyView != null) {
            // If we have a dummy view and we have our title disabled, remove it from its parent
            val parent = mDummyView?.parent
            if (parent is ViewGroup) {
                parent.removeView(mDummyView)
            }
        }
        if (mCollapsingTitleEnabled && mToolbar != null) {
            if (mDummyView == null) {
                mDummyView = View(context)
            }
            if (mDummyView?.parent == null) {
                mToolbar?.addView(
                    mDummyView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var measureSpec = heightMeasureSpec
        ensureToolbar()
        super.onMeasure(widthMeasureSpec, measureSpec)
        val mode = MeasureSpec.getMode(measureSpec)
        val leftInset = mLastInsets?.systemWindowInsetLeft ?: 0
        if (mode == MeasureSpec.UNSPECIFIED && leftInset > 0) {
            // If we have a left inset and we're set to wrap_content height we need to make sure
            // we add the left inset to our height, therefore we re-measure
            measureSpec = MeasureSpec.makeMeasureSpec(
                measuredWidth + leftInset, MeasureSpec.EXACTLY
            )
            super.onMeasure(widthMeasureSpec, measureSpec)
        }
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.onLayout(changed, left, top, right, bottom)
        val lastInsets = mLastInsets
        if (lastInsets != null) {
            // Shift down any views which are not set to fit system windows
            val insetLeft = lastInsets.systemWindowInsetLeft
            var i = 0
            val z = childCount
            while (i < z) {
                val child = getChildAt(i)
                if (!ViewCompat.getFitsSystemWindows(child)) {
                    if (child.left < insetLeft) {
                        // If the child isn't set to fit system windows but is drawing within
                        // the inset offset it down
                        ViewCompat.offsetLeftAndRight(child, insetLeft)
                    }
                }
                i++
            }
        }

        // Update the collapsed bounds by getting it's transformed bounds
        val dummyView = mDummyView
        val toolbar = mToolbar
        val toolbarDirectChild = mToolbarDirectChild
        if (mCollapsingTitleEnabled && dummyView != null) {
            // We only draw the title if the dummy view is being displayed (Toolbar removes
            // views if there is no space)
            mDrawCollapsingTitle = (ViewCompat.isAttachedToWindow(dummyView)
                    && dummyView.visibility == View.VISIBLE)
            if (mDrawCollapsingTitle) {
                val isRtl = (ViewCompat.getLayoutDirection(this)
                        == ViewCompat.LAYOUT_DIRECTION_RTL)

                // Update the collapsed bounds
                if (toolbar != null) {
                    getMaxOffsetForPinChild(
                        toolbarDirectChild ?: toolbar
                    )
                }
                //                ViewGroupUtils.getDescendantRect(this, mDummyView, mTmpRect);
            }
        }

        // Update our child view offset helpers. This needs to be done after the title has been
        // setup, so that any Toolbars are in their original position
        var i = 0
        val z = childCount
        while (i < z) {
            getViewOffsetHelper(getChildAt(i)).onViewLayout()
            i++
        }

        // Finally, set our minimum height to enable proper AppBarLayout collapsing
        if (toolbar != null) {
            minimumWidth = if (toolbarDirectChild == null || toolbarDirectChild === this) {
                getWidthWithMargins(toolbar)
            } else {
                getWidthWithMargins(toolbarDirectChild)
            }
        } else {
            for (childIndex in 0 until childCount) {
                val view = getChildAt(childIndex)
                val lp = view.layoutParams as? LayoutParams ?: continue
                if (lp.collapseMode == LayoutParams.COLLAPSE_MODE_PIN) {
                    minimumWidth = getWidthWithMargins(
                        view
                    )
                }
            }
        }
    }

    //    @Override
    //    protected boolean verifyDrawable(Drawable who) {
    //        return super.verifyDrawable(who) || who == mContentScrim || who == mStatusBarScrim;
    //    }
    //    @Override
    //    public void setVisibility(int visibility) {
    //        super.setVisibility(visibility);
    //
    //        final boolean visible = visibility == VISIBLE;
    //        if (mStatusBarScrim != null && mStatusBarScrim.isVisible() != visible) {
    //            mStatusBarScrim.setVisible(visible, false);
    //        }
    //        if (mContentScrim != null && mContentScrim.isVisible() != visible) {
    //            mContentScrim.setVisible(visible, false);
    //        }
    //    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is LayoutParams
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun generateLayoutParams(attrs: AttributeSet): FrameLayout.LayoutParams {
        return LayoutParams(
            context,
            attrs
        )
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): FrameLayout.LayoutParams {
        return LayoutParams(p)
    }

    fun getMaxOffsetForPinChild(child: View): Int {
        val offsetHelper =
            getViewOffsetHelper(child)
        val lp =
            child.layoutParams as? LayoutParams
        return (width
                - offsetHelper.getLayoutLeft()
                - child.width
                - (lp?.rightMargin ?: 0))
    }

    class LayoutParams : FrameLayout.LayoutParams {
        /**
         * Returns the requested collapse mode.
         *
         * @return the current mode. One of [.COLLAPSE_MODE_OFF], [.COLLAPSE_MODE_PIN]
         * or [.COLLAPSE_MODE_PARALLAX].
         */
        /**
         * Set the collapse mode.
         *
         * @param collapseMode one of [.COLLAPSE_MODE_OFF], [.COLLAPSE_MODE_PIN]
         * or [.COLLAPSE_MODE_PARALLAX].
         */
        @get:CollapseMode
        var collapseMode =
            COLLAPSE_MODE_OFF
        /**
         * Returns the parallax scroll multiplier used in conjunction with
         * [.COLLAPSE_MODE_PARALLAX].
         *
         * @see .setParallaxMultiplier
         */
        /**
         * Set the parallax scroll multiplier used in conjunction with
         * [.COLLAPSE_MODE_PARALLAX]. A value of `0.0` indicates no movement at all,
         * `1.0f` indicates normal scroll movement.
         *
         * @see .getParallaxMultiplier
         */
        var parallaxMultiplier =
            DEFAULT_PARALLAX_MULTIPLIER

        constructor(c: Context, attrs: AttributeSet?) : super(
            c,
            attrs
        ) {
            val a = c.obtainStyledAttributes(
                attrs,
                R.styleable.CollapsingToolbarLayout_Layout
            )
            collapseMode = a.getInt(
                R.styleable.CollapsingToolbarLayout_Layout_layout_collapseMode,
                COLLAPSE_MODE_OFF
            )
            parallaxMultiplier = a.getFloat(
                R.styleable.CollapsingToolbarLayout_Layout_layout_collapseParallaxMultiplier,
                DEFAULT_PARALLAX_MULTIPLIER
            )
            a.recycle()
        }

        constructor(width: Int, height: Int) : super(width, height)
        constructor(width: Int, height: Int, gravity: Int) : super(width, height, gravity)
        constructor(p: ViewGroup.LayoutParams) : super(p)
        constructor(source: MarginLayoutParams) : super(source)

        // The copy constructor called here only exists on API 19+.
        @RequiresApi(19)
        constructor(source: FrameLayout.LayoutParams) : super(source)

        @IntDef(
            COLLAPSE_MODE_OFF,
            COLLAPSE_MODE_PIN,
            COLLAPSE_MODE_PARALLAX
        )
        @Retention(AnnotationRetention.SOURCE)
        internal annotation class CollapseMode
        companion object {
            /**
             * The view will act as normal with no collapsing behavior.
             */
            const val COLLAPSE_MODE_OFF = 0

            /**
             * The view will pin in place until it reaches the bottom of the
             * [HorizontalCollapsingToolbarLayout].
             */
            const val COLLAPSE_MODE_PIN = 1

            /**
             * The view will scroll in a parallax fashion. See [.setParallaxMultiplier]
             * to change the multiplier used.
             */
            const val COLLAPSE_MODE_PARALLAX = 2
            private const val DEFAULT_PARALLAX_MULTIPLIER = 0.5f
        }
    }

    private inner class OffsetUpdateListener internal constructor() :
        HorizontalAppBarLayout.OnOffsetChangedListener {

        override fun onOffsetChanged(
            layout: HorizontalAppBarLayout,
            verticalOffset: Int
        ) {
            mCurrentOffset = verticalOffset
            val insetLeft = mLastInsets?.systemWindowInsetLeft ?: 0
            var i = 0
            val z = childCount
            while (i < z) {
                val child = getChildAt(i)
                val lp = child.layoutParams as? LayoutParams ?: continue
                val offsetHelper =
                    getViewOffsetHelper(child)
                when (lp.collapseMode) {
                    LayoutParams.COLLAPSE_MODE_PIN -> offsetHelper.setLeftAndRightOffset(
                        MathUtils.clamp(
                            -verticalOffset,
                            0,
                            getMaxOffsetForPinChild(child)
                        )
                    )
                    LayoutParams.COLLAPSE_MODE_PARALLAX -> offsetHelper.setLeftAndRightOffset(
                        Math.round(-verticalOffset * lp.parallaxMultiplier)
                    )
                }
                i++
            }
        }
    }

    companion object {
        private const val DEFAULT_SCRIM_ANIMATION_DURATION = 600
        private fun getWidthWithMargins(view: View): Int {
            val lp = view.layoutParams
            if (lp is MarginLayoutParams) {
                val mlp = lp
                return view.width + mlp.leftMargin + mlp.rightMargin
            }
            return view.width
        }

        fun getViewOffsetHelper(view: View): ViewOffsetHelper {
            var offsetHelper =
                view.getTag(R.id.view_offset_helper) as? ViewOffsetHelper
            if (offsetHelper == null) {
                offsetHelper = ViewOffsetHelper(view)
                view.setTag(R.id.view_offset_helper, offsetHelper)
            }
            return offsetHelper
        }
    }

    init {
        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.CollapsingToolbarLayout, defStyleAttr,
            R.style.Widget_Design_CollapsingToolbar
        )
        mCollapsingTitleEnabled = a.getBoolean(
            R.styleable.CollapsingToolbarLayout_titleEnabled, true
        )
        mScrimVisibleHeightTrigger = a.getDimensionPixelSize(
            R.styleable.CollapsingToolbarLayout_scrimVisibleHeightTrigger, -1
        )
        scrimAnimationDuration = a.getInt(
            R.styleable.CollapsingToolbarLayout_scrimAnimationDuration,
            DEFAULT_SCRIM_ANIMATION_DURATION
        ).toLong()
        mToolbarId = a.getResourceId(R.styleable.CollapsingToolbarLayout_toolbarId, -1)
        a.recycle()
        setWillNotDraw(false)
        ViewCompat.setOnApplyWindowInsetsListener(
            this
        ) { _, insets -> onWindowInsetChanged(insets) }
    }
}