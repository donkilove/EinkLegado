package io.legado.app.help.sync

import androidx.room.Room
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.DbProvider
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookTrash
import io.legado.app.testing.TestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 回收站「彻底删除」的清理完整性测试。
 *
 * 「彻底」必须同时清掉三样东西：回收站行、本地文件、云端文件。
 * 此前只清了数据库行，本地文件与云端文件都留在原地——
 * 用户以为删干净了，磁盘上却一直占着空间，网盘里也堆着看不见的孤儿文件。
 *
 * 这里固定四件事：
 * 1. 彻底删除会删掉本地文件；
 * 2. 清空回收站也走同一条清理路径（不能只 clear 数据库）；
 * 3. 网络书（书源）的 bookUrl 是网址，绝不能被当路径删；
 * 4. 过期清理同样删本地文件。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class BookTrashPurgeTest {

    private lateinit var db: AppDatabase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        AppDatabase.dbCallback.skipSeedData = true
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.dbCallback)
            .build()
        DbProvider.override(db)
        tempDir = File(context.cacheDir, "purge-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        DbProvider.reset()
        AppDatabase.dbCallback.skipSeedData = false
        db.close()
        tempDir.deleteRecursively()
    }

    private fun localBookFile(name: String = "book.txt"): File {
        val f = File(tempDir, name)
        f.writeText("第一章 测试\n正文")
        return f
    }

    private fun trashOf(file: File, origin: String = BookType.localTag): BookTrash =
        BookTrash(
            bookUrl = file.absolutePath,
            name = "测试书",
            author = "作者",
            origin = origin,
            originName = file.name,
            type = BookType.local,
            deletedAt = System.currentTimeMillis()
        )

    @Test
    fun `彻底删除会删掉本地文件`() {
        val file = localBookFile()
        val trash = trashOf(file)
        db.bookTrashDao.insert(trash)
        assertTrue("前置条件：文件应存在", file.exists())

        BookTrashHelper.purge(trash)

        assertFalse("彻底删除后本地文件应被删掉", file.exists())
        assertEquals("回收站行也应被删掉", 0, db.bookTrashDao.count)
    }

    @Test
    fun `清空回收站会逐条删掉本地文件`() {
        val f1 = localBookFile("a.txt")
        val f2 = localBookFile("b.txt")
        db.bookTrashDao.insert(trashOf(f1))
        db.bookTrashDao.insert(trashOf(f2).copy(name = "另一本"))

        val cleared = BookTrashHelper.purgeAll()

        assertEquals(2, cleared)
        assertFalse("第一本的文件应被删掉", f1.exists())
        assertFalse("第二本的文件应被删掉", f2.exists())
        assertEquals("回收站应被清空", 0, db.bookTrashDao.count)
    }

    @Test
    fun `网络书的网址不会被当成路径删除`() {
        // 书源书的 bookUrl 是 http 网址，不是文件路径。
        // 若按路径删，FileUtils.delete 会静默失败或误删同名本地路径。
        val trash = BookTrash(
            bookUrl = "https://example.com/book/1",
            name = "网络书",
            author = "作者",
            origin = "https://example.com",
            originName = "网络书",
            type = 0,
            deletedAt = System.currentTimeMillis()
        )
        db.bookTrashDao.insert(trash)

        BookTrashHelper.purge(trash)

        assertEquals("回收站行应被删掉", 0, db.bookTrashDao.count)
        // 没有抛异常、也没有把网址当路径处理，即为通过
    }

    @Test
    fun `过期清理同样删掉本地文件`() {
        val file = localBookFile("expired.txt")
        val old = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        db.bookTrashDao.insert(trashOf(file).copy(deletedAt = old))

        val purged = BookTrashHelper.purgeExpired()

        assertEquals(1, purged)
        assertFalse("过期清理也应删掉本地文件", file.exists())
        assertEquals(0, db.bookTrashDao.count)
    }

    @Test
    fun `未到期的条目不会被清理`() {
        val file = localBookFile("keep.txt")
        db.bookTrashDao.insert(trashOf(file))

        val purged = BookTrashHelper.purgeExpired()

        assertEquals(0, purged)
        assertTrue("未到期不该删文件", file.exists())
        assertEquals(1, db.bookTrashDao.count)
    }

    @Test
    fun `删除失败也要清掉数据库行`() {
        // 文件不存在时删除会失败，但用户的意图是「这本书消失」，
        // 不能因为一个删不掉的文件把记录永远留在回收站里。
        val ghost = File(tempDir, "not-exist.txt")
        val trash = trashOf(ghost)
        db.bookTrashDao.insert(trash)

        BookTrashHelper.purge(trash)

        assertEquals("文件不存在时也应清掉回收站行", 0, db.bookTrashDao.count)
    }
}
