package io.legado.app.help.sync

import android.content.Context
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.DbProvider
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.testing.TestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [UserDataSync.replaceLocal] 的执行层测试。
 *
 * 上游的决策（[SyncDecision]）与差异计算（[SyncPlan]）已有纯函数单测，
 * 但「计划如何落到数据库」这一段此前完全没有测试——而它恰恰是真正删数据的地方。
 * 这里用 Robolectric + 内存库覆盖它。
 *
 * 注意：测试会通过 [DbProvider.override] 把全局 `appDb` 换成内存库，
 * 每个用例结束后还原，避免影响其他测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class UserDataSyncDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        // 关掉 onOpen 的种数据，否则每次建库都会预置 6–7 个内置分组，
        // 断言「同步写入了什么」时还得把它们排除掉。
        AppDatabase.dbCallback.skipSeedData = true
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.dbCallback)
            .build()
        DbProvider.override(db)
    }

    @After
    fun tearDown() {
        DbProvider.reset()
        AppDatabase.dbCallback.skipSeedData = false
        db.close()
    }

    private fun group(id: Long, name: String = "g$id") = BookGroup(groupId = id, groupName = name)

    private fun bookmark(
        time: Long,
        chapterIndex: Int = 1,
        chapterPos: Int = 0
    ) = Bookmark(
        time = time,
        bookName = "书",
        bookAuthor = "作者",
        chapterIndex = chapterIndex,
        chapterPos = chapterPos
    )

    private fun source(url: String) = BookSource(bookSourceUrl = url, bookSourceName = url)

    // ---------------------------------------------------------------- 分组

    @Test
    fun `replaceLocal 会新增云端有而本地没有的分组`() {
        val remote = SyncData(groups = listOf(group(1), group(2)))

        val result = UserDataSync.replaceLocal(remote)

        assertEquals(2, result.groups)
        assertEquals(listOf(1L, 2L), db.bookGroupDao.all.map { it.groupId }.sorted())
    }

    @Test
    fun `replaceLocal 会删除本地有而云端没有的分组`() {
        db.bookGroupDao.insert(group(1), group(2), group(3))

        val result = UserDataSync.replaceLocal(SyncData(groups = listOf(group(1))))

        assertEquals(2, result.groups)
        assertEquals(listOf(1L), db.bookGroupDao.all.map { it.groupId })
    }

    @Test
    fun `replaceLocal 对分组可同时增删`() {
        db.bookGroupDao.insert(group(1), group(2))

        val result = UserDataSync.replaceLocal(SyncData(groups = listOf(group(2), group(3))))

        assertEquals(2, result.groups)
        assertEquals(listOf(2L, 3L), db.bookGroupDao.all.map { it.groupId }.sorted())
    }

    // ---------------------------------------------------------------- 书签

    @Test
    fun `replaceLocal 会新增云端有而本地没有的书签`() {
        val result = UserDataSync.replaceLocal(
            SyncData(bookmarks = listOf(bookmark(100, chapterIndex = 1)))
        )

        assertEquals(1, result.bookmarks)
        assertEquals(listOf(100L), db.bookmarkDao.all.map { it.time })
    }

    @Test
    fun `replaceLocal 会删除本地有而云端没有的书签`() {
        db.bookmarkDao.insert(bookmark(100, chapterIndex = 1), bookmark(200, chapterIndex = 2))

        val result = UserDataSync.replaceLocal(
            SyncData(bookmarks = listOf(bookmark(100, chapterIndex = 1)))
        )

        assertEquals(1, result.bookmarks)
        assertEquals(listOf(100L), db.bookmarkDao.all.map { it.time })
    }

    @Test
    fun `书签按四元组去重时不重复插入`() {
        // 本地已有同一位置的书签（时间戳不同），云端那份不应再插入
        db.bookmarkDao.insert(bookmark(time = 100, chapterIndex = 5, chapterPos = 10))

        val result = UserDataSync.replaceLocal(
            SyncData(bookmarks = listOf(bookmark(time = 999, chapterIndex = 5, chapterPos = 10)))
        )

        assertEquals("同一位置不该被当作新增", 0, result.bookmarks)
        assertEquals("数量不应变化", 1, db.bookmarkDao.all.size)
        assertEquals("原记录不该被删除", 100L, db.bookmarkDao.all.first().time)
    }

    // ---------------------------------------------------------------- 书源

    @Test
    fun `replaceLocal 会新增云端有而本地没有的书源`() {
        val result = UserDataSync.replaceLocal(
            SyncData(sources = listOf(source("https://a.com")))
        )

        assertEquals(1, result.sources)
        assertEquals(listOf("https://a.com"), db.bookSourceDao.all.map { it.bookSourceUrl })
    }

    @Test
    fun `replaceLocal 会删除本地有而云端没有的书源`() {
        db.bookSourceDao.insert(source("https://a.com"), source("https://b.com"))

        val result = UserDataSync.replaceLocal(
            SyncData(sources = listOf(source("https://a.com")))
        )

        assertEquals(1, result.sources)
        assertEquals(listOf("https://a.com"), db.bookSourceDao.all.map { it.bookSourceUrl })
    }

    @Test
    fun `云端书源为空时不删除本地书源`() {
        db.bookSourceDao.insert(source("https://a.com"), source("https://b.com"))

        val result = UserDataSync.replaceLocal(SyncData(sources = emptyList()))

        assertEquals("不该有任何书源变动", 0, result.sources)
        assertEquals("本地书源必须完好", 2, db.bookSourceDao.all.size)
    }

    @Test
    fun `云端书源为空时其他数据仍按计划处理`() {
        db.bookGroupDao.insert(group(1), group(2))
        db.bookSourceDao.insert(source("https://a.com"))

        val result = UserDataSync.replaceLocal(
            SyncData(groups = listOf(group(2)), sources = emptyList())
        )

        assertEquals("分组仍应被删", 1, result.groups)
        assertEquals(listOf(2L), db.bookGroupDao.all.map { it.groupId })
        assertEquals("书源仍应保留", 1, db.bookSourceDao.all.size)
    }

    // ---------------------------------------------------------------- 无副作用

    @Test
    fun `计划为空时不改动任何数据`() {
        db.bookGroupDao.insert(group(1))
        db.bookmarkDao.insert(bookmark(100, chapterIndex = 1))
        db.bookSourceDao.insert(source("https://a.com"))

        val remote = SyncData(
            groups = listOf(group(1)),
            bookmarks = listOf(bookmark(100, chapterIndex = 1)),
            sources = listOf(source("https://a.com"))
        )
        val result = UserDataSync.replaceLocal(remote)

        assertEquals(0, result.total)
        assertEquals(1, db.bookGroupDao.all.size)
        assertEquals(1, db.bookmarkDao.all.size)
        assertEquals(1, db.bookSourceDao.all.size)
    }

    @Test
    fun `云端为空且本地为空时不产生动作`() {
        val result = UserDataSync.replaceLocal(SyncData())

        assertEquals(0, result.total)
        assertTrue(db.bookGroupDao.all.isEmpty())
        assertTrue(db.bookmarkDao.all.isEmpty())
        assertTrue(db.bookSourceDao.all.isEmpty())
    }

    // ---------------------------------------------------------------- 计数语义

    @Test
    fun `result 计数反映实际发生变化的条目数`() {
        db.bookGroupDao.insert(group(1))

        val result = UserDataSync.replaceLocal(
            SyncData(
                groups = listOf(group(2), group(3)),
                bookmarks = listOf(bookmark(100, chapterIndex = 1)),
                sources = listOf(source("https://a.com"))
            )
        )

        // 删 1 个分组 + 加 2 个分组 + 加 1 条书签 + 加 1 个书源
        assertEquals(3, result.groups)
        assertEquals(1, result.bookmarks)
        assertEquals(1, result.sources)
        assertEquals(5, result.total)
    }

    @Test
    fun `被删除的分组确实从库中消失`() {
        db.bookGroupDao.insert(group(42, "要删的组"))

        UserDataSync.replaceLocal(SyncData(groups = emptyList()))

        assertNull(db.bookGroupDao.getByID(42))
    }
}
