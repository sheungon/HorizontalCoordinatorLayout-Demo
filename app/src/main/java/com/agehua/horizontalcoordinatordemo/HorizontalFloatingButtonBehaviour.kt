package com.agehua.horizontalcoordinatordemo

import android.content.Context
import android.util.AttributeSet
import android.view.View

@Suppress("unused")
class HorizontalFloatingButtonBehaviour : ViewOffsetBehavior<View> {

    constructor()
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun layoutDependsOn(
        parent: HorizontalCoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean = dependency is HorizontalAppBarLayout

    override fun onDependentViewChanged(
        parent: HorizontalCoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean {
        val bottom = dependency.bottom - parent.paddingTop
        val center = bottom / 2f
        val halfChild = child.height / 2f
        val width = child.width
        setTopAndBottomOffset((center - halfChild).toInt())
        if (dependency is HorizontalAppBarLayout) {
            val totalScrollRange = dependency.totalScrollRange.toFloat()
            val movedPercentage = dependency.getRight() / totalScrollRange
            child.x = -width * movedPercentage
        }
        return true
    }
}