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
    private val itemName: String = "条",
    /** 列表列数。>1 时（网格模式）每页必须是整行，条数按列数取整。 */
    private val columns: Int = 1,
    /**
     * 列表项高度是否一致。
     *
     * - false（默认）：逐页测量各自能放几条，适合项高不一的列表（如替换净化规则的摘要可能 1 或 2 行）
     * - true：只测一次，所有页统一条数，适合单行列表（目录、缓存）。
     *   此时页边界稳定，「第几页」含义确定，按下标跳页才准确；
     *   若项高其实不一致，请保持 false，否则会出现某页溢出。
     */
    private val uniformItemHeight: Boolean = false
) {

    companion object {
        /**
         * 未测量页的预估条数：先给一个偏大的值，让列表渲染到溢出屏幕，
         * 这样按子项底边统计才能得到真实容量。测量完会被实际值取代。
         */
        private const val INITIAL_PAGE_SIZE = 40

        private const val MAX_MEASURE_ATTEMPTS = 20
        private const val MEASURE_RETRY_DELAY_MS = 50L

        /** 连续测量轮数：取多轮最大值，规避首轮布局未稳定导致条数偏小 */
        private const val MEASURE_ROUNDS = 3
        private const val MEASURE_ROUND_DELAY_MS = 180L

        /** 数据落地并完成一次布局需要的时间 */
        private const val LAYOUT_SETTLE_DELAY_MS = 120L
    }

    private val paginator = Paginator<T>(INITIAL_PAGE_SIZE)
    private var measureAttempts = 0
    private var measuring = false

    /** 统一条数模式（[uniformItemHeight]）下是否已完成一次测量 */
    private var uniformMeasured = false

    /** 多轮测量中的最大值 */
    private var bestFit = 0

    /** 当前页数据变化通知，调用方在这里刷新 adapter */
    var onItemsChanged: ((List<T>) -> Unit)? = null

    /**
     * 首次完成每页条数测量后回调。
     *
     * 测量过程会把页码重置到第一页，需要「打开后定位到某个条目」的页面
     * （如目录定位到当前阅读章节）要等这个回调之后再做，否则会被测量结果覆盖。
     */
    var onCalibrated: (() -> Unit)? = null

    val page: Int get() = paginator.page
    val pageCount: Int get() = paginator.pageCount
    val total: Int get() = paginator.total

    /** 列数（网格模式下 >1） */
    val columnCount: Int get() = columns

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
        // 数据变了，统一条数模式需要重新测一次（列表可能整体换了，项高未必还一样）
        uniformMeasured = false
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

    /** 当前实际使用的 LayoutManager（分页器会把原本可滚动的换成不可滚动的那个） */
    val layoutManager: LinearLayoutManager?
        get() = recyclerView.layoutManager as? LinearLayoutManager

    /**
     * 跳到第 [itemIndex] 条数据所在的页。
     * 每页条数各不相同，不能再用「下标 / 每页条数」换算。
     */
    fun jumpToItem(itemIndex: Int): Boolean {
        val target = paginator.pageOfItem(itemIndex)
        return moveTo(target)
    }

    /** 第 [itemIndex] 条数据在当前页内的位置 */
    fun indexInPage(itemIndex: Int): Int =
        (itemIndex - paginator.pageStart(paginator.page)).coerceAtLeast(0)

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
        measureAttempts = 0
        bestFit = 0
        // 翻页栏在只有一页时是隐藏的，此时 RecyclerView 更高。
        // 按那个高度算出的条数，等翻页栏显示后就会放不下（书架的网格模式曾因此裁掉半行）。
        pageBar.visibility = View.VISIBLE
        scheduleMeasureRound(LAYOUT_SETTLE_DELAY_MS)
    }

    private fun scheduleMeasureRound(delay: Long) {
        recyclerView.postDelayed({ doMeasureRound() }, delay)
    }

    /**
     * 单轮测量，多轮取最大值。
     *
     * 首轮常常在布局稳定前就跑完（RecyclerView 高度或子项数量还没定稿），
     * 会得到明显偏小的条数——目录列表曾因此只算出 3 条，导致每页条数与页码和内容对不上。
     */
    private fun doMeasureRound() {
        val visibleHeight = recyclerView.height -
            recyclerView.paddingTop - recyclerView.paddingBottom
        var fit = 0
        if (visibleHeight > 0 && recyclerView.childCount > 0) {
            for (i in 0 until recyclerView.childCount) {
                if (recyclerView.getChildAt(i).bottom > visibleHeight) break
                fit++
            }
        }
        if (fit > bestFit) bestFit = fit
        measureAttempts++
        if (measureAttempts < MEASURE_ROUNDS) {
            scheduleMeasureRound(MEASURE_ROUND_DELAY_MS)
            return
        }
        if (bestFit <= 0) {
            measuring = false
            return
        }
        commitMeasured(bestFit)
    }

    private fun commitMeasured(fitCount: Int) {
        measureAttempts = 0
        // 网格模式下每页必须是整行，按列数向下取整
        val perPage = if (columns > 1) {
            (fitCount / columns).coerceAtLeast(1) * columns
        } else {
            fitCount
        }
        if (uniformItemHeight) {
            // 项高一致：一次测量后所有页统一，页边界因此稳定。
            // 已测量过就不再重设，否则每次翻页都会把页码重置回第一页。
            if (uniformMeasured) {
                measuring = false
                return
            }
            uniformMeasured = true
            paginator.setUniformPageSize(perPage)
            onItemsChanged?.invoke(paginator.slice())
            updatePageBar()
            measuring = false
            onCalibrated?.invoke()
            return
        }
        val changed = paginator.commitPageSize(perPage)
        // 用实测条数作为后续未测量页的估算基准，否则那些页仍按初始的 40 条估，
        // 页数会随翻页一页页往上跳（1/2、2/3、3/4…），看起来像算错了
        paginator.setDefaultPageSize(perPage)
        if (changed) {
            // 本页条数变了，页边界随之变化，重新切片
            onItemsChanged?.invoke(paginator.slice())
            updatePageBar()
            recyclerView.postDelayed({
                measuring = false
                measureAttempts = 0
                bestFit = 0
                // 重新切片后可能又有一轮布局，再确认一次（commitPageSize 幂等，不会死循环）
                scheduleMeasureRound(0)
            }, LAYOUT_SETTLE_DELAY_MS)
        } else {
            measuring = false
        }
    }

    private fun updatePageBar() {
        pageBar.update(paginator.page, paginator.pageCount, paginator.total, itemName)
    }
}
