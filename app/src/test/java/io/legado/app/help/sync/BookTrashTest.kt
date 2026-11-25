package io.legado.app.help.sync

import android.content.Context
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.DbProvider
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookTrash
import io.legado.app.testing.TestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 回收站策略与落库测试。
 *
 * 书架删除改为「先进回收站、30 天后才物理删除」，理由：
 * 删除要跨设备传播，而 `Book.delete()` 会连本地文件一起删，误删不可逆。
 * 回收站把「传播删除」与「销毁数据」拆成两个时刻。
 *
 * 这里固定三件事：
 * 1. 到期判定是纯函数，边界（第 29 天 / 第 30 天 / 第 31 天）必须精确；
 * 2. 移入回收站后，书籍从书架消失但记录仍在；
 * 3. 恢复后书籍回到书架，且进度字段不丢。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class BookTrashTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
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

    private fun book(
        url: String = "https://a.com/book",
        name: String = "书",
        author: String = "作者",
        chapter: Int = 3,
        pos: Int = 120
    ) = Book(
        bookUrl = url,
        name = name,
        author = author,
        durChapterIndex = chapter,
        durChapterPos = pos,
        durChapterTitle = "第${chapter}章"
    )

    // ---------------------------------------------------------------- 到期策略（纯函数）

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `未满 30 天不过期`() {
        val now = 1_000_000_000_000L
        assertFalse(BookTrashPolicy.isExpired(now - 29 * day, now))
    }

    @Test
    fun `正好 30 天不过期`() {
        val now = 1_000_000_000_000L
        // 边界取「严格大于」：第 30 天当天仍可恢复，第 31 天才清
        assertFalse(BookTrashPolicy.isExpired(now - 30 * day, now))
    }

    @Test
    fun `超过 30 天过期`() {
        val now = 1_000_000_000_000L
        assertTrue(BookTrashPolicy.isExpired(now - 31 * day, now))
    }

    @Test
    fun `筛选过期条目只挑超期的那几条`() {
        val now = 1_000_000_000_000L
        val items = listOf(
            BookTrash(bookUrl = "a", name = "a", author = "x", deletedAt = now - 40 * day),
            BookTrash(bookUrl = "b", name = "b", author = "x", deletedAt = now - 10 * day),
            BookTrash(bookUrl = "c", name = "c", author = "x", deletedAt = now - 31 * day)
        )

        val expired = BookTrashPolicy.expired(items, now)

        assertEquals(listOf("a", "c"), expired.map { it.bookUrl })
    }

    @Test
    fun `剩余天数计算`() {
        val now = 1_000_000_000_000L
        assertEquals(30L, BookTrashPolicy.remainingDays(now, now))
        assertEquals(20L, BookTrashPolicy.remainingDays(now - 10 * day, now))
        assertEquals(0L, BookTrashPolicy.remainingDays(now - 40 * day, now))
    }

    // ---------------------------------------------------------------- 落库

    @Test
    fun `移入回收站后书架不再有这本书 但记录仍在`() {
        val b = book()
        db.bookDao.insert(b)

        BookTrashHelper.moveToTrash(b)

        assertNull("书架里应当查不到", db.bookDao.getBook(b.bookUrl))
        val trash = db.bookTrashDao.all
        assertEquals(1, trash.size)
        assertEquals(b.bookUrl, trash[0].bookUrl)
        assertEquals(b.name, trash[0].name)
        assertEquals(b.author, trash[0].author)
    }

    @Test
    fun `移入回收站保留阅读进度`() {
        val b = book(chapter = 42, pos = 999)
        db.bookDao.insert(b)

        BookTrashHelper.moveToTrash(b)

        val trash = db.bookTrashDao.all.single()
        assertEquals(42, trash.durChapterIndex)
        assertEquals(999, trash.durChapterPos)
        assertEquals("第42章", trash.durChapterTitle)
    }

    @Test
    fun `恢复后书回到书架且进度不丢`() {
        val b = book(chapter = 42, pos = 999)
        db.bookDao.insert(b)
        BookTrashHelper.moveToTrash(b)

        BookTrashHelper.restore(db.bookTrashDao.all.single())

        val restored = db.bookDao.getBook(b.bookUrl)
        assertNotNull("恢复后应当回到书架", restored)
        assertEquals(42, restored!!.durChapterIndex)
        assertEquals(999, restored.durChapterPos)
        assertTrue("回收站应当清空", db.bookTrashDao.all.isEmpty())
    }

    @Test
    fun `彻底删除会移出回收站`() {
        val b = book()
        db.bookDao.insert(b)
        BookTrashHelper.moveToTrash(b)

        BookTrashHelper.purge(db.bookTrashDao.all.single())

        assertTrue(db.bookTrashDao.all.isEmpty())
        assertNull(db.bookDao.getBook(b.bookUrl))
    }

    @Test
    fun `清理过期条目只删超期的`() {
        val now = System.currentTimeMillis()
        db.bookTrashDao.insert(
            BookTrash(bookUrl = "old", name = "旧", author = "a", deletedAt = now - 40 * day),
            BookTrash(bookUrl = "new", name = "新", author = "a", deletedAt = now - 1 * day)
        )

        val removed = BookTrashHelper.purgeExpired(now)

        assertEquals(1, removed)
        assertEquals(listOf("new"), db.bookTrashDao.all.map { it.bookUrl })
    }

    // ---------------------------------------------------------------- 跨设备传播

    @Test
    fun `云端回收站有条目时 本地同名书跟着移入回收站`() {
        val b = book(name = "被删的书", author = "作者")
        db.bookDao.insert(b)

        val remote = SyncData(
            bookTrash = listOf(BookTrashSync(name = "被删的书", author = "作者", deletedAt = 1000L))
        )
        UserDataSync.replaceLocal(remote)

        assertNull("本地书架应当已移除", db.bookDao.getBook(b.bookUrl))
        val trash = db.bookTrashDao.all.single()
        assertEquals("被删的书", trash.name)
        assertEquals("删除时间要沿用云端，否则两端保留期错开", 1000L, trash.deletedAt)
    }

    @Test
    fun `云端回收站字段缺失时不动本地书架`() {
        val b = book(name = "还在的书", author = "作者")
        db.bookDao.insert(b)

        // v2 载荷没有 bookTrash 字段
        UserDataSync.replaceLocal(SyncData())

        assertNotNull("字段缺失表示载荷不携带该类目，不能当成「云端确认为空」", db.bookDao.getBook(b.bookUrl))
        assertTrue(db.bookTrashDao.all.isEmpty())
    }

    @Test
    fun `本地已有同名回收站条目时不重复处理`() {
        val b = book(name = "书", author = "作者")
        db.bookDao.insert(b)
        BookTrashHelper.moveToTrash(b, 500L)

        val remote = SyncData(
            bookTrash = listOf(BookTrashSync(name = "书", author = "作者", deletedAt = 500L))
        )
        UserDataSync.replaceLocal(remote)

        assertEquals(1, db.bookTrashDao.all.size)
        assertEquals(500L, db.bookTrashDao.all.single().deletedAt)
    }

    @Test
    fun `同一本书重复移入不产生两条`() {
        val b = book()
        db.bookDao.insert(b)
        BookTrashHelper.moveToTrash(b)
        // 模拟另一台设备也删了同一本书，同步过来再次移入
        db.bookDao.insert(b)
        BookTrashHelper.moveToTrash(b)

        assertEquals(1, db.bookTrashDao.all.size)
    }
}
