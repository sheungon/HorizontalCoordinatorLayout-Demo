package com.agehua.horizontalcoordinatordemo

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.WindowInsetsCompat

class HorizontalFloatingButtonBehaviour : ViewOffsetBehavior<LinearLayout> {

    constructor()
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun layoutDependsOn(
        parent: HorizontalCoordinatorLayout,
        child: LinearLayout,
        dependency: View
    ): Boolean {
        return dependency is HorizontalAppBarLayout
    }

    override fun onDependentViewChanged(
        parent: HorizontalCoordinatorLayout,
        child: LinearLayout,
        dependency: View
    ): Boolean {
        val bottom = dependency.bottom
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

    override fun onApplyWindowInsets(
        coordinatorLayout: HorizontalCoordinatorLayout,
        child: LinearLayout,
        insets: WindowInsetsCompat
    ): WindowInsetsCompat {
        return super.onApplyWindowInsets(coordinatorLayout, child, insets)
    }
}