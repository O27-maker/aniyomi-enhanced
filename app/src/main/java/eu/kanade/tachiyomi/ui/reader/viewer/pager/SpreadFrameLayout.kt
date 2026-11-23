package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.view.ViewGroup

/**
 * Lightweight display container for a Spread item.
 *
 * It owns no reader pages and performs no loading, selection, or navigation work.
 * Its children are supplied by a future adapter integration and remain the
 * original page-holder views. More than two children are rejected to prevent
 * accidental misuse while the feature is experimental.
 */
internal class SpreadFrameLayout(context: Context) : ViewGroup(context) {

    var isR2L: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        check(childCount <= 2) { "SpreadFrameLayout supports at most two page children" }
        val width = resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec)
        val height = resolveSize(MeasureSpec.getSize(heightMeasureSpec), heightMeasureSpec)
        val slots = if (childCount == 2) SpreadLayoutGeometry.pair(isR2L) else listOf(SpreadLayoutGeometry.single())

        for (index in 0 until childCount) {
            val slot = slots[index]
            val childWidth = (width * slot.width).toInt()
            val childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
            val childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            getChildAt(index).measure(childWidthSpec, childHeightSpec)
        }
        setMeasuredDimension(width, height)
    }


    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val slots = if (childCount == 2) SpreadLayoutGeometry.pair(isR2L) else listOf(SpreadLayoutGeometry.single())
        for (index in 0 until childCount) {
            val slot = slots[index]
            val childLeft = (width * slot.left).toInt()
            val childRight = (width * slot.right).toInt()
            getChildAt(index).layout(childLeft, 0, childRight, bottom - top)
        }
    }


    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)


    override fun generateLayoutParams(attrs: android.util.AttributeSet?): LayoutParams = LayoutParams(context, attrs)


    override fun generateLayoutParams(p: LayoutParams): LayoutParams = LayoutParams(p)


    override fun checkLayoutParams(p: LayoutParams): Boolean = p is LayoutParams

}

