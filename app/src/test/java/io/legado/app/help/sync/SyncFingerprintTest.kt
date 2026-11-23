package io.legado.app.help.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SyncFingerprint] 的行为测试。
 *
 * 指纹用于判断「本地自上次同步后是否改动过」，判错会直接导致数据丢失：
 * - 误判成「没改动」→ 用云端覆盖本地，本地新增的书源/分组消失
 * - 误判成「改过」→ 上传本地，云端更新被冲掉
 *
 * 因此这里同时守住两件事：同一份数据的指纹必须稳定，不同数据的指纹必须不同。
 * 另外要守住旧格式（32 位 hashCode）的识别——升级后旧指纹不能与任何新指纹相等，
 * 否则升级当次同步会误判为「本地未改动」。
 */
class SyncFingerprintTest {

    // ---------------------------------------------------------------- 稳定性

    @Test
    fun `同一份数据多次计算结果一致`() {
        val data = SyncData(version = 2)

        assertEquals(SyncFingerprint.of(data), SyncFingerprint.of(data))
    }

    @Test
    fun `指纹带版本前缀`() {
        val fp = SyncFingerprint.of(SyncData())

        assertTrue("新指纹应带 v2 前缀以便与旧格式区分", fp.startsWith("v2:"))
    }

    @Test
    fun `SHA-256 输出长度固定`() {
        val fp = SyncFingerprint.of(SyncData())

        // "v2:" + 64 位十六进制
        assertEquals("v2:".length + 64, fp.length)
    }

    // ---------------------------------------------------------------- 区分度

    @Test
    fun `内容不同则指纹不同`() {
        val a = SyncData(version = 2)
        val b = SyncData(version = 99)

        assertNotEquals(SyncFingerprint.of(a), SyncFingerprint.of(b))
    }

    @Test
    fun `数据内容不同则指纹不同`() {
        // SyncData 默认 version = 2，必须显式换成别的值才能构成「内容不同」
        assertNotEquals(
            SyncFingerprint.of(SyncData()),
            SyncFingerprint.of(SyncData(version = 1))
        )
    }

    // ---------------------------------------------------------------- 旧格式识别

    @Test
    fun `旧格式指纹被识别为过期`() {
        // 旧实现是 String.hashCode().toString()，即纯数字（可能带负号）
        assertTrue("纯数字旧值应判为过期", SyncFingerprint.isLegacy("123456789"))
        assertTrue("负数旧值也应判为过期", SyncFingerprint.isLegacy("-987654321"))
    }

    @Test
    fun `新格式指纹不被识别为过期`() {
        assertFalse(SyncFingerprint.isLegacy(SyncFingerprint.of(SyncData())))
    }

    @Test
    fun `空字符串与 null 不被视为旧格式`() {
        assertFalse("空串不是有效的旧指纹", SyncFingerprint.isLegacy(""))
        assertFalse(SyncFingerprint.isLegacy(null))
    }

    @Test
    fun `旧指纹永不等于新指纹`() {
        val data = SyncData()
        val legacy = data.normalized().toString().hashCode().toString()

        assertNotEquals(
            "升级后旧值与新值不等，才会走保守分支",
            legacy,
            SyncFingerprint.of(data)
        )
    }
}
