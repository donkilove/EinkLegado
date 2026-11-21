package io.legado.app.data.entities

/**
 * 所有书籍阅读进度的聚合快照。
 *
 * 旧方案是「每本书一个 json 文件」，打开 App 全量对齐时要发 N 次请求；
 * 改为单文件聚合后，一次下载即可拿到所有书的进度。
 *
 * [books] 的 key 形如 `书名_作者`（见 [progressKey]）。
 */
data class BookProgressBundle(
    val version: Int = CURRENT_VERSION,
    val books: MutableMap<String, BookProgress> = linkedMapOf()
) {

    companion object {
        const val CURRENT_VERSION = 2

        fun progressKey(name: String, author: String): String = "${name}_${author}"
    }

    /**
     * 合并一条远端进度：只有远端进度更靠后时返回 true 并写入
     */
    fun merge(remote: BookProgress): Boolean {
        val key = progressKey(remote.name, remote.author)
        val local = books[key]
        if (local == null || remote.isAheadOf(local)) {
            books[key] = remote
            return true
        }
        return false
    }

    fun put(progress: BookProgress) {
        books[progressKey(progress.name, progress.author)] = progress
    }

    fun get(name: String, author: String): BookProgress? =
        books[progressKey(name, author)]

    /**
     * 与本地图书记录比较后写回：只有远端更靠后才更新
     * @return 是否发生了更新
     */
    fun applyTo(book: Book): Boolean {
        val remote = get(book.name, book.author) ?: return false
        if (!remote.isAheadOf(book)) return false
        book.durChapterIndex = remote.durChapterIndex
        book.durChapterPos = remote.durChapterPos
        book.durChapterTitle = remote.durChapterTitle
        book.durChapterTime = remote.durChapterTime
        return true
    }
}

/**
 * 进度是否比这本书当前的记录更靠后（章节索引优先，其次章节内位置）
 */
fun BookProgress.isAheadOf(book: Book): Boolean =
    durChapterIndex > book.durChapterIndex ||
            (durChapterIndex == book.durChapterIndex && durChapterPos > book.durChapterPos)

fun BookProgress.isAheadOf(other: BookProgress): Boolean =
    durChapterIndex > other.durChapterIndex ||
            (durChapterIndex == other.durChapterIndex && durChapterPos > other.durChapterPos)
