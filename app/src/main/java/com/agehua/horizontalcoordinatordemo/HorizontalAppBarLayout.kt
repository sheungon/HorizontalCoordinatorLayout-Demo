package com.agehua.horizontalcoordinatordemo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.ClassLoaderCreator
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.LinearLayout
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.core.math.MathUtils
import androidx.core.util.ObjectsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.customview.view.AbsSavedState
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.abs

@HorizontalCoordinatorLayout.DefaultBehavior(HorizontalAppBarLayout.Behavior::class)
class HorizontalAppBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private var mTotalScrollRange = INVALID_SCROLL_RANGE
    private var mDownPreScrollRange = INVALID_SCROLL_RANGE
    private var mDownScrollRange = INVALID_SCROLL_RANGE
    private var mHaveChildWithInterpolator = false
    var pendingAction = PENDING_ACTION_NONE
        private set
    private var mLastInsets: WindowInsetsCompat? = null
    private var mListeners: MutableList<OnOffsetChangedListener>? =
        null
    private var mCollapsible = false
    private var mCollapsed = false
    private var mTmpStatesArray: IntArray? = null

    /**
     * Add a listener that will be called when the offset of this [HorizontalAppBarLayout] changes.
     *
     * @param listener The listener that will be called when the offset changes.]
     * @see .removeOnOffsetChangedListener
     */
    fun addOnOffsetChangedListener(listener: OnOffsetChangedListener?) {
        var listeners = mListeners
        if (listeners == null) {
            listeners = ArrayList<OnOffsetChangedListener>().also { mListeners = it }
        }
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * Remove the previously added [HorizontalAppBarLayout.OnOffsetChangedListener].
     *
     * @param listener the listener to remove.
     */
    fun removeOnOffsetChangedListener(listener: OnOffsetChangedListener?) {
        if (listener != null) {
            mListeners?.remove(listener)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        invalidateScrollRanges()
    }

    override fun onLayout(
        changed: Boolean,
        l: Int,
        t: Int,
        r: Int,
        b: Int
    ) {
        super.onLayout(changed, l, t, r, b)
        invalidateScrollRanges()
        mHaveChildWithInterpolator = false
        var i = 0
        val z = childCount
        while (i < z) {
            val child = getChildAt(i)
            val childLp =
                child.layoutParams as LayoutParams
            val interpolator = childLp.scrollInterpolator
            if (interpolator != null) {
                mHaveChildWithInterpolator = true
                break
            }
            i++
        }
        updateCollapsible()

//        for (int i = 0, z = getChildCount(); i < z; i++) {
//            getViewOffsetHelper(getChildAt(i)).onViewLayout();
//        }
    }

    private fun updateCollapsible() {
        var haveCollapsibleChild = false
        var i = 0
        val z = childCount
        while (i < z) {
            if ((getChildAt(i).layoutParams as LayoutParams).isCollapsible) {
                haveCollapsibleChild = true
                break
            }
            i++
        }
        setCollapsibleState(haveCollapsibleChild)
    }

    private fun invalidateScrollRanges() {
        // Invalidate the scroll ranges
        mTotalScrollRange = INVALID_SCROLL_RANGE
        mDownPreScrollRange = INVALID_SCROLL_RANGE
        mDownScrollRange = INVALID_SCROLL_RANGE
    }

    override fun setOrientation(orientation: Int) {
        require(orientation == HORIZONTAL) {
            ("AppBarLayout is always vertical and does"
                    + " not support horizontal orientation")
        }
        super.setOrientation(orientation)
    }

    /**
     * Sets whether this [HorizontalAppBarLayout] is expanded or not, animating if it has already
     * been laid out.
     *
     *
     * As with [HorizontalAppBarLayout]'s scrolling, this method relies on this layout being a
     * direct child of a [HorizontalCoordinatorLayout].
     *
     * @param expanded true if the layout should be fully expanded, false if it should
     * be fully collapsed
     * @attr ref android.support.design.R.styleable#AppBarLayout_expanded
     */
    fun setExpanded(expanded: Boolean) {
        setExpanded(expanded, ViewCompat.isLaidOut(this))
    }

    /**
     * Sets whether this [HorizontalAppBarLayout] is expanded or not.
     *
     *
     * As with [HorizontalAppBarLayout]'s scrolling, this method relies on this layout being a
     * direct child of a [HorizontalCoordinatorLayout].
     *
     * @param expanded true if the layout should be fully expanded, false if it should
     * be fully collapsed
     * @param animate  Whether to animate to the new state
     * @attr ref android.support.design.R.styleable#AppBarLayout_expanded
     */
    fun setExpanded(expanded: Boolean, animate: Boolean) {
        setExpanded(expanded, animate, true)
    }

    private fun setExpanded(
        expanded: Boolean,
        animate: Boolean,
        force: Boolean
    ) {
        pendingAction =
            ((if (expanded) PENDING_ACTION_EXPANDED else PENDING_ACTION_COLLAPSED)
                    or (if (animate) PENDING_ACTION_ANIMATE_ENABLED else 0)
                    or if (force) PENDING_ACTION_FORCE else 0)
        requestLayout()
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is LayoutParams
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams {
        return LayoutParams(
            context,
            attrs
        )
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): LayoutParams {
        if (Build.VERSION.SDK_INT >= 19 && p is LinearLayout.LayoutParams) {
            return LayoutParams(p)
        } else if (p is MarginLayoutParams) {
            return LayoutParams(p)
        }
        return LayoutParams(p)
    }

    fun hasChildWithInterpolator(): Boolean {
        return mHaveChildWithInterpolator
    }// As soon as a view doesn't have the scroll flag, we end the range calculation.
    // This is because views below can not scroll under a fixed view.
// For a collapsing scroll, we to take the collapsed height into account.
    // We also break straight away since later views can't scroll beneath
    // us
// We're set to scroll so add the child's height

    /**
     * Returns the scroll range of all children.
     *
     * @return the scroll range in px
     */
    val totalScrollRange: Int
        get() {
            if (mTotalScrollRange != INVALID_SCROLL_RANGE) {
                return mTotalScrollRange
            }
            var range = 0
            var i = 0
            val z = childCount
            while (i < z) {
                val child = getChildAt(i)
                val lp =
                    child.layoutParams as LayoutParams
                val childWidth = child.measuredWidth
                val flags = lp.scrollFlags
                if (flags and LayoutParams.SCROLL_FLAG_SCROLL != 0) {
                    // We're set to scroll so add the child's height
                    range += childWidth + lp.leftMargin + lp.rightMargin
                    if (flags and LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED != 0) {
                        // For a collapsing scroll, we to take the collapsed height into account.
                        // We also break straight away since later views can't scroll beneath
                        // us
                        range -= ViewCompat.getMinimumWidth(child)
                        break
                    }
                } else {
                    // As soon as a view doesn't have the scroll flag, we end the range calculation.
                    // This is because views below can not scroll under a fixed view.
                    break
                }
                i++
            }
            return Math.max(0, range - leftInset).also { mTotalScrollRange = it }
        }

    fun hasScrollableChildren(): Boolean {
        return totalScrollRange != 0
    }

    /**
     * Return the scroll range when scrolling up from a nested pre-scroll.
     */
    val upNestedPreScrollRange: Int
        get() = totalScrollRange// If we've hit an non-quick return scrollable view, and we've already hit a
    // quick return view, return now
// Else use the full Width (minus the top inset)// Only enter by the amount of the collapsed height// If they're set to enter collapsed, use the minimum height// First take the margin into account
    // The view has the quick return flag combination...
// If we already have a valid value, return it

    /**
     * Return the scroll range when scrolling down from a nested pre-scroll.
     */
    val downNestedPreScrollRange: Int
        get() {
            if (mDownPreScrollRange != INVALID_SCROLL_RANGE) {
                // If we already have a valid value, return it
                return mDownPreScrollRange
            }
            var range = 0
            for (i in childCount - 1 downTo 0) {
                val child = getChildAt(i)
                val lp =
                    child.layoutParams as LayoutParams
                val childWidth = child.measuredWidth
                val flags = lp.scrollFlags
                if (flags and LayoutParams.FLAG_QUICK_RETURN == LayoutParams.FLAG_QUICK_RETURN) {
                    // First take the margin into account
                    range += lp.leftMargin + lp.rightMargin
                    // The view has the quick return flag combination...
                    range += if (flags and LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED != 0) {
                        // If they're set to enter collapsed, use the minimum height
                        ViewCompat.getMinimumWidth(child)
                    } else if (flags and LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED != 0) {
                        // Only enter by the amount of the collapsed height
                        childWidth - ViewCompat.getMinimumWidth(child)
                    } else {
                        // Else use the full Width (minus the top inset)
                        childWidth - leftInset
                    }
                } else if (range > 0) {
                    // If we've hit an non-quick return scrollable view, and we've already hit a
                    // quick return view, return now
                    break
                }
            }
            return Math.max(0, range).also { mDownPreScrollRange = it }
        }// As soon as a view doesn't have the scroll flag, we end the range calculation.
    // This is because views below can not scroll under a fixed view.
// For a collapsing exit scroll, we to take the collapsed Width into account.
    // We also break the range straight away since later views can't scroll
    // beneath us
// We're set to scroll so add the child's Width// If we already have a valid value, return it

    /**
     * Return the scroll range when scrolling down from a nested scroll.
     */
    val downNestedScrollRange: Int
        get() {
            if (mDownScrollRange != INVALID_SCROLL_RANGE) {
                // If we already have a valid value, return it
                return mDownScrollRange
            }
            var range = 0
            var i = 0
            val z = childCount
            while (i < z) {
                val child = getChildAt(i)
                val lp =
                    child.layoutParams as LayoutParams
                var childWidth = child.measuredWidth
                childWidth += lp.leftMargin + lp.rightMargin
                val flags = lp.scrollFlags
                if (flags and LayoutParams.SCROLL_FLAG_SCROLL != 0) {
                    // We're set to scroll so add the child's Width
                    range += childWidth
                    if (flags and LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED != 0) {
                        // For a collapsing exit scroll, we to take the collapsed Width into account.
                        // We also break the range straight away since later views can't scroll
                        // beneath us
                        range -= ViewCompat.getMinimumWidth(child) + leftInset
                        break
                    }
                } else {
                    // As soon as a view doesn't have the scroll flag, we end the range calculation.
                    // This is because views below can not scroll under a fixed view.
                    break
                }
                i++
            }
            return Math.max(0, range).also { mDownScrollRange = it }
        }

    fun dispatchOffsetUpdates(offset: Int) {
        // Iterate backwards through the list so that most recently added listeners
        // get the first chance to decide
//        if (null == mChildOffsetListener) {
//            mChildOffsetListener = new OffsetUpdateListener();
//            addOnOffsetChangedListener(mChildOffsetListener);
//        }
        mListeners?.forEach {
            it.onOffsetChanged(this, offset)
        }
    }

    // If this layout has a min Width, use it (doubled)

    // Otherwise, we'll use twice the min Width of our last child
    val minimumWidthForVisibleOverlappingContent: Int
        // If we reach here then we don't have a min height explicitly set. Instead we'll take a
        // guess at 1/3 of our Width being visible
        get() {
            val topInset = leftInset
            val minWidth = ViewCompat.getMinimumWidth(this)
            if (minWidth != 0) {
                // If this layout has a min Width, use it (doubled)
                return minWidth * 2 + topInset
            }

            // Otherwise, we'll use twice the min Width of our last child
            val childCount = childCount
            val lastChildMinWidth =
                if (childCount >= 1) ViewCompat.getMinimumWidth(getChildAt(childCount - 1)) else 0
            return if (lastChildMinWidth != 0) {
                lastChildMinWidth * 2 + topInset
            } else width / 3

            // If we reach here then we don't have a min height explicitly set. Instead we'll take a
            // guess at 1/3 of our Width being visible
        }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        // Note that we can't allocate this at the class level (in declaration) since
        // some paths in super View constructor are going to call this method before
        // that
        val extraStates = mTmpStatesArray
            ?: IntArray(2).also { mTmpStatesArray = it }
        val states = super.onCreateDrawableState(extraSpace + extraStates.size)
        extraStates[0] =
            if (mCollapsible) R.attr.state_collapsible else -R.attr.state_collapsible
        extraStates[1] =
            if (mCollapsible && mCollapsed) R.attr.state_collapsed else -R.attr.state_collapsed
        return View.mergeDrawableStates(states, extraStates)
    }

    /**
     * Sets whether the AppBarLayout has collapsible children or not.
     *
     * @return true if the collapsible state changed
     */
    private fun setCollapsibleState(collapsible: Boolean): Boolean {
        if (mCollapsible != collapsible) {
            mCollapsible = collapsible
            refreshDrawableState()
            return true
        }
        return false
    }

    /**
     * Sets whether the AppBarLayout is in a collapsed state or not.
     *
     * @return true if the collapsed state changed
     */
    fun setCollapsedState(collapsed: Boolean): Boolean {
        if (mCollapsed != collapsed) {
            mCollapsed = collapsed
            refreshDrawableState()
            return true
        }
        return false
    }

    fun resetPendingAction() {
        pendingAction = PENDING_ACTION_NONE
    }

    val leftInset: Int
        get() = mLastInsets?.systemWindowInsetLeft ?: 0

    fun onWindowInsetChanged(insets: WindowInsetsCompat?): WindowInsetsCompat? {
        var newInsets: WindowInsetsCompat? = null
        if (ViewCompat.getFitsSystemWindows(this)) {
            // If we're set to fit system windows, keep the insets
            newInsets = insets
        }

        // If our insets have changed, keep them and invalidate the scroll ranges...
        if (!ObjectsCompat.equals(mLastInsets, newInsets)) {
            mLastInsets = newInsets
            invalidateScrollRanges()
        }
        return insets
    }

    interface OnOffsetChangedListener {
        fun onOffsetChanged(
            appBarLayout: HorizontalAppBarLayout?,
            verticalOffset: Int
        )
    }

    class LayoutParams : LinearLayout.LayoutParams {
        ///////////////// Added collapseMode support /////////////////
        @get:CollapseMode
        var collapseMode =
            COLLAPSE_MODE_OFF
        var parallaxMultiplier = 0.5f
        /**
         * Returns the scrolling flags.
         *
         * @attr ref android.support.design.R.styleable#AppBarLayout_Layout_layout_scrollFlags
         * @see .setScrollFlags
         */
        /**
         * Set the scrolling flags.
         *
         * @param flags bitwise int of [.SCROLL_FLAG_SCROLL],
         * [.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED], [.SCROLL_FLAG_ENTER_ALWAYS],
         * [.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED] and [.SCROLL_FLAG_SNAP].
         * @attr ref android.support.design.R.styleable#AppBarLayout_Layout_layout_scrollFlags
         * @see .getScrollFlags
         */
        @get:ScrollFlags
        var scrollFlags =
            SCROLL_FLAG_SCROLL
        /**
         * Returns the [Interpolator] being used for scrolling the view associated with this
         * [HorizontalAppBarLayout.LayoutParams]. Null indicates 'normal' 1-to-1 scrolling.
         *
         * @attr ref android.support.design.R.styleable#AppBarLayout_Layout_layout_scrollInterpolator
         * @see .setScrollInterpolator
         */
        /**
         * Set the interpolator to when scrolling the view associated with this
         * [HorizontalAppBarLayout.LayoutParams].
         *
         * @param interpolator the interpolator to use, or null to use normal 1-to-1 scrolling.
         * @attr ref android.support.design.R.styleable#AppBarLayout_Layout_layout_scrollInterpolator
         * @see .getScrollInterpolator
         */
        var scrollInterpolator: Interpolator? = null

        constructor(c: Context, attrs: AttributeSet?) : super(
            c,
            attrs
        ) {
            val a = c.obtainStyledAttributes(attrs, R.styleable.AppBarLayout_Layout)
            scrollFlags = a.getInt(R.styleable.AppBarLayout_Layout_layout_scrollFlags, 0)
            if (a.hasValue(R.styleable.AppBarLayout_Layout_layout_scrollInterpolator)) {
                val resId = a.getResourceId(
                    R.styleable.AppBarLayout_Layout_layout_scrollInterpolator, 0
                )
                scrollInterpolator = AnimationUtils.loadInterpolator(
                    c, resId
                )
            }
            val b = c.obtainStyledAttributes(
                attrs,
                R.styleable.CollapsingToolbarLayout_Layout
            )
            collapseMode = b.getInt(
                R.styleable.CollapsingToolbarLayout_Layout_layout_collapseMode,
                COLLAPSE_MODE_OFF
            )
            parallaxMultiplier = b.getFloat(
                R.styleable.CollapsingToolbarLayout_Layout_layout_collapseParallaxMultiplier,
                0.5f
            )
            a.recycle()
            b.recycle()
        }

        constructor(width: Int, height: Int) : super(width, height) {}
        constructor(width: Int, height: Int, weight: Float) : super(width, height, weight) {}
        constructor(p: ViewGroup.LayoutParams?) : super(p) {}
        constructor(source: MarginLayoutParams?) : super(source) {}

        @RequiresApi(19)
        constructor(source: LinearLayout.LayoutParams?) : super(source) {
            // The copy constructor called here only exists on API 19+.
        }

        @RequiresApi(19)
        constructor(source: LayoutParams) : super(
            source
        ) {
            // The copy constructor called here only exists on API 19+.
            scrollFlags = source.scrollFlags
            scrollInterpolator = source.scrollInterpolator
        }

        /**
         * Returns true if the scroll flags are compatible for 'collapsing'
         */
        val isCollapsible: Boolean
            get() = (scrollFlags and SCROLL_FLAG_SCROLL == SCROLL_FLAG_SCROLL
                    && scrollFlags and COLLAPSIBLE_FLAGS != 0)

        /**
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @IntDef(
            flag = true,
            value = [SCROLL_FLAG_SCROLL, SCROLL_FLAG_EXIT_UNTIL_COLLAPSED, SCROLL_FLAG_ENTER_ALWAYS, SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED, SCROLL_FLAG_SNAP]
        )
        @Retention(RetentionPolicy.SOURCE)
        annotation class ScrollFlags

        @IntDef(
            COLLAPSE_MODE_OFF,
            COLLAPSE_MODE_PIN,
            COLLAPSE_MODE_PARALLAX
        )
        @Retention(RetentionPolicy.SOURCE)
        internal annotation class CollapseMode
        companion object {
            const val COLLAPSE_MODE_OFF = 0
            const val COLLAPSE_MODE_PIN = 1
            const val COLLAPSE_MODE_PARALLAX = 2

            /**
             * The view will be scroll in direct relation to scroll events. This flag needs to be
             * set for any of the other flags to take effect. If any sibling views
             * before this one do not have this flag, then this value has no effect.
             */
            const val SCROLL_FLAG_SCROLL = 0x1

            /**
             * When exiting (scrolling off screen) the view will be scrolled until it is
             * 'collapsed'. The collapsed height is defined by the view's minimum height.
             *
             * @see ViewCompat.getMinimumHeight
             * @see View.setMinimumHeight
             */
            const val SCROLL_FLAG_EXIT_UNTIL_COLLAPSED = 0x2

            /**
             * When entering (scrolling on screen) the view will scroll on any downwards
             * scroll event, regardless of whether the scrolling view is also scrolling. This
             * is commonly referred to as the 'quick return' pattern.
             */
            const val SCROLL_FLAG_ENTER_ALWAYS = 0x4

            /**
             * An additional flag for 'enterAlways' which modifies the returning view to
             * only initially scroll back to it's collapsed height. Once the scrolling view has
             * reached the end of it's scroll range, the remainder of this view will be scrolled
             * into view. The collapsed height is defined by the view's minimum height.
             *
             * @see ViewCompat.getMinimumHeight
             * @see View.setMinimumHeight
             */
            const val SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED = 0x8

            /**
             * Upon a scroll ending, if the view is only partially visible then it will be snapped
             * and scrolled to it's closest edge. For example, if the view only has it's bottom 25%
             * displayed, it will be scrolled off screen completely. Conversely, if it's bottom 75%
             * is visible then it will be scrolled fully into view.
             */
            const val SCROLL_FLAG_SNAP = 0x10

            /**
             * Internal flags which allows quick checking features
             */
            const val FLAG_QUICK_RETURN =
                SCROLL_FLAG_SCROLL or SCROLL_FLAG_ENTER_ALWAYS
            const val FLAG_SNAP =
                SCROLL_FLAG_SCROLL or SCROLL_FLAG_SNAP
            const val COLLAPSIBLE_FLAGS =
                (SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
                        or SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED)
        }
    }

    /**
     * The default [HorizontalAppBarLayout.Behavior] for [HorizontalAppBarLayout]. Implements the necessary nested
     * scroll handling with offsetting.
     */
    class Behavior : HorizontalHeaderBehavior<HorizontalAppBarLayout> {
        var mOffsetDelta = 0
        private var mOffsetAnimator: ValueAnimator? = null
        private var mOffsetToChildIndexOnLayout =
            INVALID_POSITION
        private var mOffsetToChildIndexOnLayoutIsMinWidth = false
        private var mOffsetToChildIndexOnLayoutPerc = 0f
        private var mLastNestedScrollingChildRef: WeakReference<View>? =
            null
        private var mOnDragCallback: DragCallback? =
            null

        constructor() {}
        constructor(context: Context?, attrs: AttributeSet?) : super(
            context,
            attrs
        ) {
        }

        /**
         * NestedScrollView as child of View, startNestedScroll(...) will be invoked on scrolling,
         * that View parent, CoordinatorLayout, to check if the event has been consumed.
         * CoordinatorLayout as proxy callback to Behavior, here is AppBarLayout.Behavior
         *
         * @return true means CoordinatorLayout need to consume the event and should invoke
         * AppBarLayout.Behavior.onNestedPreScroll()
         */
        override fun onStartNestedScroll(
            parent: HorizontalCoordinatorLayout,
            child: HorizontalAppBarLayout,
            directTargetChild: View,
            target: View,
            nestedScrollAxes: Int,
            type: Int
        ): Boolean {
            // Return true if we're nested scrolling vertically, and we have scrollable children
            // and the scrolling view is big enough to scroll
            val started =
                (nestedScrollAxes and ViewCompat.SCROLL_AXIS_HORIZONTAL != 0 && child.hasScrollableChildren()
                        && parent.width - directTargetChild.width <= child.width)
            if (started) {
                // Cancel any offset animation
                mOffsetAnimator?.cancel()
            }

            // A new nested scroll has started so clear out the previous ref
            mLastNestedScrollingChildRef = null
            return started
        }

        override fun onNestedPreScroll(
            horizontalCoordinatorLayout: HorizontalCoordinatorLayout,
            child: HorizontalAppBarLayout,
            target: View,
            dx: Int,
            dy: Int,
            consumed: IntArray, type: Int
        ) {
            if (dx != 0) {
                val min: Int
                val max: Int
                if (dx < 0) {
                    // We're scrolling down
                    min = -child.totalScrollRange
                    max = min + child.downNestedPreScrollRange
                } else {
                    // We're scrolling up
                    min = -child.upNestedPreScrollRange
                    max = 0
                }
                if (min != max) {
                    consumed[0] = scroll(horizontalCoordinatorLayout, child, dx, min, max)
                }
            }
        }

        override fun onNestedScroll(
            horizontalCoordinatorLayout: HorizontalCoordinatorLayout,
            child: HorizontalAppBarLayout,
            target: View,
            dxConsumed: Int,
            dyConsumed: Int,
            dxUnconsumed: Int,
            dyUnconsumed: Int,
            type: Int
        ) {
            if (dxUnconsumed < 0) {
                // If the scrolling view is scrolling down but not consuming, it's probably be at
                // the top of it's content
                scroll(
                    horizontalCoordinatorLayout, child, dxUnconsumed,
                    -child.downNestedScrollRange, 0
                )
            }
        }

        override fun onStopNestedScroll(
            horizontalCoordinatorLayout: HorizontalCoordinatorLayout,
            abl: HorizontalAppBarLayout,
            target: View,
            type: Int
        ) {
            if (type == ViewCompat.TYPE_TOUCH) {
                // If we haven't been flung then let's see if the current view has been set to snap
                snapToChildIfNeeded(horizontalCoordinatorLayout, abl)
            }

            // Keep a reference to the previous nested scrolling child
            mLastNestedScrollingChildRef = WeakReference(target)
        }

        /**
         * Set a callback to control any [HorizontalAppBarLayout] dragging.
         *
         * @param callback the callback to use, or `null` to use the default behavior.
         */
        fun setDragCallback(callback: DragCallback?) {
            mOnDragCallback = callback
        }

        private fun animateOffsetTo(
            horizontalCoordinatorLayout: HorizontalCoordinatorLayout,
            child: HorizontalAppBarLayout,
            offset: Int,
            velocity: Float
        ) {
            var velocity = velocity
            val distance = abs(getLeftRightOffsetForScrollingSibling() - offset)
            val duration: Int
            velocity = Math.abs(velocity)
            duration = if (velocity > 0) {
                3 * Math.round(1000 * (distance / velocity))
            } else {
                val distanceRatio = distance.toFloat() / child.width
                ((distanceRatio + 1) * 150).toInt()
            }
            animateOffsetWithDuration(horizontalCoordinatorLayout, child, offset, duration)
        }

        private fun animateOffsetWithDuration(
            horizontalCoordinatorLayout: HorizontalCoordinatorLayout,
            child: HorizontalAppBarLayout, offset: Int, duration: Int
        ) {
            val currentOffset = getLeftRightOffsetForScrollingSibling()
            if (currentOffset == offset) {
                if (mOffsetAnimator?.isRunning == true) {
                    mOffsetAnimator?.cancel()
                }
                return
            }
            var offsetAnimator = mOffsetAnimator
            if (offsetAnimator == null) {
                offsetAnimator = ValueAnimator().also { mOffsetAnimator = it }
                offsetAnimator.interpolator = DecelerateInterpolator()
                offsetAnimator.addUpdateListener { animation ->
                    setHeaderLeftRightOffset(
                        horizontalCoordinatorLayout, child,
                        animation.animatedValue as Int
                    )
                }
            } else {
                offsetAnimator.cancel()
            }
            offsetAnimator.duration = Math.min(
                duration,
                MAX_OFFSET_ANIMATION_DURATION
            ).toLong()
            offsetAnimator.setIntValues(currentOffset, offset)
            offsetAnimator.start()
        }

        private fun getChildIndexOnOffset(abl: HorizontalAppBarLayout, offset: Int): Int {
            var i = 0
            val count = abl.childCount
            while (i < count) {
                val child = abl.getChildAt(i)
                if (child.top <= -offset && child.bottom >= -offset) {
                    return i
                }
                i++
            }
            return -1
        }

        private fun snapToChildIfNeeded(
            horizontalCoordinatorLayout: HorizontalCoordinatorLayout,
            abl: HorizontalAppBarLayout
        ) {
            val offset = getLeftRightOffsetForScrollingSibling()
            val offsetChildIndex = getChildIndexOnOffset(abl, offset)
            if (offsetChildIndex >= 0) {
                val offsetChild = abl.getChildAt(offsetChildIndex)
                val lp =
                    offsetChild.layoutParams as LayoutParams
                val flags = lp.scrollFlags
                if (flags and LayoutParams.FLAG_SNAP == LayoutParams.FLAG_SNAP) {
                    // We're set the snap, so animate the offset to the nearest edge
                    var snapTop = -offsetChild.top
                    var snapBottom = -offsetChild.bottom
                    if (offsetChildIndex == abl.childCount - 1) {
                        // If this is the last child, we need to take the top inset into account
                        snapBottom += abl.leftInset
                    }
                    if (checkFlag(
                            flags,
                            LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
                        )
                    ) {
                        // If the view is set only exit until it is collapsed, we'll abide by that
                        snapBottom += ViewCompat.getMinimumWidth(offsetChild)
                    } else if (checkFlag(
                            flags,
                            LayoutParams.FLAG_QUICK_RETURN
                                    or LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                        )
                    ) {
                        // If it's set to always enter collapsed, it actually has two states. We
                        // select the state and then snap within the state
                        val seam = snapBottom + ViewCompat.getMinimumWidth(offsetChild)
                        if (offset < seam) {
                            snapTop = seam
                        } else {
                            snapBottom = seam
                        }
                    }
                    val newOffset =
                        if (offset < (snapBottom + snapTop) / 2) snapBottom else snapTop
                    animateOffsetTo(
                        horizontalCoordinatorLayout, abl,
                        MathUtils.clamp(
                            newOffset,
                            -abl.totalScrollRange,
                            0
                        ), 0f
                    )
                }
            }
        }

        override fun onMeasureChild(
            parent: HorizontalCoordinatorLayout,
            child: HorizontalAppBarLayout,
            parentWidthMeasureSpec: Int,
            widthUsed: Int,
            parentHeightMeasureSpec: Int,
            heightUsed: Int
        ): Boolean {
            val lp =
                child.layoutParams as HorizontalCoordinatorLayout.LayoutParams
            if (lp.width == HorizontalCoordinatorLayout.LayoutParams.WRAP_CONTENT) {
                // If the view is set to wrap on it's Width, CoordinatorLayout by default will
                // cap the view at the CoL's Width. Since the AppBarLayout can scroll, this isn't
                // what we actually want, so we measure it ourselves with an unspecified spec to
                // allow the child to be larger than it's parent
                parent.onMeasureChild(
                    child, parentWidthMeasureSpec, widthUsed,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightUsed
                )
                return true
            }

            // Let the parent handle it as normal
            return super.onMeasureChild(
                parent, child, parentWidthMeasureSpec, widthUsed,
                parentHeightMeasureSpec, heightUsed
            )
        }

        override fun onLayoutChild(
            parent: HorizontalCoordinatorLayout,
            abl: HorizontalAppBarLayout,
            layoutDirection: Int
        ): Boolean {
            val handled = super.onLayoutChild(parent, abl, layoutDirection)

            // The priority for for actions here is (first which is true wins):
            // 1. forced pending actions
            // 2. offsets for restorations
            // 3. non-forced pending actions
            val pendingAction = abl.pendingAction
            if (mOffsetToChildIndexOnLayout >= 0 && pendingAction and PENDING_ACTION_FORCE == 0) {
                val child = abl.getChildAt(mOffsetToChildIndexOnLayout)
                var offset = -child.bottom
                offset += if (mOffsetToChildIndexOnLayoutIsMinWidth) {
                    ViewCompat.getMinimumWidth(child) + abl.leftInset
                } else {
                    Math.round(child.width * mOffsetToChildIndexOnLayoutPerc)
                }
                setHeaderLeftRightOffset(parent, abl, offset)
            } else if (pendingAction != PENDING_ACTION_NONE) {
                val animate =
                    pendingAction and PENDING_ACTION_ANIMATE_ENABLED != 0
                if (pendingAction and PENDING_ACTION_COLLAPSED != 0) {
                    val offset = -abl.upNestedPreScrollRange
                    if (animate) {
                        animateOffsetTo(parent, abl, offset, 0f)
                    } else {
                        setHeaderLeftRightOffset(parent, abl, offset)
                    }
                } else if (pendingAction and PENDING_ACTION_EXPANDED != 0) {
                    if (animate) {
                        animateOffsetTo(parent, abl, 0, 0f)
                    } else {
                        setHeaderLeftRightOffset(parent, abl, 0)
                    }
                }
            }

            // Finally reset any pending states
            abl.resetPendingAction()
            mOffsetToChildIndexOnLayout =
                INVALID_POSITION

            // We may have changed size, so let's constrain the top and bottom offset correctly,
            // just in case we're out of the bounds
            setLeftAndRightOffset(
                MathUtils.clamp(
                    leftAndRightOffset,
                    -abl.totalScrollRange,
                    0
                )
            )

            // Update the AppBarLayout's drawable state for any elevation changes.
            // This is needed so that the elevation is set in the first layout, so that
            // we don't get a visual elevation jump pre-N (due to the draw dispatch skip)
            updateAppBarLayoutDrawableState(parent, abl, leftAndRightOffset, 0, true)

            // Make sure we dispatch the offset update
            abl.dispatchOffsetUpdates(leftAndRightOffset)
            return handled
        }

        override fun canDragView(view: HorizontalAppBarLayout): Boolean =
            // If there is a drag callback set, it's in control
            mOnDragCallback?.canDrag(view)
            // Else we'll use the default behaviour of seeing if it can scroll down
            // If we have a reference to a scrolling view, check it
                ?: mLastNestedScrollingChildRef?.get()?.let { scrollingView ->
                    scrollingView.isShown &&
                            !scrollingView.canScrollVertically(-1)
                }
                // Otherwise we assume that the scrolling view hasn't been scrolled and can drag.
                ?: true

        override fun onFlingFinished(
            parent: HorizontalCoordinatorLayout,
            layout: HorizontalAppBarLayout
        ) {
            // At the end of a manual fling, check to see if we need to snap to the edge-child
            snapToChildIfNeeded(parent, layout)
        }

        override fun getMaxDragOffset(view: HorizontalAppBarLayout): Int {
            return -view.downNestedScrollRange
        }

        override fun getScrollRangeForDragFling(view: HorizontalAppBarLayout): Int {
            return view.totalScrollRange
        }

        override fun setHeaderLeftRightOffset(
            horizontalCoordinatorLayout: HorizontalCoordinatorLayout,
            appBarLayout: HorizontalAppBarLayout,
            newOffset: Int,
            minOffset: Int,
            maxOffset: Int
        ): Int {
            var newOffset = newOffset
            val curOffset = getLeftRightOffsetForScrollingSibling()
            var consumed = 0
            // minOffset equals to AppBarLayout minus right，maxOffset equals to 0
            // If AppBarLayout scroll distance larger than minOffset or maxOffset, return 0
            if (minOffset != 0 && curOffset >= minOffset && curOffset <= maxOffset) {
                // If we have some scrolling range, and we're currently within the min and max
                // offsets, calculate a new offset //矫正newOffset，使其minOffset<=newOffset<=maxOffset
                newOffset = MathUtils.clamp(newOffset, minOffset, maxOffset)
                if (curOffset != newOffset) {
                    val interpolatedOffset =
                        if (appBarLayout.hasChildWithInterpolator()) interpolateOffset(
                            appBarLayout,
                            newOffset
                        ) else newOffset //由于默认没设置Interpolator，所以interpolatedOffset=newOffset;
                    // Invoke ViewOffsetBehvaior.setTopAndBottomOffset(...).
                    // ViewCompat.offsetTopAndBottom() moves AppBarLayout
                    val offsetChanged = setLeftAndRightOffset(interpolatedOffset)

                    // Update how much dy we have consumed
                    // Record consumed dy
                    consumed = curOffset - newOffset
                    // Update the stored sibling offset
                    // Set Interpolator, mOffsetDelta keeps = 0
                    mOffsetDelta = newOffset - interpolatedOffset
                    if (!offsetChanged && appBarLayout.hasChildWithInterpolator()) {
                        // If the offset hasn't changed and we're using an interpolated scroll
                        // then we need to keep any dependent views updated. CoL will do this for
                        // us when we move, but we need to do it manually when we don't (as an
                        // interpolated scroll may finish early).
                        horizontalCoordinatorLayout.dispatchDependentViewsChanged(appBarLayout)
                    }

                    // Dispatch the updates to any listeners
                    // Callback OnOffsetChangedListener.onOffsetChanged(...)
                    appBarLayout.dispatchOffsetUpdates(leftAndRightOffset)

                    // Update the AppBarLayout's drawable state (for any elevation changes)
                    updateAppBarLayoutDrawableState(
                        horizontalCoordinatorLayout, appBarLayout, newOffset,
                        if (newOffset < curOffset) -1 else 1, false
                    )
                }
            } else {
                // Reset the offset delta
                mOffsetDelta = 0
            }
            return consumed
        }

        val isOffsetAnimatorRunning: Boolean
            get() = mOffsetAnimator?.isRunning == true

        private fun interpolateOffset(
            layout: HorizontalAppBarLayout,
            offset: Int
        ): Int {
            val absOffset = Math.abs(offset)
            var i = 0
            val z = layout.childCount
            while (i < z) {
                val child = layout.getChildAt(i)
                val childLp =
                    child.layoutParams as LayoutParams
                val interpolator =
                    childLp.scrollInterpolator
                if (absOffset >= child.top && absOffset <= child.bottom) {
                    if (interpolator != null) {
                        var childScrollableWidth = 0
                        val flags = childLp.scrollFlags
                        if (flags and LayoutParams.SCROLL_FLAG_SCROLL != 0) {
                            // We're set to scroll so add the child's Width plus margin
                            childScrollableWidth += (child.width + childLp.topMargin
                                    + childLp.bottomMargin)
                            if (flags and LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED != 0) {
                                // For a collapsing scroll, we to take the collapsed Width
                                // into account.
                                childScrollableWidth -= ViewCompat.getMinimumWidth(child)
                            }
                        }
                        if (ViewCompat.getFitsSystemWindows(child)) {
                            childScrollableWidth -= layout.leftInset
                        }
                        if (childScrollableWidth > 0) {
                            val offsetForView = absOffset - child.top
                            val interpolatedDiff = Math.round(
                                childScrollableWidth *
                                        interpolator.getInterpolation(
                                            offsetForView / childScrollableWidth.toFloat()
                                        )
                            )
                            return Integer.signum(offset) * (child.top + interpolatedDiff)
                        }
                    }

                    // If we get to here then the view on the offset isn't suitable for interpolated
                    // scrolling. So break out of the loop
                    break
                }
                i++
            }
            return offset
        }

        private fun updateAppBarLayoutDrawableState(
            parent: HorizontalCoordinatorLayout,
            layout: HorizontalAppBarLayout,
            offset: Int,
            direction: Int,
            forceJump: Boolean
        ) {
            val child =
                getAppBarChildOnOffset(
                    layout,
                    offset
                )
            if (child != null) {
                val childLp =
                    child.layoutParams as LayoutParams
                val flags = childLp.scrollFlags
                var collapsed = false
                if (flags and LayoutParams.SCROLL_FLAG_SCROLL != 0) {
                    val minWidth = ViewCompat.getMinimumWidth(child)
                    if (direction > 0 && flags and (LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                                or LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED) != 0
                    ) {
                        // We're set to enter always collapsed so we are only collapsed when
                        // being scrolled down, and in a collapsed offset
                        collapsed = -offset >= child.bottom - minWidth - layout.leftInset
                    } else if (flags and LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED != 0) {
                        // We're set to exit until collapsed, so any offset which results in
                        // the minimum Width (or less) being shown is collapsed
                        collapsed = -offset >= child.bottom - minWidth - layout.leftInset
                    }
                }
                val changed = layout.setCollapsedState(collapsed)
                if (forceJump || changed && shouldJumpElevationState(parent, layout)) {
                    // If the collapsed state changed, we may need to
                    // jump to the current state if we have an overlapping view
                    layout.jumpDrawablesToCurrentState()
                }
            }
        }

        private fun shouldJumpElevationState(
            parent: HorizontalCoordinatorLayout,
            layout: HorizontalAppBarLayout
        ): Boolean {
            // We should jump the elevated state if we have a dependent scrolling view which has
            // an overlapping top (i.e. overlaps us)
            val dependencies =
                parent.getDependents(layout)
            var i = 0
            val size = dependencies.size
            while (i < size) {
                val dependency = dependencies[i]
                val lp =
                    dependency.layoutParams as HorizontalCoordinatorLayout.LayoutParams
                val behavior = lp.behavior
                if (behavior is ScrollingViewBehavior) {
                    return behavior.overlayLeft != 0
                }
                i++
            }
            return false
        }

        override fun getLeftRightOffsetForScrollingSibling(): Int {
            return leftAndRightOffset + mOffsetDelta
        }

        override fun onSaveInstanceState(
            parent: HorizontalCoordinatorLayout,
            abl: HorizontalAppBarLayout
        ): Parcelable {
            val superState = super.onSaveInstanceState(parent, abl)
            val offset = leftAndRightOffset

            // Try and find the first visible child...
            var i = 0
            val count = abl.childCount
            while (i < count) {
                val child = abl.getChildAt(i)
                val visBottom = child.bottom + offset
                if (child.top + offset <= 0 && visBottom >= 0) {
                    val ss =
                        SavedState(
                            superState
                        )
                    ss.firstVisibleChildIndex = i
                    ss.firstVisibleChildAtMinimumWidth =
                        visBottom == ViewCompat.getMinimumWidth(child) + abl.leftInset
                    ss.firstVisibleChildPercentageShown =
                        visBottom / child.width.toFloat()
                    return ss
                }
                i++
            }

            // Else we'll just return the super state
            return superState
        }

        override fun onRestoreInstanceState(
            parent: HorizontalCoordinatorLayout, appBarLayout: HorizontalAppBarLayout,
            state: Parcelable
        ) {
            if (state is SavedState) {
                val ss =
                    state
                super.onRestoreInstanceState(parent, appBarLayout, ss.superState)
                mOffsetToChildIndexOnLayout = ss.firstVisibleChildIndex
                mOffsetToChildIndexOnLayoutPerc = ss.firstVisibleChildPercentageShown
                mOffsetToChildIndexOnLayoutIsMinWidth = ss.firstVisibleChildAtMinimumWidth
            } else {
                super.onRestoreInstanceState(parent, appBarLayout, state)
                mOffsetToChildIndexOnLayout =
                    INVALID_POSITION
            }
        }

        /**
         * Callback to allow control over any [HorizontalAppBarLayout] dragging.
         */
        abstract class DragCallback {
            /**
             * Allows control over whether the given [HorizontalAppBarLayout] can be dragged or not.
             *
             *
             * Dragging is defined as a direct touch on the AppBarLayout with movement. This
             * call does not affect any nested scrolling.
             *
             * @return true if we are in a position to scroll the AppBarLayout via a drag, false
             * if not.
             */
            abstract fun canDrag(appBarLayout: HorizontalAppBarLayout): Boolean
        }

        protected class SavedState : AbsSavedState {
            var firstVisibleChildIndex = 0
            var firstVisibleChildPercentageShown = 0f
            var firstVisibleChildAtMinimumWidth = false

            constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
                firstVisibleChildIndex = source.readInt()
                firstVisibleChildPercentageShown = source.readFloat()
                firstVisibleChildAtMinimumWidth = source.readByte().toInt() != 0
            }

            constructor(superState: Parcelable) : super(superState)

            override fun writeToParcel(dest: Parcel, flags: Int) {
                super.writeToParcel(dest, flags)
                dest.writeInt(firstVisibleChildIndex)
                dest.writeFloat(firstVisibleChildPercentageShown)
                dest.writeByte((if (firstVisibleChildAtMinimumWidth) 1 else 0).toByte())
            }

            companion object {
                val CREATOR: Parcelable.Creator<SavedState> =
                    object :
                        ClassLoaderCreator<SavedState> {
                        override fun createFromParcel(
                            source: Parcel,
                            loader: ClassLoader
                        ): SavedState {
                            return SavedState(
                                source,
                                loader
                            )
                        }

                        override fun createFromParcel(source: Parcel): SavedState = SavedState(
                            source,
                            null
                        )

                        override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
                    }
            }
        }

        companion object {
            private const val MAX_OFFSET_ANIMATION_DURATION = 600 // ms
            private const val INVALID_POSITION = -1
            private fun checkFlag(flags: Int, check: Int): Boolean {
                return flags and check == check
            }

            private fun getAppBarChildOnOffset(
                layout: HorizontalAppBarLayout,
                offset: Int
            ): View? {
                val absOffset = Math.abs(offset)
                var i = 0
                val z = layout.childCount
                while (i < z) {
                    val child = layout.getChildAt(i)
                    if (absOffset >= child.top && absOffset <= child.bottom) {
                        return child
                    }
                    i++
                }
                return null
            }
        }
    }

    /**
     * Behavior which should be used by [View]s which can scroll vertically and support
     * nested scrolling to automatically scroll any [HorizontalAppBarLayout] siblings.
     */
    class ScrollingViewBehavior : HorizontalHeaderScrollingViewBehavior {
        constructor() {}
        constructor(
            context: Context,
            attrs: AttributeSet?
        ) : super(context, attrs) {
            val a = context.obtainStyledAttributes(
                attrs,
                R.styleable.ScrollingViewBehavior_Layout
            )
            overlayLeft = a.getDimensionPixelSize(
                R.styleable.ScrollingViewBehavior_Layout_behavior_overlapTop, 0
            )
            a.recycle()
        }

        override fun layoutDependsOn(
            parent: HorizontalCoordinatorLayout,
            child: View,
            dependency: View
        ): Boolean =
            // We depend on any AppBarLayouts
            dependency is HorizontalAppBarLayout

        override fun onDependentViewChanged(
            parent: HorizontalCoordinatorLayout,
            child: View,
            dependency: View
        ): Boolean {
            offsetChildAsNeeded(parent, child, dependency)
            return false
        }

        override fun onRequestChildRectangleOnScreen(
            parent: HorizontalCoordinatorLayout, child: View,
            rectangle: Rect, immediate: Boolean
        ): Boolean {
            val header = findFirstDependency(parent.getDependencies(child))
            if (header != null) {
                // Offset the rect by the child's left/top
                rectangle.offset(child.left, child.top)
                val parentRect = tempRect1
                parentRect[0, 0, parent.width] = parent.height
                if (!parentRect.contains(rectangle)) {
                    // If the rectangle can not be fully seen the visible bounds, collapse
                    // the AppBarLayout
                    header.setExpanded(false, !immediate)
                    return true
                }
            }
            return false
        }

        private fun offsetChildAsNeeded(
            parent: HorizontalCoordinatorLayout,
            child: View,
            dependency: View
        ) {
            val behavior =
                (dependency.layoutParams as HorizontalCoordinatorLayout.LayoutParams).behavior
            if (behavior is Behavior) {
                // Offset the child, pinning it to the bottom the header-dependency, maintaining
                // any vertical gap and overlap
                ViewCompat.offsetLeftAndRight(
                    child, (dependency.right - child.left
                            + behavior.mOffsetDelta
                            + horizontalLayoutGap)
                            - getOverlapPixelsForOffset(dependency)
                )
            }
        }

        override fun getOverlapRatioForOffset(header: View): Float {
            if (header is HorizontalAppBarLayout) {
                val abl = header
                val totalScrollRange = abl.totalScrollRange
                val preScrollDown = abl.downNestedPreScrollRange
                val offset = getAppBarLayoutOffset(abl)
                if (preScrollDown != 0 && totalScrollRange + offset <= preScrollDown) {
                    // If we're in a pre-scroll down. Don't use the offset at all.
                    return 0f
                } else {
                    val availScrollRange = totalScrollRange - preScrollDown
                    if (availScrollRange != 0) {
                        // Else we'll use a interpolated ratio of the overlap, depending on offset
                        return 1f + offset / availScrollRange.toFloat()
                    }
                }
            }
            return 0f
        }

        override fun findFirstDependency(views: List<View>): HorizontalAppBarLayout? {
            var i = 0
            val z = views.size
            while (i < z) {
                val view = views[i]
                if (view is HorizontalAppBarLayout) {
                    return view
                }
                i++
            }
            return null
        }

        override fun getScrollRange(view: View): Int {
            return if (view is HorizontalAppBarLayout) {
                view.totalScrollRange
            } else {
                super.getScrollRange(view)
            }
        }

        companion object {
            private fun getAppBarLayoutOffset(abl: HorizontalAppBarLayout): Int {
                val behavior =
                    (abl.layoutParams as HorizontalCoordinatorLayout.LayoutParams).behavior
                return if (behavior is Behavior) {
                    behavior.getLeftRightOffsetForScrollingSibling()
                } else 0
            }
        }
    } //

    //    static ViewOffsetHelper getViewOffsetHelper(View view) {
    //        ViewOffsetHelper offsetHelper = (ViewOffsetHelper) view.getTag(R.id.view_offset_helper);
    //        if (offsetHelper == null) {
    //            offsetHelper = new ViewOffsetHelper(view);
    //            view.setTag(R.id.view_offset_helper, offsetHelper);
    //        }
    //        return offsetHelper;
    //    }
    //    private class OffsetUpdateListener implements HorizontalAppBarLayout.OnOffsetChangedListener {
    //        OffsetUpdateListener() {
    //        }
    //
    //        @Override
    //        public void onOffsetChanged(HorizontalAppBarLayout layout, int verticalOffset) {
    //
    //            final int insetTop = mLastInsets != null ? mLastInsets.getSystemWindowInsetTop() : 0;
    //
    //            for (int i = 0, z = getChildCount(); i < z; i++) {
    //                final View child = getChildAt(i);
    //                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
    //                final ViewOffsetHelper offsetHelper = getViewOffsetHelper(child);
    //
    //                switch (lp.mCollapseMode) {
    //                    case LayoutParams.COLLAPSE_MODE_PIN:
    //                        offsetHelper.setLeftAndRightOffset(MathUtils.clamp(
    //                                -verticalOffset, 0, getMaxOffsetForPinChild(child)));
    //                        break;
    //                    case LayoutParams.COLLAPSE_MODE_PARALLAX:
    //                        offsetHelper.setLeftAndRightOffset((int) Math.round(-verticalOffset * 0.5));
    //                        break;
    //                }
    //            }
    //        }
    //    }
    //    final int getMaxOffsetForPinChild(View child) {
    //        final ViewOffsetHelper offsetHelper = getViewOffsetHelper(child);
    //        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
    //        return getWidth()
    //                - offsetHelper.getLayoutLeft()
    //                - child.getWidth()
    //                - lp.rightMargin;
    //    }
    companion object {
        const val PENDING_ACTION_NONE = 0x0
        const val PENDING_ACTION_EXPANDED = 0x1
        const val PENDING_ACTION_COLLAPSED = 0x2
        const val PENDING_ACTION_ANIMATE_ENABLED = 0x4
        const val PENDING_ACTION_FORCE = 0x8
        private const val INVALID_SCROLL_RANGE = -1
    }

    init {
        orientation = HORIZONTAL
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.AppBarLayout,
            0, R.style.Widget_Design_AppBarLayout
        )
        ViewCompat.setBackground(this, a.getDrawable(R.styleable.AppBarLayout_android_background))
        if (a.hasValue(R.styleable.AppBarLayout_expanded)) {
            setExpanded(a.getBoolean(R.styleable.AppBarLayout_expanded, false), false, false)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            // In O+, we have these values set in the style. Since there is no defStyleAttr for
            // AppBarLayout at the AppCompat level, check for these attributes here.
            if (a.hasValue(R.styleable.AppBarLayout_android_keyboardNavigationCluster)) {
                this.isKeyboardNavigationCluster = a.getBoolean(
                    R.styleable.AppBarLayout_android_keyboardNavigationCluster, false
                )
            }
            if (a.hasValue(R.styleable.AppBarLayout_android_touchscreenBlocksFocus)) {
                this.touchscreenBlocksFocus = a.getBoolean(
                    R.styleable.AppBarLayout_android_touchscreenBlocksFocus, false
                )
            }
        }
        a.recycle()
        ViewCompat.setOnApplyWindowInsetsListener(
            this
        ) { v: View?, insets: WindowInsetsCompat? ->
            onWindowInsetChanged(
                insets
            )
        }
    }
}