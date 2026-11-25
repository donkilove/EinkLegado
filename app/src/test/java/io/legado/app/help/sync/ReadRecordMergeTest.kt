package io.legado.app.help.sync

import android.content.Context
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.DbProvider
import io.legado.app.data.entities.ReadRecord
import io.legado.app.testing.TestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 阅读时长的按设备合并。
 *
 * 背景（既有缺陷）：
 * `ReadRecord` 的主键是 `(deviceId, bookName)`，设计意图是「每台设备记自己那一行」，
 * 读取时用 `sum(readTime) group by bookName` 汇总。但 `ReadBook` / `ReadManga`
 * 构造记录后**从未给 deviceId 赋值**，默认值是空串，于是所有设备的时间都挤在一行里。
 *
 * 后果：
 * - 同步时两台设备写同一行，谁后写谁覆盖，时间会丢；
 * - `Restore.kt` 里「本机记录才比较大小」的分支因 `"" != androidId` 恒真而成为死代码，
 *   恢复备份会无条件覆盖本机阅读时长。
 *
 * 这里的测试固定两件事：
 * 1. 每台设备写自己那一行，汇总时求和，不会重复计数；
 * 2. 同步合并时同一 `(deviceId, bookName)` 取较大的 readTime 与 lastRead。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ReadRecordMergeTest {

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

    private fun record(device: String, book: String, time: Long, lastRead: Long = 0L) =
        ReadRecord(deviceId = device, bookName = book, readTime = time, lastRead = lastRead)

    // ---------------------------------------------------------------- 汇总

    @Test
    fun `两台设备各记一行 汇总为两者之和`() {
        db.readRecordDao.insert(record("phone", "书", 10 * 60_000))
        db.readRecordDao.insert(record("tablet", "书", 10 * 60_000))

        assertEquals("手机 10 分钟 + 平板 10 分钟 = 20 分钟", 20 * 60_000L, db.readRecordDao.getReadTime("书"))
    }

    @Test
    fun `同一设备重复写入是覆盖 不是累加`() {
        db.readRecordDao.insert(record("phone", "书", 5 * 60_000))
        db.readRecordDao.insert(record("phone", "书", 8 * 60_000))

        assertEquals(8 * 60_000L, db.readRecordDao.getReadTime("书"))
    }

    @Test
    fun `不同书互不影响`() {
        db.readRecordDao.insert(record("phone", "书A", 1_000))
        db.readRecordDao.insert(record("phone", "书B", 2_000))

        assertEquals(1_000L, db.readRecordDao.getReadTime("书A"))
        assertEquals(2_000L, db.readRecordDao.getReadTime("书B"))
    }

    // ---------------------------------------------------------------- 同步合并（纯函数）

    @Test
    fun `云端更长的记录会覆盖本地`() {
        val local = listOf(record("phone", "书", 1_000))
        val remote = listOf(record("phone", "书", 3_000))

        val toInsert = ReadRecordMerge.pick(local, remote)

        assertEquals(1, toInsert.size)
        assertEquals(3_000, toInsert[0].readTime)
    }

    @Test
    fun `本地更长时不被云端覆盖`() {
        val local = listOf(record("phone", "书", 5_000))
        val remote = listOf(record("phone", "书", 3_000))

        assertTrue(ReadRecordMerge.pick(local, remote).isEmpty())
    }

    @Test
    fun `云端独有的设备记录会被补上`() {
        val local = listOf(record("phone", "书", 1_000))
        val remote = listOf(record("phone", "书", 1_000), record("tablet", "书", 2_000))

        val toInsert = ReadRecordMerge.pick(local, remote)

        assertEquals(1, toInsert.size)
        assertEquals("tablet", toInsert[0].deviceId)
        assertEquals(2_000, toInsert[0].readTime)
    }

    @Test
    fun `lastRead 也取较大值`() {
        val local = listOf(record("phone", "书", 1_000, lastRead = 9_000))
        val remote = listOf(record("phone", "书", 2_000, lastRead = 5_000))

        val toInsert = ReadRecordMerge.pick(local, remote)

        assertEquals("readTime 变大就该合并", 1, toInsert.size)
        assertEquals("lastRead 不能被更小的值覆盖", 9_000, toInsert[0].lastRead)
    }

    @Test
    fun `云端字段缺失时不动本地`() {
        val local = listOf(record("phone", "书", 1_000))

        assertTrue(ReadRecordMerge.pick(local, null).isEmpty())
    }

    // ---------------------------------------------------------------- 迁移

    // ---------------------------------------------------------------- 开关闸门

    @Test
    fun `关闭同步阅读时长时 载荷里该类目为 null 而不是空列表`() {
        val records = listOf(record("phone", "书", 1_000))

        val disabled = SyncPayloadGate.readRecords(enabled = false) { records }
        val enabled = SyncPayloadGate.readRecords(enabled = true) { records }

        assertEquals(
            "null 表示载荷不携带该类目，同步会整体跳过；给空列表会被判成「云端确认没有」，反过来删掉对方的记录",
            null,
            disabled
        )
        assertEquals(records, enabled)
    }

    @Test
    fun `关闭时不会去读数据库`() {
        var called = false

        SyncPayloadGate.readRecords(enabled = false) {
            called = true
            emptyList()
        }

        assertTrue("开关关闭时不应触发查询", !called)
    }

    @Test
    fun `旧版空 deviceId 的行会被归到本机`() {
        db.readRecordDao.insert(record("", "书", 7_000))
        db.readRecordDao.insert(record("", "另一本", 3_000))

        val fixed = ReadRecordMerge.claimLegacyRows(db.readRecordDao.all, "my-device")

        assertEquals(2, fixed)
        assertTrue("迁移后不应再有空 deviceId", db.readRecordDao.all.none { it.deviceId.isEmpty() })
        assertEquals(7_000L, db.readRecordDao.getReadTime("书"))
    }

    @Test
    fun `已有本机行时不重复迁移 取较大值`() {
        db.readRecordDao.insert(record("", "书", 7_000))
        db.readRecordDao.insert(record("my-device", "书", 2_000))

        ReadRecordMerge.claimLegacyRows(db.readRecordDao.all, "my-device")

        val rows = db.readRecordDao.all.filter { it.bookName == "书" }
        assertEquals("空 deviceId 行应被并入本机行，而不是留下两条", 1, rows.size)
        assertEquals(7_000, rows[0].readTime)
    }
}
