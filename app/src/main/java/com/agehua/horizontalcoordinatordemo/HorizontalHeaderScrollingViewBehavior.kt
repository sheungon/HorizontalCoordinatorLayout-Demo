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
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.math.MathUtils
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat

/**
 *
 */
abstract class HorizontalHeaderScrollingViewBehavior :
    ViewOffsetBehavior<View> {

    val tempRect1 = Rect()
    val tempRect2 = Rect()

    /**
     * The gap between the top of the scrolling view and the bottom of the header layout in pixels.
     */
    var horizontalLayoutGap = 0
        private set

    /**
     * @param overlayLeft the distance in px
     */
    var overlayLeft = 0

    constructor()
    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : super(context, attrs)

    abstract fun findFirstDependency(views: List<View>): View?

    override fun onMeasureChild(
        parent: HorizontalCoordinatorLayout,
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ): Boolean {
        val childLpWidth = child.layoutParams.width
        if (childLpWidth == ViewGroup.LayoutParams.MATCH_PARENT
            || childLpWidth == ViewGroup.LayoutParams.WRAP_CONTENT
        ) {
            // If the menu's height is set to match_parent/wrap_content then measure it
            // with the maximum visible height
            val dependencies =
                parent.getDependencies(child)
            val header = findFirstDependency(dependencies)
            if (header != null) {
                if (ViewCompat.getFitsSystemWindows(header)
                    && !ViewCompat.getFitsSystemWindows(child)
                ) {
                    // If the header is fitting system windows then we need to also,
                    // otherwise we'll get CoL's compatible measuring
                    ViewCompat.setFitsSystemWindows(child, true)
                    if (ViewCompat.getFitsSystemWindows(child)) {
                        // If the set succeeded, trigger a new layout and return true
                        child.requestLayout()
                        return true
                    }
                }
                var availableWidth =
                    View.MeasureSpec.getSize(parentWidthMeasureSpec)
                if (availableWidth == 0) {
                    // If the measure spec doesn't specify a size, use the current height
                    availableWidth = parent.width
                }
                val width = (availableWidth - header.measuredWidth
                        + getScrollRange(header))
                val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                    width,
                    if (childLpWidth == ViewGroup.LayoutParams.MATCH_PARENT) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST
                )

                // Now measure the scrolling view with the correct height
                parent.onMeasureChild(
                    child, widthMeasureSpec,
                    widthUsed, parentHeightMeasureSpec, heightUsed
                )
                return true
            }
        }
        return false
    }

    override fun layoutChild(
        parent: HorizontalCoordinatorLayout,
        child: View,
        layoutDirection: Int
    ) {
        val dependencies =
            parent.getDependencies(child)
        val header = findFirstDependency(dependencies)
        if (header != null) {
            val lp =
                child.layoutParams as HorizontalCoordinatorLayout.LayoutParams
            val available = tempRect1
            if (layoutDirection == 0) { // Horizontal
                available[header.right + lp.leftMargin, parent.top + lp.topMargin, parent.width - parent.paddingRight - lp.rightMargin] =
                    parent.height + header.bottom - parent.paddingBottom - lp.bottomMargin
            } else {
                available[parent.paddingLeft + lp.leftMargin, header.bottom + lp.topMargin, parent.width - parent.paddingRight - lp.rightMargin] =
                    parent.height + header.bottom - parent.paddingBottom - lp.bottomMargin
            }
            val parentInsets = parent.lastWindowInsets
            if (parentInsets != null && ViewCompat.getFitsSystemWindows(parent)
                && !ViewCompat.getFitsSystemWindows(child)
            ) {
                // If we're set to handle insets but this child isn't, then it has been measured as
                // if there are no insets. We need to lay it out to match horizontally.
                // Top and bottom and already handled in the logic above
                available.left += parentInsets.systemWindowInsetLeft
                available.right -= parentInsets.systemWindowInsetRight
            }
            val out = tempRect2
            GravityCompat.apply(
                resolveGravity(lp.gravity),
                child.measuredWidth,
                child.measuredHeight,
                available,
                out,
                layoutDirection
            )
            val overlap = getOverlapPixelsForOffset(header)
            child.layout(out.left - overlap, out.top, out.right - overlap, out.bottom)
            horizontalLayoutGap = out.left - header.right
        } else {
            // If we don't have a dependency, let super handle it
            super.layoutChild(parent, child, layoutDirection)
            horizontalLayoutGap = 0
        }
    }

    open fun getOverlapRatioForOffset(header: View): Float = 1f

    fun getOverlapPixelsForOffset(header: View): Int =
        if (overlayLeft == 0) 0
        else MathUtils.clamp(
            (getOverlapRatioForOffset(header) * overlayLeft).toInt(), 0, overlayLeft
        )

    open fun getScrollRange(view: View): Int = view.measuredHeight

    companion object {
        private fun resolveGravity(gravity: Int): Int =
            if (gravity == Gravity.NO_GRAVITY) GravityCompat.START or Gravity.TOP
            else gravity
    }
}