package io.legado.app.ui.widget

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.utils.Paginator

/**
 * 墨水屏列表分页器（公共组件）。
 *
 * 电子纸上滚动会持续刷新并累积残影，所以长列表统一改成「翻页」。
 * 这里把三件容易写错、之前各页面各写一遍的事收拢到一处：
 *
 * 1. **自适应每页条数**：列表项高度并不一致（同一条规则可能一行摘要也可能两行），
 *    固定条数必然出问题——按矮的定会溢出，按高的定会空一大块。
 *    因此每页渲染完成后测量本页实际放得下几条，回填给 [Paginator]，
 *    让每一页的边界都由自己的内容决定。
 * 2. **分页后禁止滚动**：列表已按页切好，再允许上下滑会出现「翻页了还能滑」的半空列表。
 *    注意 `isNestedScrollingEnabled = false` 只关掉把滚动交给父容器，
 *    要挡住 RecyclerView 自身滚动必须让 LayoutManager 声明不可滚动。
 * 3. **翻页**：左右滑动，边界判断统一在这里。
 *
 * 调用方只负责把数据交给 [setAll]，并在 [onItemsChanged] 里刷新 adapter。
 */
class EinkPager<T>(
    private val recyclerView: RecyclerView,
    private val pageBar: PageBar,
    private val itemName: String = "条"
) {

    companion object {
        /**
         * 未测量页的预估条数：先给一个偏大的值，让列表渲染到溢出屏幕，
         * 这样按子项底边统计才能得到真实容量。测量完会被实际值取代。
         */
        private const val INITIAL_PAGE_SIZE = 40

        private const val MAX_MEASURE_ATTEMPTS = 20
        private const val MEASURE_RETRY_DELAY_MS = 50L

        /** 数据落地并完成一次布局需要的时间 */
        private const val LAYOUT_SETTLE_DELAY_MS = 120L
    }

    private val paginator = Paginator<T>(INITIAL_PAGE_SIZE)
    private var measureAttempts = 0
    private var measuring = false

    /** 当前页数据变化通知，调用方在这里刷新 adapter */
    var onItemsChanged: ((List<T>) -> Unit)? = null

    val page: Int get() = paginator.page
    val pageCount: Int get() = paginator.pageCount
    val total: Int get() = paginator.total

    init {
        disableScrolling()
        recyclerView.itemAnimator = null
        // 已经有左右滑动翻页，底栏的上一页/下一页按钮是重复入口
        pageBar.showButtons = false
        pageBar.onPageChange = { delta -> moveBy(delta) }
    }

    /**
     * 真正禁止列表滚动。
     *
     * `isNestedScrollingEnabled = false` 只影响嵌套滚动，RecyclerView 自身照样能滚；
     * 必须让 LayoutManager 声明不可滚动才能挡住。
     */
    private fun disableScrolling() {
        val existing = recyclerView.layoutManager
        if (existing is LinearLayoutManager && existing !is UnscrollableLayoutManager) {
            recyclerView.layoutManager = UnscrollableLayoutManager(
                recyclerView.context, existing.orientation, existing.reverseLayout
            ).apply {
                stackFromEnd = existing.stackFromEnd
            }
        }
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
    }

    /**
     * 声明自身不可滚动的 LinearLayoutManager。
     */
    private class UnscrollableLayoutManager(
        context: android.content.Context,
        orientation: Int,
        reverseLayout: Boolean
    ) : LinearLayoutManager(context, orientation, reverseLayout) {

        override fun canScrollVertically(): Boolean = false

        override fun canScrollHorizontally(): Boolean = false
    }

    /**
     * 全量数据变化。
     */
    fun setAll(items: List<T>) {
        // LayoutManager 可能在 EinkPager 构造之后才被页面设置，这里再兜一次
        disableScrolling()
        val slice = paginator.setAll(items)
        onItemsChanged?.invoke(slice)
        updatePageBar()
        measureCurrentPage()
    }

    /**
     * 数据变了且项高也可能变（搜索、排序、分组）时调用。
     * 现在每页都会自行测量，这里与 [setAll] 等价，保留名字表达调用方意图。
     */
    fun setAllAndRecalibrate(items: List<T>) {
        setAll(items)
    }

    fun moveBy(delta: Int): Boolean {
        val slice = paginator.moveBy(delta) ?: return false
        onItemsChanged?.invoke(slice)
        updatePageBar()
        recyclerView.scrollToPosition(0)
        measureCurrentPage()
        return true
    }

    fun moveTo(target: Int): Boolean {
        if (target !in 0 until paginator.pageCount) return false
        onItemsChanged?.invoke(paginator.moveTo(target))
        updatePageBar()
        recyclerView.scrollToPosition(0)
        measureCurrentPage()
        return true
    }

    /**
     * 左右滑动翻页：-1 上一页，1 下一页。已在边界时返回 false。
     */
    fun onSwipe(direction: Int): Boolean = moveBy(direction)

    /**
     * 回到第一页（搜索、筛选后调用）。
     */
    fun reset() {
        onItemsChanged?.invoke(paginator.reset())
        updatePageBar()
        recyclerView.scrollToPosition(0)
        measureCurrentPage()
    }

    /**
     * 测量当前页实际能完整放下几条，回填给分页器。
     * 条数有变化时重新切片并刷新，从而让每页都恰好填满。
     */
    private fun measureCurrentPage() {
        if (measuring) return
        measuring = true
        recyclerView.postDelayed({ doMeasure() }, LAYOUT_SETTLE_DELAY_MS)
    }

    private fun doMeasure() {
        val visibleHeight = recyclerView.height -
            recyclerView.paddingTop - recyclerView.paddingBottom
        if (visibleHeight <= 0 || recyclerView.childCount == 0) {
            if (measureAttempts++ < MAX_MEASURE_ATTEMPTS) {
                recyclerView.postDelayed({ doMeasure() }, MEASURE_RETRY_DELAY_MS)
            } else {
                measuring = false
            }
            return
        }
        var fitCount = 0
        for (i in 0 until recyclerView.childCount) {
            if (recyclerView.getChildAt(i).bottom > visibleHeight) break
            fitCount++
        }
        if (fitCount <= 0) {
            if (measureAttempts++ < MAX_MEASURE_ATTEMPTS) {
                recyclerView.postDelayed({ doMeasure() }, MEASURE_RETRY_DELAY_MS)
            } else {
                measuring = false
            }
            return
        }
        measureAttempts = 0
        val changed = paginator.commitPageSize(fitCount)
        // 用实测条数作为后续未测量页的估算基准，否则那些页仍按初始的 40 条估，
        // 页数会随翻页一页页往上跳（1/2、2/3、3/4…），看起来像算错了
        paginator.setDefaultPageSize(fitCount)
        if (changed) {
            // 本页条数变了，页边界随之变化，重新切片
            onItemsChanged?.invoke(paginator.slice())
            updatePageBar()
            recyclerView.postDelayed({
                measuring = false
                // 重新切片后可能又有一轮布局，再确认一次（commitPageSize 幂等，不会死循环）
                doMeasure()
            }, LAYOUT_SETTLE_DELAY_MS)
        } else {
            measuring = false
        }
    }

    private fun updatePageBar() {
        pageBar.update(paginator.page, paginator.pageCount, paginator.total, itemName)
    }
}
