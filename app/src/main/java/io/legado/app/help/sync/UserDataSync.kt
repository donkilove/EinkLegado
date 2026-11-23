package io.legado.app.help.sync

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark

/**
 * 用户数据同步快照：书架分组、书签、书源。
 *
 * 合并语义是「最后修改者胜」：靠 [SyncState] 里记录的指纹判断哪一端自上次同步后改动过，
 * 由改动过的一端覆盖另一端。这样用户在两台设备上新增或删除都能正确传播。
 *
 * 唯一的例外是书源：若一侧书源列表为空而另一侧有数据，视为异常（如首次安装、数据损坏），
 * 不会用空列表去覆盖对方的书源。
 */
data class SyncData(
    val version: Int = 2,
    val groups: List<BookGroup> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val sources: List<BookSource> = emptyList()
) {
    fun isEmpty(): Boolean = groups.isEmpty() && bookmarks.isEmpty() && sources.isEmpty()

    /**
     * 规范化顺序：保证同一份数据无论插入先后，算出的指纹一致
     */
    fun normalized(): SyncData = copy(
        groups = groups.sortedBy { it.groupId },
        bookmarks = bookmarks.sortedBy { it.time },
        sources = sources.sortedBy { it.bookSourceUrl }
    )
}

object UserDataSync {

    data class Result(
        val groups: Int = 0,
        val bookmarks: Int = 0,
        val sources: Int = 0
    ) {
        val total: Int get() = groups + bookmarks + sources
    }

    /**
     * 导出本地全量数据
     */
    fun export(): SyncData = SyncData(
        groups = appDb.bookGroupDao.all,
        bookmarks = appDb.bookmarkDao.all,
        sources = appDb.bookSourceDao.all
    )

    /**
     * 数据指纹：用于判断本地自上次同步后是否改动过。
     *
     * 实现在 [SyncFingerprint]，此处保留方法名以兼容既有调用点。
     */
    fun fingerprint(data: SyncData): String = SyncFingerprint.of(data)

    /**
     * 用云端数据整体覆盖本地：既补上云端新增，也删除云端已删的条目
     * @return 发生变化的条目数
     */
    fun replaceLocal(remote: SyncData): Result {
        var groupCount = 0
        var bookmarkCount = 0
        var sourceCount = 0

        runCatching {
            val remoteIds = remote.groups.map { it.groupId }.toSet()
            appDb.bookGroupDao.all.filter { it.groupId !in remoteIds }.forEach {
                appDb.bookGroupDao.delete(it)
                groupCount++
            }
            val localIds = appDb.bookGroupDao.all.map { it.groupId }.toSet()
            remote.groups.filter { it.groupId !in localIds }.forEach {
                appDb.bookGroupDao.insert(it)
                groupCount++
            }
        }

        runCatching {
            val remoteKeys = remote.bookmarks.map { bookmarkKey(it) }.toSet()
            appDb.bookmarkDao.all.filter { bookmarkKey(it) !in remoteKeys }.forEach {
                appDb.bookmarkDao.delete(it)
                bookmarkCount++
            }
            val localKeys = appDb.bookmarkDao.all.map { bookmarkKey(it) }.toSet()
            remote.bookmarks.filter { bookmarkKey(it) !in localKeys }.forEach {
                appDb.bookmarkDao.insert(it)
                bookmarkCount++
            }
        }

        runCatching {
            // 书源保护：云端没有书源时不动本地的
            if (remote.sources.isNotEmpty()) {
                val remoteUrls = remote.sources.map { it.bookSourceUrl }.toSet()
                appDb.bookSourceDao.all.filter { it.bookSourceUrl !in remoteUrls }.forEach {
                    appDb.bookSourceDao.delete(it)
                    sourceCount++
                }
                val localUrls = appDb.bookSourceDao.all.map { it.bookSourceUrl }.toSet()
                remote.sources.filter { it.bookSourceUrl !in localUrls }.forEach {
                    appDb.bookSourceDao.insert(it)
                    sourceCount++
                }
            }
        }

        return Result(groupCount, bookmarkCount, sourceCount)
    }

    private fun bookmarkKey(bookmark: Bookmark): String =
        "${bookmark.bookName}|${bookmark.bookAuthor}|${bookmark.chapterIndex}|${bookmark.chapterPos}"
}
