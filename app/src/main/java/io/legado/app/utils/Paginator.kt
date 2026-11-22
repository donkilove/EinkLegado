package io.legado.app.utils

/**
 * 列表分页助手（支持每页条数各不相同）。
 *
 * 电子纸上滚动会持续刷新并累积残影，长列表改为「每页 N 项 + 翻页」。
 * 但列表项高度并不一致（同一条规则可能一行摘要也可能两行），
 * 用一个固定的「每页条数」必然出问题：
 * - 按最矮的项定条数：较高的页会溢出，而滚动已禁用，溢出的条目既看不到也划不到
 * - 按最高的项定条数：较矮的页会空出一大块
 *
 * 所以这里按页记录实际条数：每页渲染完由调用方回填真实能放下的条数（[commitPageSize]），
 * 还没渲染过的页先用 [defaultPageSize] 估。页边界因此随内容自然变化，既不溢出也不留大片空白。
 *
 * 本类只做切片，不持有业务含义。
 */
class Paginator<T>(defaultPageSize: Int = DEFAULT_PAGE_SIZE) {

    companion object {
        /** 未测量的页使用的初始条数 */
        private const val DEFAULT_PAGE_SIZE = 8

        /** 单页最少条数，避免项特别高时每页只剩一两条 */
        private const val MIN_PAGE_SIZE = 3
    }

    private var defaultPageSize: Int = defaultPageSize

    private var all: List<T> = emptyList()

    /** 已渲染过的页各自的实际条数；未测量的页用 [defaultPageSize] */
    private val measuredSizes = mutableListOf<Int>()

    var page: Int = 0
        private set

    val total: Int get() = all.size

    private fun sizeOf(pageIndex: Int): Int =
        measuredSizes.getOrNull(pageIndex) ?: defaultPageSize

    /** 第 pageIndex 页的起始下标（按累计已定页码求和） */
    private fun startOf(pageIndex: Int): Int {
        var start = 0
        for (i in 0 until pageIndex) {
            if (start >= all.size) return start
            start += sizeOf(i).coerceAtLeast(1)
        }
        return start
    }

    val pageCount: Int
        get() {
            if (all.isEmpty()) return 1
            var idx = 0
            var count = 1
            while (true) {
                idx += sizeOf(count - 1).coerceAtLeast(1)
                if (idx >= all.size) return count
                count++
            }
        }

    /** 全量数据变化：丢弃已测量的页边界，回到第一页 */
    fun setAll(items: List<T>): List<T> {
        all = items
        measuredSizes.clear()
        page = 0
        return slice()
    }

    /**
     * 回填当前页实际放得下的条数。渲染完成、测量出结果后调用。
     * 与已有值相同则什么都不做，避免反复重排。
     */
    fun commitPageSize(actual: Int): Boolean {
        val value = actual.coerceAtLeast(MIN_PAGE_SIZE)
        while (measuredSizes.size <= page) {
            measuredSizes.add(defaultPageSize)
        }
        if (measuredSizes[page] == value) return false
        measuredSizes[page] = value
        // 收紧后页数可能变少，把页码收回范围内
        page = page.coerceIn(0, pageCount - 1)
        return true
    }

    /** 设置未测量页的预估条数（首次校准用） */
    fun setDefaultPageSize(size: Int) {
        defaultPageSize = size.coerceAtLeast(MIN_PAGE_SIZE)
    }

    /**
     * 所有页统一用 [size] 条，并丢弃已测量的各页边界。
     *
     * 列表项高度一致时（目录、缓存这类单行列表）用这个：页边界稳定，
     * 「第几页」才是个确定的含义，按下标跳页/定位才不会错位。
     * 反过来，项高不一致的列表（如同一规则有 1 或 2 行摘要）必须逐页测量，
     * 否则总会有页溢出或留大片空白。
     */
    fun setUniformPageSize(size: Int) {
        defaultPageSize = size.coerceAtLeast(MIN_PAGE_SIZE)
        measuredSizes.clear()
        page = 0
    }

    fun moveTo(target: Int): List<T> {
        page = target.coerceIn(0, pageCount - 1)
        return slice()
    }

    fun moveBy(delta: Int): List<T>? {
        val target = page + delta
        if (target !in 0 until pageCount) return null
        page = target
        return slice()
    }

    /**
     * 找出第 [itemIndex] 条数据所在页。
     * 每页条数各不相同，无法用「下标 / 每页条数」直接算，只能按累计边界逐个走。
     */
    fun pageOfItem(itemIndex: Int): Int {
        if (itemIndex <= 0) return 0
        var start = 0
        var p = 0
        val count = pageCount
        while (p < count) {
            val size = sizeOf(p).coerceAtLeast(1)
            if (itemIndex < start + size) return p
            start += size
            p++
        }
        return (count - 1).coerceAtLeast(0)
    }

    /** 第 [pageIndex] 页的起始下标 */
    fun pageStart(pageIndex: Int): Int = startOf(pageIndex)

    fun slice(): List<T> {
        if (all.isEmpty()) return emptyList()
        val start = startOf(page)
        if (start >= all.size) return emptyList()
        val end = minOf(start + sizeOf(page).coerceAtLeast(1), all.size)
        return all.subList(start, end)
    }
}
