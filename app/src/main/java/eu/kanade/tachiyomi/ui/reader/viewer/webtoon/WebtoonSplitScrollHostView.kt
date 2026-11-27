package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager

/**
 * Two-column Split Scroll host. Each side owns an independent WebtoonFrame,
 * RecyclerView and LayoutManager. The draggable center handle adjusts the
 * shared width ratio without coupling the columns' scroll positions. A full-width
 * boundary slot is shown only when the segment barrier coordinator releases it.
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
    private val resizeHandle: FrameLayout

    var onLeftWidthChanged: ((Int) -> Unit)? = null

    private var dragStartWidthPercent = DEFAULT_LEFT_WIDTH_PERCENT

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
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
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

        resizeHandle = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Resize reader columns"
            setOnTouchListener { view, event ->
                fun updateWidth() {
                    val location = IntArray(2)
                    columnsRow.getLocationOnScreen(location)
                    val availableWidth = columnsRow.width
                    val percent = WebtoonSplitScrollColumnWidths.percentAtPosition(
                        positionX = event.rawX,
                        containerLeft = location[0].toFloat(),
                        containerWidth = availableWidth.toFloat(),
                        currentPercent = leftWidthPercent,
                    )
                    setLeftWidthPercent(percent)
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartWidthPercent = leftWidthPercent
                        updateWidth()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        updateWidth()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        updateWidth()
                        onLeftWidthChanged?.invoke(leftWidthPercent)
                        view.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        setLeftWidthPercent(dragStartWidthPercent)
                        true
                    }
                    else -> true
                }
            }
            val line = View(context).apply {
                setBackgroundColor(Color.GRAY)
                alpha = 0.65f
            }
            addView(
                line,
                FrameLayout.LayoutParams(
                    (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2),
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
        }
        // Keep the drag target compact so it does not block page gestures or
        // independent vertical scrolling across the full height of the reader.
        addView(
            resizeHandle,
            FrameLayout.LayoutParams(
                (48 * resources.displayMetrics.density).toInt().coerceAtLeast(48),
                (72 * resources.displayMetrics.density).toInt().coerceAtLeast(72),
                Gravity.CENTER_VERTICAL,
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
        leftWidthPercent = WebtoonSplitScrollColumnWidths.normalize(percent)
        val (leftWeight, rightWeight) = WebtoonSplitScrollColumnWidths.weights(leftWidthPercent)
        leftFrame.layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            leftWeight,
        )
        rightFrame.layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            rightWeight,
        )
        columnsRow.requestLayout()
        resizeHandle.translationX = columnsRow.width * leftWidthPercent / 100f - resizeHandle.width / 2f
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        // The row's width is only reliable after its first layout pass.
        resizeHandle.translationX = columnsRow.width * leftWidthPercent / 100f - resizeHandle.width / 2f
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
    fun showBoundary(view: View, onContinue: () -> Unit) {
        boundarySlot.removeAllViews()
        // The existing transition card has no action of its own. Make the full-width
        // boundary an explicit tap target so the reader can enter the next segment.
        view.setOnClickListener { onContinue() }
        view.isClickable = true
        boundarySlot.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        columnsRow.visibility = GONE
        resizeHandle.visibility = GONE
        boundarySlot.visibility = VISIBLE
    }

    fun hideBoundary() {
        boundarySlot.getChildAt(0)?.setOnClickListener(null)
        boundarySlot.removeAllViews()
        boundarySlot.visibility = GONE
        columnsRow.visibility = VISIBLE
        resizeHandle.visibility = VISIBLE
    }

    /** Attach the independent page adapters for the active segment. */
    fun setColumnAdapters(
        left: RecyclerView.Adapter<out RecyclerView.ViewHolder>?,
        right: RecyclerView.Adapter<out RecyclerView.ViewHolder>?,
    ) {
        leftRecycler.adapter = left
        rightRecycler.adapter = right
    }

    companion object {
        const val DEFAULT_LEFT_WIDTH_PERCENT = 50
        const val MIN_WIDTH_PERCENT = WebtoonSplitScrollColumnWidths.MIN_PERCENT
        const val MAX_WIDTH_PERCENT = WebtoonSplitScrollColumnWidths.MAX_PERCENT
        private const val SPLIT_SCROLL_CACHE_SIZE = 4
    }
}
