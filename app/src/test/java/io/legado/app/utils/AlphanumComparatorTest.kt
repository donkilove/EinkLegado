package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AlphanumComparator] 的行为测试。
 *
 * 它负责「自然排序」：把字符串里的连续数字当作数值比较，而不是按字符逐个比。
 * 阅读器用它排章节名，所以 `第2章` 必须排在 `第10章` 之前——
 * 若退化成字典序，`第10章` 会跑到 `第2章` 前面，目录顺序全乱。
 *
 * 契约：返回负数表示 s1 在前，0 表示等价，正数表示 s1 在后。
 */
class AlphanumComparatorTest {

    private fun sorted(vararg items: String): List<String> =
        items.sortedWith(AlphanumComparator)

    // ---------------------------------------------------------------- 基本契约

    @Test
    fun `相同字符串比较为 0`() {
        assertEquals(0, AlphanumComparator.compare("第1章", "第1章"))
        assertEquals(0, AlphanumComparator.compare("", ""))
    }

    @Test
    fun `比较结果满足反对称性`() {
        val a = "第2章"
        val b = "第10章"

        assertTrue("a 应排在 b 前", AlphanumComparator.compare(a, b) < 0)
        assertTrue("b 应排在 a 后", AlphanumComparator.compare(b, a) > 0)
    }

    // ---------------------------------------------------------------- 数字按数值比较

    @Test
    fun `数字按数值大小而不是字符顺序`() {
        assertEquals(
            listOf("第2章", "第10章"),
            sorted("第10章", "第2章")
        )
    }

    @Test
    fun `多位数排序正确`() {
        assertEquals(
            listOf("第1章", "第2章", "第9章", "第10章", "第11章", "第100章"),
            sorted("第100章", "第10章", "第11章", "第2章", "第9章", "第1章")
        )
    }

    @Test
    fun `位数不同时短数字更小`() {
        assertTrue(AlphanumComparator.compare("卷9", "卷10") < 0)
        assertTrue(AlphanumComparator.compare("卷99", "卷100") < 0)
    }

    @Test
    fun `位数相同时逐位比较`() {
        assertTrue(AlphanumComparator.compare("卷12", "卷13") < 0)
        assertTrue(AlphanumComparator.compare("卷19", "卷20") < 0)
    }

    // ---------------------------------------------------------------- 非数字按字典序

    @Test
    fun `纯文字按字典序`() {
        assertEquals(
            listOf("Chapter", "Prologue"),
            sorted("Prologue", "Chapter")
        )
    }

    @Test
    fun `纯文字比较与 String 一致`() {
        assertEquals(
            "abc".compareTo("abd"),
            AlphanumComparator.compare("abc", "abd")
        )
    }

    // ---------------------------------------------------------------- 混合

    @Test
    fun `文字相同数字不同时按数字排`() {
        assertEquals(
            listOf("卷1", "卷2", "卷10"),
            sorted("卷10", "卷1", "卷2")
        )
    }

    @Test
    fun `数字在前文字在后也能正确分段`() {
        assertEquals(
            listOf("1卷a", "1卷b", "2卷a"),
            sorted("2卷a", "1卷b", "1卷a")
        )
    }

    @Test
    fun `多段数字依次比较`() {
        assertEquals(
            listOf("第1卷10章", "第1卷11章", "第2卷1章"),
            sorted("第2卷1章", "第1卷11章", "第1卷10章")
        )
    }

    // ---------------------------------------------------------------- 前后缀

    @Test
    fun `一个是另一个的前缀时短的在前`() {
        assertTrue(AlphanumComparator.compare("abc", "abcd") < 0)
        assertTrue(AlphanumComparator.compare("abcd", "abc") > 0)
    }

    @Test
    fun `空字符串排在最前`() {
        assertTrue("空串应排在非空串之前", AlphanumComparator.compare("", "a") < 0)
    }

    // ---------------------------------------------------------------- 稳定性

    @Test
    fun `对已排序列表再排序结果不变`() {
        val sortedList = listOf("第1章", "第2章", "第10章")

        assertEquals(sortedList, sorted(*sortedList.toTypedArray()))
    }

    @Test
    fun `相同元素排序后仍相邻`() {
        val list = sorted("第2章", "第2章", "第10章")

        assertEquals(listOf("第2章", "第2章", "第10章"), list)
    }

    // ---------------------------------------------------------------- 边界

    @Test
    fun `单个数字字符串`() {
        assertEquals(listOf("2", "10"), sorted("10", "2"))
    }

    @Test
    fun `前导零按数值比较`() {
        // "010" 与 "10" 数值相同，长度不同 → 短者在前（区分位数策略）
        assertTrue(AlphanumComparator.compare("010", "10") > 0)
    }
}
