package io.legado.app.help.sync

import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同步载荷 v3 的语义测试。
 *
 * 这一版把同步从「3 类」扩到「15 类」，最大的风险是**升级当次清空数据**：
 * 旧客户端上传的 v2 JSON 里没有新字段，如果新字段默认成空列表，
 * 就会被判成「云端确实没有」，进而删光本地的 RSS、替换规则、字典。
 *
 * 因此这里把「字段缺失」与「字段为空」严格区分开：
 * - 字段缺失（null）  → 该类目整体跳过，不动本地
 * - 字段为空（[]）    → 云端确实没有，按规则处理（删除可传播）
 *
 * 另外固定替换规则的身份算法：不能用主键 id（各设备 autoGenerate 必然不同），
 * 必须用内容哈希，且 order / isEnabled 不参与身份（它们要同步，但不是「是谁」）。
 */
class SyncPayloadV3Test {

    // ---------------------------------------------------------------- v2 载荷兼容

    private val v2Json = """
        {
          "version": 2,
          "groups": [],
          "bookmarks": [],
          "sources": []
        }
    """.trimIndent()

    @Test
    fun `v2 载荷缺少的新字段解析为 null`() {
        val data = GSON.fromJsonObject<SyncData>(v2Json).getOrThrow()

        assertNull("v2 没有 rssSources，必须解析成 null 而不是空列表", data.rssSources)
        assertNull("v2 没有 replaceRules", data.replaceRules)
        assertNull("v2 没有 dictRules", data.dictRules)
        assertNull("v2 没有 txtTocRules", data.txtTocRules)
        assertNull("v2 没有 searchKeywords", data.searchKeywords)
    }

    @Test
    fun `v2 载荷的既有字段仍正常解析`() {
        val data = GSON.fromJsonObject<SyncData>(v2Json).getOrThrow()

        assertEquals(2, data.version)
        assertTrue(data.groups.isEmpty())
        assertTrue(data.bookmarks.isEmpty())
        assertTrue(data.sources.isEmpty())
    }

    @Test
    fun `云端该类目缺失时整体跳过 不删本地`() {
        val local = SyncData(
            rssSources = listOf(RssSource(sourceUrl = "u1", sourceName = "本地有一份"))
        )
        val remote = SyncData(rssSources = null)

        val diff = SyncMerge.diff(local.rssSources, remote.rssSources) { it.sourceUrl }

        assertTrue("字段缺失表示「这一端没这个能力」，不能拿它当空列表删数据", diff.isEmpty())
    }

    @Test
    fun `云端该类目为空列表时才允许删除本地`() {
        val local = SyncData(rssSources = listOf(RssSource(sourceUrl = "u1")))
        val remote = SyncData(rssSources = emptyList())

        val diff = SyncMerge.diff(local.rssSources, remote.rssSources) { it.sourceUrl }

        assertFalse("空列表是明确信号：云端确实没有，删除可以传播", diff.isEmpty())
        assertEquals(1, diff.toDelete.size)
        assertEquals("u1", diff.toDelete[0].sourceUrl)
    }

    @Test
    fun `本地为该类目 null 时 不做删除`() {
        // 理论上本地 export 不会是 null，但纯函数必须对 null 收敛
        val diff = SyncMerge.diff<ReplaceRule>(null, emptyList()) { SyncKeys.replaceRuleKey(it) }

        assertTrue(diff.isEmpty())
    }

    // ---------------------------------------------------------------- 空列表保护

    @Test
    fun `开启保护时 云端空列表不删本地`() {
        val local = listOf(RssSource(sourceUrl = "u1"))
        val remote = emptyList<RssSource>()

        val diff = SyncMerge.diff(local, remote, keepWhenRemoteEmpty = true) { it.sourceUrl }

        assertTrue("书源类资产要保护：云端空不代表要删光本地", diff.isEmpty())
    }

    @Test
    fun `保护只挡空列表 不挡正常删除`() {
        val local = listOf(RssSource(sourceUrl = "u1"), RssSource(sourceUrl = "u2"))
        val remote = listOf(RssSource(sourceUrl = "u2"))

        val diff = SyncMerge.diff(local, remote, keepWhenRemoteEmpty = true) { it.sourceUrl }

        assertEquals(1, diff.toDelete.size)
        assertEquals("u1", diff.toDelete[0].sourceUrl)
    }

    // ---------------------------------------------------------------- 差异计算

