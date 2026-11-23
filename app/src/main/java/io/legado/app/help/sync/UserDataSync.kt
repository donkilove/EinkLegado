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
     *
     * 差异计算在 [SyncPlan.planReplace]（纯函数，可单测），此处只负责把计划翻译成 DAO 调用。
     * @return 发生变化的条目数
     */
    fun replaceLocal(remote: SyncData): Result {
        val local = export()
        val plan = SyncPlan.planReplace(local, remote)
        var groupCount = 0
        var bookmarkCount = 0
        var sourceCount = 0

        runCatching {
            plan.groupsToDelete.forEach { groupId ->
                local.groups.firstOrNull { it.groupId == groupId }?.let {
                    appDb.bookGroupDao.delete(it)
                    groupCount++
                }
            }
            plan.groupsToInsert.forEach {
                appDb.bookGroupDao.insert(it)
                groupCount++
            }
        }

        runCatching {
            val localByKey = local.bookmarks.associateBy { SyncPlan.bookmarkKey(it) }
            plan.bookmarksToDelete.forEach { bookmark ->
                localByKey[SyncPlan.bookmarkKey(bookmark)]?.let {
                    appDb.bookmarkDao.delete(it)
                    bookmarkCount++
                }
            }
            plan.bookmarksToInsert.forEach {
                appDb.bookmarkDao.insert(it)
                bookmarkCount++
            }
        }

        runCatching {
            val localByUrl = local.sources.associateBy { it.bookSourceUrl }
            plan.sourcesToDelete.forEach { url ->
                localByUrl[url]?.let {
                    appDb.bookSourceDao.delete(it)
                    sourceCount++
                }
            }
            plan.sourcesToInsert.forEach {
                appDb.bookSourceDao.insert(it)
                sourceCount++
            }
        }

        return Result(groupCount, bookmarkCount, sourceCount)
    }
}
