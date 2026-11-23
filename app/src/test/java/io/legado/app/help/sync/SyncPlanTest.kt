package io.legado.app.help.sync

import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SyncPlan] 的差异计算测试。
 *
 * replaceLocal 的语义是「以云端为准」：删掉本地有而云端没有的，补上云端有而本地没有的。
 * 算错方向就是删用户的组 / 丢用户的书签，所以每个方向都要单独固定。
 *
 * 书源有一条额外规则：云端书源为空时整体跳过书源部分，避免用空列表冲掉本地资产。
 */
class SyncPlanTest {

    private fun group(id: Long, name: String = "g$id") = BookGroup(groupId = id, groupName = name)

    private fun bookmark(
        time: Long,
        book: String = "书",
        author: String = "作者",
        chapterIndex: Int = 0,
        chapterPos: Int = 0
    ) = Bookmark(
        time = time,
        bookName = book,
        bookAuthor = author,
        chapterIndex = chapterIndex,
        chapterPos = chapterPos
    )

    private fun source(url: String) = BookSource(bookSourceUrl = url, bookSourceName = url)

    // ---------------------------------------------------------------- 全空

    @Test
    fun `两端都为空时计划为空`() {
        val plan = SyncPlan.planReplace(SyncData(), SyncData())

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `两端完全一致时计划为空`() {
        val data = SyncData(
            groups = listOf(group(1), group(2)),
            bookmarks = listOf(bookmark(100), bookmark(200)),
            sources = listOf(source("https://a.com"))
        )

        assertTrue(SyncPlan.planReplace(data, data.copy()).isEmpty())
    }

    // ---------------------------------------------------------------- 分组

    @Test
    fun `云端多出的分组被补进本地`() {
        val plan = SyncPlan.planReplace(
            local = SyncData(groups = listOf(group(1))),
            remote = SyncData(groups = listOf(group(1), group(2)))
        )

        assertEquals(listOf(2L), plan.groupsToInsert.map { it.groupId })
        assertTrue(plan.groupsToDelete.isEmpty())
    }

    @Test
    fun `本地多出的分组被删除`() {
        val plan = SyncPlan.planReplace(
            local = SyncData(groups = listOf(group(1), group(2))),
            remote = SyncData(groups = listOf(group(1)))
        )

        assertEquals(listOf(2L), plan.groupsToDelete)
        assertTrue(plan.groupsToInsert.isEmpty())
    }

    @Test
    fun `分组可同时增删`() {
        val plan = SyncPlan.planReplace(
            local = SyncData(groups = listOf(group(1), group(2))),
            remote = SyncData(groups = listOf(group(2), group(3)))
        )

        assertEquals(listOf(1L), plan.groupsToDelete)
        assertEquals(listOf(3L), plan.groupsToInsert.map { it.groupId })
    }

    // ---------------------------------------------------------------- 书签

    @Test
    fun `云端多出的书签被补进本地`() {
        val plan = SyncPlan.planReplace(
            local = SyncData(bookmarks = listOf(bookmark(100, chapterIndex = 1))),
            remote = SyncData(bookmarks = listOf(bookmark(100, chapterIndex = 1), bookmark(200, chapterIndex = 2)))
        )

        assertEquals(listOf(200L), plan.bookmarksToInsert.map { it.time })
        assertTrue(plan.bookmarksToDelete.isEmpty())
    }

    @Test
    fun `本地多出的书签被删除`() {
        val plan = SyncPlan.planReplace(
            local = SyncData(bookmarks = listOf(bookmark(100, chapterIndex = 1), bookmark(200, chapterIndex = 2))),
            remote = SyncData(bookmarks = listOf(bookmark(100, chapterIndex = 1)))
        )

        assertEquals(listOf(200L), plan.bookmarksToDelete.map { it.time })
        assertTrue(plan.bookmarksToInsert.isEmpty())
    }

    @Test
    fun `书签按书名作者章节位置去重而不是按主键时间`() {
        // 同一处书签在两台设备上时间戳不同，但语义相同 → 不应重复插入
        val local = SyncData(bookmarks = listOf(bookmark(time = 100, chapterIndex = 5, chapterPos = 10)))
        val remote = SyncData(bookmarks = listOf(bookmark(time = 999, chapterIndex = 5, chapterPos = 10)))

        val plan = SyncPlan.planReplace(local, remote)

        assertTrue("同一位置不该重复插入", plan.bookmarksToInsert.isEmpty())
        assertTrue("同一位置不该被删除", plan.bookmarksToDelete.isEmpty())
    }

    @Test
    fun `同一本书不同章节的书签视为不同`() {
        val local = SyncData(bookmarks = listOf(bookmark(time = 100, chapterIndex = 5, chapterPos = 10)))
        val remote = SyncData(bookmarks = listOf(bookmark(time = 100, chapterIndex = 6, chapterPos = 10)))

        val plan = SyncPlan.planReplace(local, remote)

        assertEquals(1, plan.bookmarksToInsert.size)
        assertEquals(1, plan.bookmarksToDelete.size)
    }

    @Test
    fun `同一章节不同位置的书签视为不同`() {
        val local = SyncData(bookmarks = listOf(bookmark(time = 100, chapterIndex = 5, chapterPos = 10)))
        val remote = SyncData(bookmarks = listOf(bookmark(time = 100, chapterIndex = 5, chapterPos = 20)))

        val plan = SyncPlan.planReplace(local, remote)

        assertEquals(1, plan.bookmarksToInsert.size)
        assertEquals(1, plan.bookmarksToDelete.size)
    }

    // ---------------------------------------------------------------- 书源

    @Test
    fun `云端多出的书源被补进本地`() {
        val plan = SyncPlan.planReplace(
            local = SyncData(sources = listOf(source("https://a.com"))),
            remote = SyncData(sources = listOf(source("https://a.com"), source("https://b.com")))
        )

        assertEquals(listOf("https://b.com"), plan.sourcesToInsert.map { it.bookSourceUrl })
        assertTrue(plan.sourcesToDelete.isEmpty())
    }

    @Test
    fun `本地多出的书源被删除`() {
        val plan = SyncPlan.planReplace(
            local = SyncData(sources = listOf(source("https://a.com"), source("https://b.com"))),
            remote = SyncData(sources = listOf(source("https://a.com")))
        )

        assertEquals(listOf("https://b.com"), plan.sourcesToDelete)
        assertTrue(plan.sourcesToInsert.isEmpty())
    }

    @Test
    fun `云端书源为空时整个书源部分被跳过`() {
        // 保护：不能用空列表冲掉本地书源
        val plan = SyncPlan.planReplace(
            local = SyncData(sources = listOf(source("https://a.com"))),
            remote = SyncData(sources = emptyList())
        )

        assertTrue("不该删除本地书源", plan.sourcesToDelete.isEmpty())
        assertTrue(plan.sourcesToInsert.isEmpty())
    }

    @Test
    fun `两端书源都为空时书源部分也无动作`() {
        val plan = SyncPlan.planReplace(
            local = SyncData(sources = emptyList()),
            remote = SyncData(sources = emptyList())
        )

        assertTrue(plan.sourcesToDelete.isEmpty())
        assertTrue(plan.sourcesToInsert.isEmpty())
    }

    @Test
    fun `书源保护不影响分组与书签的处理`() {
        val plan = SyncPlan.planReplace(
            local = SyncData(
                groups = listOf(group(1), group(2)),
                bookmarks = listOf(bookmark(100, chapterIndex = 1)),
                sources = listOf(source("https://a.com"))
            ),
            remote = SyncData(
                groups = listOf(group(2)),
                bookmarks = listOf(bookmark(100, chapterIndex = 1), bookmark(200, chapterIndex = 2)),
                sources = emptyList()
            )
        )

        assertEquals("分组仍应删除", listOf(1L), plan.groupsToDelete)
        assertEquals("书签仍应插入", 1, plan.bookmarksToInsert.size)
        assertTrue("但书源不该被删", plan.sourcesToDelete.isEmpty())
    }

    // ---------------------------------------------------------------- 混合

    @Test
    fun `三类数据可同时增删`() {
        val plan = SyncPlan.planReplace(
            local = SyncData(
                groups = listOf(group(1)),
                bookmarks = listOf(bookmark(100, chapterIndex = 1)),
                sources = listOf(source("https://a.com"))
            ),
            remote = SyncData(
                groups = listOf(group(2)),
                bookmarks = listOf(bookmark(200, chapterIndex = 2)),
                sources = listOf(source("https://b.com"))
            )
        )

        assertEquals(listOf(1L), plan.groupsToDelete)
        assertEquals(listOf(2L), plan.groupsToInsert.map { it.groupId })
        assertEquals(1, plan.bookmarksToDelete.size)
        assertEquals(1, plan.bookmarksToInsert.size)
        assertEquals(listOf("https://a.com"), plan.sourcesToDelete)
        assertEquals(listOf("https://b.com"), plan.sourcesToInsert.map { it.bookSourceUrl })
        assertFalse(plan.isEmpty())
    }

    @Test
    fun `计划不修改传入的数据`() {
        val localGroups = listOf(group(1), group(2))
        val local = SyncData(groups = localGroups)
        val remote = SyncData(groups = listOf(group(2)))

        SyncPlan.planReplace(local, remote)

        assertEquals("计算计划不应改动调用方数据", 2, local.groups.size)
        assertEquals(listOf(1L, 2L), localGroups.map { it.groupId })
    }
}