    @Test
    fun `双向差异 删多出的 补缺少的`() {
        val local = listOf(RssSource(sourceUrl = "a"), RssSource(sourceUrl = "b"))
        val remote = listOf(RssSource(sourceUrl = "b"), RssSource(sourceUrl = "c"))

        val diff = SyncMerge.diff(local, remote) { it.sourceUrl }

        assertEquals(listOf("a"), diff.toDelete.map { it.sourceUrl })
        assertEquals(listOf("c"), diff.toInsert.map { it.sourceUrl })
    }

    @Test
    fun `两侧一致时无动作`() {
        val same = listOf(RssSource(sourceUrl = "a"))
        assertTrue(SyncMerge.diff(same, same) { it.sourceUrl }.isEmpty())
    }

    // ---------------------------------------------------------------- 替换规则身份

    private fun rule(
        id: Long = 1L,
        name: String = "去广告",
        pattern: String = "\\s*广告\\s*",
        replacement: String = "",
        order: Int = 0,
        enabled: Boolean = true
    ) = ReplaceRule(
        id = id,
        name = name,
        pattern = pattern,
        replacement = replacement,
        order = order,
        isEnabled = enabled
    )

    @Test
    fun `不同 id 的相同规则 身份相同`() {
        val a = rule(id = 1L)
        val b = rule(id = 999L)

        assertEquals("id 由各设备 autoGenerate，不能参与身份", SyncKeys.replaceRuleKey(a), SyncKeys.replaceRuleKey(b))
    }

    @Test
    fun `内容不同则身份不同`() {
        assertNotEquals(
            SyncKeys.replaceRuleKey(rule(pattern = "广告")),
            SyncKeys.replaceRuleKey(rule(pattern = "推广"))
        )
    }

    @Test
    fun `排序与启用状态不参与身份`() {
        val a = rule(order = 0, enabled = true)
        val b = rule(order = 42, enabled = false)

        assertEquals(
            "order/isEnabled 要同步，但改它们不代表换了一条规则",
            SyncKeys.replaceRuleKey(a),
            SyncKeys.replaceRuleKey(b)
        )
    }

    @Test
    fun `身份稳定 同规则多次计算一致`() {
        assertEquals(SyncKeys.replaceRuleKey(rule()), SyncKeys.replaceRuleKey(rule()))
    }

    @Test
    fun `身份不包含字段拼接歧义`() {
        // "ab|" + "c" 与 "ab" + "|c" 若简单拼接会撞车，用分隔符或长度前缀规避
        val a = ReplaceRule(name = "ab", pattern = "c", replacement = "")
        val b = ReplaceRule(name = "ab|", pattern = "c", replacement = "")

        assertNotEquals(SyncKeys.replaceRuleKey(a), SyncKeys.replaceRuleKey(b))
    }

    // ---------------------------------------------------------------- 规范化与指纹

    @Test
    fun `规范化对新增类目也排序`() {
        val data = SyncData(
            dictRules = listOf(DictRule(name = "z"), DictRule(name = "a")),
            txtTocRules = emptyList(),
            rssSources = listOf(RssSource(sourceUrl = "u2"), RssSource(sourceUrl = "u1"))
        ).normalized()

        assertEquals(listOf("a", "z"), data.dictRules!!.map { it.name })
        assertEquals(listOf("u1", "u2"), data.rssSources!!.map { it.sourceUrl })
    }

    @Test
    fun `规范化保留 null 语义`() {
        val data = SyncData(rssSources = null).normalized()
        assertNull("规范化不能把「缺失」变成「空列表」", data.rssSources)
    }

    @Test
    fun `新增类目参与指纹`() {
        val a = SyncData(rssSources = listOf(RssSource(sourceUrl = "u1")))
        val b = SyncData(rssSources = listOf(RssSource(sourceUrl = "u2")))

        assertNotEquals(SyncFingerprint.of(a), SyncFingerprint.of(b))
    }

    @Test
    fun `顺序不同但内容相同的载荷指纹一致`() {
        val a = SyncData(rssSources = listOf(RssSource(sourceUrl = "u1"), RssSource(sourceUrl = "u2")))
        val b = SyncData(rssSources = listOf(RssSource(sourceUrl = "u2"), RssSource(sourceUrl = "u1")))

        assertEquals(SyncFingerprint.of(a), SyncFingerprint.of(b))
    }
}
