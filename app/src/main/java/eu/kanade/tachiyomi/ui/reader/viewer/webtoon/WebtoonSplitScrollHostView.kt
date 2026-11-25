package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager

/**
 * Structural container for a future Split Scroll reader.
 *
 * Each side owns a separate WebtoonFrame and WebtoonRecyclerView, so pinch, pan,
 * fling and vertical scrolling are scoped to the touched column. A full-width
 * boundary slot sits after the two columns and is shown only when the segment
 * barrier coordinator releases it.
 *
 * This view is intentionally not installed by WebtoonViewer yet. Its recyclers
 * have independent LayoutManagers but no adapters until the viewer integration
 * can supply correctly segmented adapters and lifecycle-safe holders.
 */
internal class WebtoonSplitScrollHostView(
    context: Context,
) : FrameLayout(context) {

    val leftRecycler: WebtoonRecyclerView
    val rightRecycler: WebtoonRecyclerView

    private val leftFrame: WebtoonFrame
    private val rightFrame: WebtoonFrame
    private val columnsRow: LinearLayout
    private val boundarySlot: FrameLayout

    var leftWidthPercent: Int = DEFAULT_LEFT_WIDTH_PERCENT
        private set

    init {
        val scrollDistance = resources.displayMetrics.heightPixels * 3 / 4

        leftRecycler = createRecycler(scrollDistance)
        rightRecycler = createRecycler(scrollDistance)

        leftFrame = WebtoonFrame(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            addView(
                leftRecycler,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        rightFrame = WebtoonFrame(context).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            addView(
                rightRecycler,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        columnsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(leftFrame)
            addView(rightFrame)
        }
        addView(
            columnsRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        boundarySlot = FrameLayout(context).apply {
            visibility = GONE
        }
        addView(
            boundarySlot,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        setLeftWidthPercent(DEFAULT_LEFT_WIDTH_PERCENT)
    }

    private fun createRecycler(scrollDistance: Int): WebtoonRecyclerView =
        WebtoonRecyclerView(context).apply {
            setItemViewCacheSize(SPLIT_SCROLL_CACHE_SIZE)
            isFocusable = false
            itemAnimator = null
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            layoutManager = WebtoonLayoutManager(context, scrollDistance)
        }

    /** Apply one shared width split while preserving independent column scrolling. */
    fun setLeftWidthPercent(percent: Int) {
        leftWidthPercent = percent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)
        leftFrame.layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            leftWidthPercent.toFloat(),
        )
        rightFrame.layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            (100 - leftWidthPercent).toFloat(),
        )
        columnsRow.requestLayout()
    }

    fun setDoubleTapZoomEnabled(enabled: Boolean) {
        leftFrame.doubleTapZoom = enabled
        rightFrame.doubleTapZoom = enabled
    }

    fun setZoomOutDisabled(disabled: Boolean) {
        leftFrame.zoomOutDisabled = disabled
        rightFrame.zoomOutDisabled = disabled
    }

    /**
     * Display a full-width boundary view after the current two-column segment.
     * The caller owns the view and must only show it after both columns are done.
     */
    fun showBoundary(view: View) {
        boundarySlot.removeAllViews()
        boundarySlot.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        columnsRow.visibility = GONE
        boundarySlot.visibility = VISIBLE
    }

    fun hideBoundary() {
        boundarySlot.removeAllViews()
        boundarySlot.visibility = GONE
        columnsRow.visibility = VISIBLE
    }

    /** Attach independent adapters supplied by the future segmented viewer. */
    fun setColumnAdapters(
        left: RecyclerView.Adapter<out RecyclerView.ViewHolder>?,
        right: RecyclerView.Adapter<out RecyclerView.ViewHolder>?,
    ) {
        leftRecycler.adapter = left
        rightRecycler.adapter = right
    }

    companion object {
        const val DEFAULT_LEFT_WIDTH_PERCENT = 50
        const val MIN_WIDTH_PERCENT = 20
        const val MAX_WIDTH_PERCENT = 80
        private const val SPLIT_SCROLL_CACHE_SIZE = 4
    }
}
