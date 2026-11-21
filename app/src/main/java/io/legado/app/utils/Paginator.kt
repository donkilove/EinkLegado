package io.legado.app.utils

/**
 * 列表分页助手。
 *
 * 电子纸上滚动会持续刷新并累积残影，长列表改为「每页 N 项 + 翻页」。
 * 只做切片，不持有业务含义；调用方保留全量数据，本类负责当前页与越界收敛。
 */
class Paginator<T>(val pageSize: Int = 8) {

    private var all: List<T> = emptyList()

    var page: Int = 0
        private set

    val total: Int get() = all.size

    val pageCount: Int
        get() = if (all.isEmpty()) 1 else (all.size + pageSize - 1) / pageSize

    val hasPrev: Boolean get() = page > 0
    val hasNext: Boolean get() = page < pageCount - 1

    /**
     * 全量数据变化：页数变少时收敛到最后一页，然后返回当前页切片
     */
    fun setAll(items: List<T>): List<T> {
        all = items
        page = page.coerceIn(0, pageCount - 1)
        return slice()
    }

    /**
     * 回到第一页并返回切片（搜索时使用）
     */
    fun resetPage(): List<T> {
        page = 0
        return slice()
    }

    fun moveTo(target: Int): List<T> {
        page = target.coerceIn(0, pageCount - 1)
        return slice()
    }

    fun slice(): List<T> {
        if (all.isEmpty()) return emptyList()
        val start = page * pageSize
        val end = minOf(start + pageSize, all.size)
        return all.subList(start, end)
    }
}
