package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollColumnWidths

/**
 * Two independent vertical columns for the paged reader's Split Scroll presentation.
 *
 * Each column has its own RecyclerView/LayoutManager and therefore its own scroll
 * position. The host deliberately does not synchronize them. Chapter transitions
 * are presented in the full-width boundary slot between segments.
 */
internal class PagerSplitScrollHostView(
    context: Context,
) : FrameLayout(context) {

    val leftRecycler: RecyclerView = createRecycler(context)
    val rightRecycler: RecyclerView = createRecycler(context)

    private val leftFrame = FrameLayout(context).apply {
        addView(leftRecycler, matchParent())
    }
    private val rightFrame = FrameLayout(context).apply {
        addView(rightRecycler, matchParent())
    }
    private val columnsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(leftFrame, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(rightFrame, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }
    private val boundarySlot = FrameLayout(context).apply { visibility = GONE }
    private val resizeHandle = FrameLayout(context).apply {
        isClickable = true
        isFocusable = true
        contentDescription = "Resize reader columns"
        val line = View(context).apply {
            setBackgroundColor(Color.GRAY)
            alpha = 0.65f
        }
        addView(line, LayoutParams(
            (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2),
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER,
        ))
        setOnTouchListener { view, event ->
            fun update() {
                val location = IntArray(2)
                columnsRow.getLocationOnScreen(location)
                val percent = WebtoonSplitScrollColumnWidths.percentAtPosition(
                    positionX = event.rawX,
                    containerLeft = location[0].toFloat(),
                    containerWidth = columnsRow.width.toFloat(),
                    currentPercent = leftWidthPercent,
                )
                setLeftWidthPercent(percent)
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartWidthPercent = leftWidthPercent
                    update()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    update()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    update()
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
    }

    var onLeftWidthChanged: ((Int) -> Unit)? = null
    var onTap: ((MotionEvent) -> Unit)? = null
    private var dragStartWidthPercent = DEFAULT_LEFT_WIDTH_PERCENT

    var leftWidthPercent: Int = DEFAULT_LEFT_WIDTH_PERCENT
        private set

    init {
        addView(columnsRow, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        addView(boundarySlot, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        addView(resizeHandle, LayoutParams(
            (48 * resources.displayMetrics.density).toInt().coerceAtLeast(48),
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.LEFT,
        ))
        setLeftWidthPercent(DEFAULT_LEFT_WIDTH_PERCENT)
        installTapListener(leftRecycler)
        installTapListener(rightRecycler)
    }

    /**
     * Observe confirmed single taps without intercepting the page holder's own zoom gestures.
     * Double taps are left to PagerPageHolder and are not forwarded as navigation taps.
     */
    private fun installTapListener(recycler: RecyclerView) {
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                onTap?.invoke(event)
                return true
            }
        })
        recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                detector.onTouchEvent(event)
                return false
            }
        })
    }

    fun setLeftWidthPercent(percent: Int) {
        leftWidthPercent = WebtoonSplitScrollColumnWidths.normalize(percent)
        val (leftWeight, rightWeight) = WebtoonSplitScrollColumnWidths.weights(leftWidthPercent)
        leftFrame.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, leftWeight,
        )
        rightFrame.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, rightWeight,
        )
        columnsRow.requestLayout()
        updateHandlePosition()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateHandlePosition()
    }

    private fun updateHandlePosition() {
        resizeHandle.translationX =
            columnsRow.width * leftWidthPercent / 100f - resizeHandle.width / 2f
    }

    fun setColumnAdapters(
        left: RecyclerView.Adapter<out RecyclerView.ViewHolder>?,
        right: RecyclerView.Adapter<out RecyclerView.ViewHolder>?,
    ) {
        leftRecycler.adapter = left
        rightRecycler.adapter = right
    }

    fun showBoundary(view: View, onContinue: () -> Unit = {}) {
        view.setOnClickListener { onContinue() }
        view.isClickable = true
        boundarySlot.removeAllViews()
        boundarySlot.addView(view, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        columnsRow.visibility = GONE
        resizeHandle.visibility = GONE
        boundarySlot.visibility = VISIBLE
    }

    fun hideBoundary() {
        boundarySlot.removeAllViews()
        boundarySlot.visibility = GONE
        columnsRow.visibility = VISIBLE
        resizeHandle.visibility = VISIBLE
    }

    fun clearAdapters() {
        leftRecycler.adapter = null
        rightRecycler.adapter = null
    }

    private fun createRecycler(context: Context) = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        itemAnimator = null
        setItemViewCacheSize(4)
        isFocusable = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    companion object {
        const val DEFAULT_LEFT_WIDTH_PERCENT = 50
        const val MIN_WIDTH_PERCENT = WebtoonSplitScrollColumnWidths.MIN_PERCENT
        const val MAX_WIDTH_PERCENT = WebtoonSplitScrollColumnWidths.MAX_PERCENT
    }
}
