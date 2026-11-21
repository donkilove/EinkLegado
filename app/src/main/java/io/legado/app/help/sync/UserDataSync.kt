package io.legado.app.help.sync

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark

/**
 * 用户数据同步快照：书架分组、书签、书源。
 *
 * 与阅读进度不同，这类数据是「资产」性质（尤其书源），所以合并策略是**只增不删**：
 * 云端有本地没有的条目会被补进来，但任何一端都不会因为另一端的缺失而删除本地数据。
 */
data class SyncData(
    val version: Int = 1,
    val groups: List<BookGroup> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val sources: List<BookSource> = emptyList()
) {
    fun isEmpty(): Boolean = groups.isEmpty() && bookmarks.isEmpty() && sources.isEmpty()
}

/**
 * 合并云端同步数据到本地，并导出本地全量用于回写云端。
 */
object UserDataSync {

    data class Result(
        val groups: Int = 0,
        val bookmarks: Int = 0,
        val sources: Int = 0
    ) {
        val total: Int get() = groups + bookmarks + sources
    }

    /**
     * 把云端数据并入本地：只插入本地没有的条目
     */
    fun merge(remote: SyncData): Result {
        var groupCount = 0
        var bookmarkCount = 0
        var sourceCount = 0

        runCatching {
            val localIds = appDb.bookGroupDao.all.map { it.groupId }.toSet()
            remote.groups.filter { it.groupId !in localIds }.forEach {
                appDb.bookGroupDao.insert(it)
                groupCount++
            }
        }

        runCatching {
            val localKeys = appDb.bookmarkDao.all.map { bookmarkKey(it) }.toSet()
            remote.bookmarks.filter { bookmarkKey(it) !in localKeys }.forEach {
                appDb.bookmarkDao.insert(it)
                bookmarkCount++
            }
        }

        runCatching {
            val localUrls = appDb.bookSourceDao.all.map { it.bookSourceUrl }.toSet()
            remote.sources.filter { it.bookSourceUrl !in localUrls }.forEach {
                appDb.bookSourceDao.insert(it)
                sourceCount++
            }
        }

        return Result(groupCount, bookmarkCount, sourceCount)
    }

    /**
     * 导出本地全量数据
     */
    fun export(): SyncData = SyncData(
        groups = appDb.bookGroupDao.all,
        bookmarks = appDb.bookmarkDao.all,
        sources = appDb.bookSourceDao.all
    )

    private fun bookmarkKey(bookmark: Bookmark): String =
        "${bookmark.bookName}|${bookmark.bookAuthor}|${bookmark.chapterIndex}|${bookmark.chapterPos}"
}
