package io.legado.app.ui.widget

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.utils.Paginator

/**
 * 墨水屏列表分页器（公共组件）。
 *
 * 电子纸上滚动会持续刷新并累积残影，所以长列表统一改成「每页 N 项 + 翻页」。
 * 这里把三件容易写错、之前各页面各写一遍的事收拢到一处：
 *
 * 1. **自适应每页条数**：按 RecyclerView 实际可视高度截断。
 *    关键点是初始页条数必须大于一屏容量，否则列表根本不会溢出，
 *    统计出的条数会被初始值封顶（此前固定初始值 8，一屏能放 10 条时永远只能得到 8）。
 * 2. **分页后禁止滚动**：列表已按页切好，再允许上下滑就会出现「翻页了还能滑」的半空列表。
 * 3. **翻页**：底栏按钮与左右滑动都走同一套边界判断。
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
         * 校准用的初始页条数：故意远大于一屏容量，保证列表溢出屏幕，
         * 这样按子项底边统计才能得到真实容量。校准完成后会被实际值取代。
         */
        private const val INITIAL_PAGE_SIZE = 40

        /** 单页最少条数，避免项特别高时每页只剩一两条 */
        private const val MIN_PAGE_SIZE = 3

        private const val MAX_CALIBRATE_ATTEMPTS = 20
        private const val CALIBRATE_RETRY_DELAY_MS = 50L
    }

    private val paginator = Paginator<T>(INITIAL_PAGE_SIZE)
    private var calibrated = false
    private var calibrateAttempts = 0

    /** 当前页数据变化通知，调用方在这里刷新 adapter */
    var onItemsChanged: ((List<T>) -> Unit)? = null

    /** 是否已经完成每页条数校准 */
    val isCalibrated: Boolean get() = calibrated

    val page: Int get() = paginator.page
    val pageCount: Int get() = paginator.pageCount
    val total: Int get() = paginator.total

    init {
        // 分页后不允许再滚动，否则会出现「已经翻页还能上下滑」的错乱状态
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        recyclerView.itemAnimator = null
        pageBar.onPageChange = { delta -> moveBy(delta) }
    }

    /**
     * 全量数据变化。只在首次调用时校准每页条数。
     */
    fun setAll(items: List<T>) {
        val slice = paginator.setAll(items)
        onItemsChanged?.invoke(slice)
        updatePageBar()
        calibrateIfNeeded()
    }

    /**
     * 数据变了但项高可能也变了（例如切换分组、内容变长）时，丢弃已校准结果重新算。
     */
    fun setAllAndRecalibrate(items: List<T>) {
        calibrated = false
        calibrateAttempts = 0
        setAll(items)
    }

    fun moveBy(delta: Int): Boolean {
        val target = paginator.page + delta
        if (target !in 0 until paginator.pageCount) return false
        onItemsChanged?.invoke(paginator.moveTo(target))
        updatePageBar()
        recyclerView.scrollToPosition(0)
        return true
    }

    fun moveTo(target: Int): Boolean {
        if (target !in 0 until paginator.pageCount) return false
        onItemsChanged?.invoke(paginator.moveTo(target))
        updatePageBar()
        recyclerView.scrollToPosition(0)
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
        onItemsChanged?.invoke(paginator.resetPage())
        updatePageBar()
        recyclerView.scrollToPosition(0)
    }

    private fun calibrateIfNeeded() {
        if (calibrated) return
        recyclerView.post { doCalibrate() }
    }

    private fun doCalibrate() {
        if (calibrated) return
        val visibleHeight = recyclerView.height -
            recyclerView.paddingTop - recyclerView.paddingBottom
        if (visibleHeight <= 0 || recyclerView.childCount == 0) {
            retryCalibrate()
            return
        }
        // 以子项实际底边判断：RecyclerView 的 height 不含 divider / margin，
        // 只看 height 会把已经溢出屏幕的项也算进来
        var fitCount = 0
        for (i in 0 until recyclerView.childCount) {
            if (recyclerView.getChildAt(i).bottom > visibleHeight) break
            fitCount++
        }
        if (fitCount <= 0) {
            retryCalibrate()
            return
        }
        calibrated = true
        calibrateAttempts = 0
        val newSize = fitCount.coerceAtLeast(MIN_PAGE_SIZE)
        if (newSize != paginator.pageSize) {
            onItemsChanged?.invoke(paginator.setPageSize(newSize))
            updatePageBar()
        }
    }

    private fun retryCalibrate() {
        if (calibrateAttempts++ >= MAX_CALIBRATE_ATTEMPTS) return
        recyclerView.postDelayed({ doCalibrate() }, CALIBRATE_RETRY_DELAY_MS)
    }

    private fun updatePageBar() {
        pageBar.update(paginator.page, paginator.pageCount, paginator.total, itemName)
    }
}
