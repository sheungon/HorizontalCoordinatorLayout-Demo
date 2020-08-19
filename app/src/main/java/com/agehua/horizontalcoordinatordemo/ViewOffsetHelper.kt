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

import android.view.View
import androidx.core.view.ViewCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility helper for moving a [View] around using
 * [View.offsetLeftAndRight] and
 * [View.offsetTopAndBottom].
 *
 *
 * Also the setting of absolute offsets (similar to translationX/Y), rather than additive
 * offsets.
 */
internal class ViewOffsetHelper(private val view: View) {

    private val layoutTop = AtomicInteger(0)
    private val layoutLeft = AtomicInteger(0)
    private val topAndBottomOffset = AtomicInteger(0)
    private val leftAndRightOffset = AtomicInteger(0)

    fun getLayoutTop(): Int = layoutTop.get()
    fun getLayoutLeft(): Int = layoutLeft.get()
    fun getTopAndBottomOffset(): Int = topAndBottomOffset.get()
    fun getLeftAndRightOffset(): Int = leftAndRightOffset.get()

    fun onViewLayout() {
        // Now grab the intended top
        layoutTop.set(view.top)
        layoutLeft.set(view.left)

        // And offset it as needed
        updateOffsets()
    }

    private fun updateOffsets() {
        ViewCompat.offsetTopAndBottom(
            view,
            topAndBottomOffset.get() - (view.top - layoutTop.get())
        )
        ViewCompat.offsetLeftAndRight(
            view,
            leftAndRightOffset.get() - (view.left - layoutLeft.get())
        )
    }

    /**
     * Set the top and bottom offset for this [ViewOffsetHelper]'s view.
     *
     * @param offset the offset in px.
     * @return true if the offset has changed
     */
    fun setTopAndBottomOffset(offset: Int): Boolean {
        if (topAndBottomOffset.get() != offset) {
            topAndBottomOffset.set(offset)
            updateOffsets()
            return true
        }
        return false
    }

    /**
     * Set the left and right offset for this [ViewOffsetHelper]'s view.
     *
     * @param offset the offset in px.
     * @return true if the offset has changed
     */
    fun setLeftAndRightOffset(offset: Int): Boolean {
        if (leftAndRightOffset.get() != offset) {
            leftAndRightOffset.set(offset)
            updateOffsets()
            return true
        }
        return false
    }
}