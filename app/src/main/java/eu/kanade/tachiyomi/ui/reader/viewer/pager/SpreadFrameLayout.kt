package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.view.ViewGroup
import eu.kanade.tachiyomi.widget.ViewPagerAdapter

/**
 * Lightweight display container for a Spread item.
 *
 * It owns no reader pages and performs no loading, selection, or navigation work.
 * Its children are the original page-holder views created for the active adapter
 * projection. More than two children are rejected to prevent accidental misuse.
 */
internal class SpreadFrameLayout(context: Context) : ViewGroup(context), ViewPagerAdapter.PositionableView {

    private var projectedUnit: SpreadAdapterItemMapper.Unit<Any>? = null

    /** Stable source identity used by ViewPagerAdapter when it repositions instantiated frames. */
    override val item: Any
        get() = requireNotNull(projectedUnit) { "Spread frame has not been bound" }

    var isR2L: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    /**
     * Attach the original page holders for one projected display unit.
     *
     * The holders remain the owners of image loading, zoom, gestures, and page identity.
     * This container only arranges them; permanent disposal remains the pager adapter's
     * responsibility when it recursively disposes the removed view tree.
     */
    fun bind(
        unit: SpreadAdapterItemMapper.Unit<Any>,
        first: PagerPageHolder,
        second: PagerPageHolder? = null,
        isR2L: Boolean = false,
    ) {
        check(projectedUnit == null || projectedUnit === unit) {
            "A Spread frame cannot be rebound to a different display unit"
        }
        when (unit) {
            is SpreadAdapterItemMapper.Unit.Single -> {
                require(unit.item === first.item) {
                    "The first holder does not match the projected page"
                }
                require(second == null) {
                    "A single-page display unit cannot contain a second holder"
                }
            }
            is SpreadAdapterItemMapper.Unit.Pair -> {
                require(unit.first === first.item && unit.second === second?.item) {
                    "Page holders do not match the projected spread"
                }
            }
        }
        require(first !== second) { "A page holder cannot occupy both spread slots" }
        require(first.parent == null || first.parent === this) {
            "The first page holder is already attached to another parent"
        }
        require(second == null || second.parent == null || second.parent === this) {
            "The second page holder is already attached to another parent"
        }

        // Detach existing children first. PagerPageHolder treats this as a reusable
        // detach, not permanent disposal, and resumes its state collector on reattach.
        removeAllViews()
        projectedUnit = unit
        this.isR2L = isR2L
        addView(first)
        if (second != null) addView(second)
    }

    /** Remove children without disposing their reusable page holders. */
    fun detachPageHolders() {
        removeAllViews()
    }

    /** Release the adapter identity after the pager permanently destroys this frame. */
    fun clearBinding() {
        removeAllViews()
        projectedUnit = null
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

    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT,
    )

    override fun generateLayoutParams(attrs: android.util.AttributeSet?): LayoutParams = LayoutParams(context, attrs)

    override fun generateLayoutParams(p: LayoutParams): LayoutParams = LayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams): Boolean = p is LayoutParams
}
