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
import android.view.View
import java.util.concurrent.atomic.AtomicInteger

/**
 * Behavior will automatically sets up a [ViewOffsetHelper] on a [View].
 */
open class ViewOffsetBehavior<V : View> :
    HorizontalCoordinatorLayout.Behavior<V> {
    private var viewOffsetHelper: ViewOffsetHelper? = null
    private val tempTopBottomOffset = AtomicInteger(0)
    private var tempLeftRightOffset = AtomicInteger(0)

    constructor()
    constructor(context: Context, attrs: AttributeSet?) : super(
        context,
        attrs
    )

    override fun onLayoutChild(
        parent: HorizontalCoordinatorLayout,
        child: V,
        layoutDirection: Int
    ): Boolean {
        // First let lay the child out
        layoutChild(parent, child, layoutDirection)
        var viewOffsetHelper = viewOffsetHelper
        if (viewOffsetHelper == null) {
            viewOffsetHelper = ViewOffsetHelper(child).also {
                this.viewOffsetHelper = it
            }
        }
        viewOffsetHelper.onViewLayout()
        val topBottomOffset = tempTopBottomOffset.getAndSet(0)
        if (topBottomOffset != 0) {
            viewOffsetHelper.setTopAndBottomOffset(topBottomOffset)
        }
        val leftRightOffset = tempLeftRightOffset.getAndSet(0)
        if (leftRightOffset != 0) {
            viewOffsetHelper.setLeftAndRightOffset(leftRightOffset)
        }
        return true
    }

    protected open fun layoutChild(
        parent: HorizontalCoordinatorLayout,
        child: V,
        layoutDirection: Int
    ) {
        // Let the parent lay it out by default
        parent.onLayoutChild(child, layoutDirection)
    }

    fun setTopAndBottomOffset(offset: Int): Boolean {
        val viewOffsetHelper = viewOffsetHelper
        tempTopBottomOffset.set(
            if (viewOffsetHelper != null) {
                return viewOffsetHelper.setTopAndBottomOffset(offset)
            } else {
                offset
            }
        )
        return false
    }

    fun setLeftAndRightOffset(offset: Int): Boolean {
        val viewOffsetHelper = viewOffsetHelper
        tempLeftRightOffset.set(
            if (viewOffsetHelper != null) {
                return viewOffsetHelper.setLeftAndRightOffset(offset)
            } else {
                offset
            }
        )
        return false
    }

    val topAndBottomOffset: Int
        get() = viewOffsetHelper?.getTopAndBottomOffset() ?: 0

    val leftAndRightOffset: Int
        get() = viewOffsetHelper?.getLeftAndRightOffset() ?: 0
}