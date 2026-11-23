package io.legado.app.help.sync

import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark

/**
 * 用云端数据覆盖本地时，具体要做哪些动作。
 *
 * 只描述「该删什么、该加什么」，不执行。这样差异计算可以脱离数据库单独验证，
 * 而 [io.legado.app.help.sync.UserDataSync.replaceLocal] 只负责把计划翻译成 DAO 调用。
 *
 * 语义是**以云端为准**：本地有而云端没有的删掉，云端有而本地没有的补上。
 * 算错方向就是删掉用户的分组、丢掉用户的标签，所以每个方向都被单独固定。
 */
data class SyncPlan(
    val groupsToDelete: List<Long> = emptyList(),
    val groupsToInsert: List<BookGroup> = emptyList(),
    val bookmarksToDelete: List<Bookmark> = emptyList(),
    val bookmarksToInsert: List<Bookmark> = emptyList(),
    val sourcesToDelete: List<String> = emptyList(),
    val sourcesToInsert: List<BookSource> = emptyList()
) {

    /** 没有任何动作可做 */
    fun isEmpty(): Boolean = groupsToDelete.isEmpty()
            && groupsToInsert.isEmpty()
            && bookmarksToDelete.isEmpty()
            && bookmarksToInsert.isEmpty()
            && sourcesToDelete.isEmpty()
            && sourcesToInsert.isEmpty()

    companion object {

        /**
         * 计算「以云端为准」所需的动作。
         *
         * 书源有一条额外保护：云端书源列表为空时，整体跳过书源部分，
         * 避免首次安装或数据损坏时用空列表冲掉本地书源资产。
         * 该保护**只影响书源**，分组与书签照常处理。
         */
        fun planReplace(local: SyncData, remote: SyncData): SyncPlan {
            // 分组：按 groupId 判断增删
            val localGroupIds = local.groups.mapTo(HashSet()) { it.groupId }
            val remoteGroupIds = remote.groups.mapTo(HashSet()) { it.groupId }
            val groupsToDelete = local.groups.filterNot { it.groupId in remoteGroupIds }
            val groupsToInsert = remote.groups.filterNot { it.groupId in localGroupIds }

            // 书签：按「书名|作者|章|位置」判断增删。
            // 不能用主键 time——同一处标签在两台设备上的创建时间不同，
            // 若按 time 比较会把它们当成两条不同的书签，导致重复。
            val localBookmarkKeys = local.bookmarks.mapTo(HashSet()) { bookmarkKey(it) }
            val remoteBookmarkKeys = remote.bookmarks.mapTo(HashSet()) { bookmarkKey(it) }
            val bookmarksToDelete = local.bookmarks.filterNot { bookmarkKey(it) in remoteBookmarkKeys }
            val bookmarksToInsert = remote.bookmarks.filterNot { bookmarkKey(it) in localBookmarkKeys }

            // 书源：云端为空时整体跳过（保护）
            val sourcesToDelete: List<String>
            val sourcesToInsert: List<BookSource>
            if (remote.sources.isEmpty()) {
                sourcesToDelete = emptyList()
                sourcesToInsert = emptyList()
            } else {
                val localSourceUrls = local.sources.mapTo(HashSet()) { it.bookSourceUrl }
                val remoteSourceUrls = remote.sources.mapTo(HashSet()) { it.bookSourceUrl }
                sourcesToDelete = local.sources.filterNot { it.bookSourceUrl in remoteSourceUrls }
                    .map { it.bookSourceUrl }
                sourcesToInsert = remote.sources.filterNot { it.bookSourceUrl in localSourceUrls }
            }

            return SyncPlan(
                groupsToDelete = groupsToDelete.map { it.groupId },
                groupsToInsert = groupsToInsert,
                bookmarksToDelete = bookmarksToDelete,
                bookmarksToInsert = bookmarksToInsert,
                sourcesToDelete = sourcesToDelete,
                sourcesToInsert = sourcesToInsert
            )
        }

        /**
         * 书签的去重键。
         *
         * 不用主键 `time`，因为同一处书签在不同设备上的创建时间不同；
         * 用「书名 + 作者 + 章节 + 章节内位置」才能在语义层面认出「这是同一条」。
         */
        fun bookmarkKey(bookmark: Bookmark): String =
            "${bookmark.bookName}|${bookmark.bookAuthor}|${bookmark.chapterIndex}|${bookmark.chapterPos}"
    }
}
