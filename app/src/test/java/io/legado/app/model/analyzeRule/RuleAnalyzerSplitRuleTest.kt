package io.legado.app.model.analyzeRule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RuleAnalyzer.splitRule] 的行为测试。
 *
 * 这是书源规则引擎实际在用的切分入口：[AnalyzeByJSoup]、[AnalyzeByXPath]、
 * [AnalyzeByJSonPath]、[AnalyzeUrl] 都以
 * `RuleAnalyzer(rule).trim().splitRule("@")` 的形式使用它，
 * 把 `rule1@rule2@rule3` 切成多级规则，再逐级求值。
 *
 * 切分错误的表现是静默的：书源不报错，但抓出来的字段是空的或错位的，
 * 用户只会看到「搜索有结果、打开是空白」。
 *
 * 注意：`splitRule` 对「分隔符出现在 `[]` 或 `()` 内」有特殊处理——
 * 选择器内部的分隔符不切分。这是它比普通 `String.split` 复杂的地方，
 * 也是最容易出错的地方。
 */
class RuleAnalyzerSplitRuleTest {

    private fun split(rule: String, separator: String = "@"): List<String> =
        RuleAnalyzer(rule).let {
            it.trim()
            it.splitRule(separator)
        }

    // ---------------------------------------------------------------- 基本切分

    @Test
    fun `无分隔符时返回单元素`() {
        assertEquals(listOf("div.intro"), split("div.intro"))
    }

    @Test
    fun `按 @ 切成两级`() {
        assertEquals(listOf("div.intro", "p"), split("div.intro@p"))
    }

    @Test
    fun `按 @ 切成三级`() {
        assertEquals(listOf("div", "p", "a"), split("div@p@a"))
    }

    @Test
    fun `末尾分隔符产生空尾项`() {
        // 既有行为：`a@` 切成 ["a", ""]
        assertEquals(listOf("a", ""), split("a@"))
    }

    @Test
    fun `首部分隔符会被 trim 去掉`() {
        // trim() 会跳过开头的 @ 与空白
        assertEquals(listOf("a", "b"), split("@a@b"))
    }

    @Test
    fun `多个首部分隔符都会被 trim 去掉`() {
        assertEquals(listOf("a"), split("@@@a"))
    }

    @Test
    fun `前导空格会被 trim 去掉`() {
        assertEquals(listOf("a", "b"), split("  @a@b"))
    }

    // ---------------------------------------------------------------- 分隔符在括号内

    @Test
    fun `方括号内的分隔符不切分`() {
        // CSS 属性选择器里常见 `[data-x="a@b"]`，此时 @ 不是规则分隔符
        assertEquals(listOf("div[data-x=\"a@b\"]", "p"), split("div[data-x=\"a@b\"]@p"))
    }

    @Test
    fun `圆括号内的分隔符不切分`() {
        // 伪类选择器 `:not(...)` 里可能出现任意字符
        assertEquals(listOf("div:not(.a@b)", "p"), split("div:not(.a@b)@p"))
    }

    @Test
    fun `嵌套括号内的分隔符不切分`() {
        assertEquals(listOf("div:not(.a[data-x=\"y@z\"])", "p"), split("div:not(.a[data-x=\"y@z\"])@p"))
    }

    @Test
    fun `括号后的分隔符正常切分`() {
        assertEquals(listOf("div[attr]", "p"), split("div[attr]@p"))
    }

    @Test
    fun `括号内的分隔符保留在元素中`() {
        val result = split("a[@b]@c")
        assertEquals(2, result.size)
        assertEquals("a[@b]", result[0])
        assertEquals("c", result[1])
    }

    // ---------------------------------------------------------------- 其它分隔符

    @Test
    fun `支持自定义分隔符`() {
        assertEquals(listOf("a", "b", "c"), split("a%b%c", "%"))
    }

    @Test
    fun `支持多字符分隔符`() {
        assertEquals(listOf("a", "b"), split("a::b", "::"))
    }

    @Test
    fun `分隔符为竖线时正常切分`() {
        assertEquals(listOf("a", "b"), split("a|b", "|"))
    }

    // ---------------------------------------------------------------- 边界

    @Test
    fun `空串返回单元素`() {
        assertEquals(listOf(""), split(""))
    }

    @Test
    fun `纯分隔符被 trim 全部跳过返回单元素空串`() {
        // 只有 `@` 时 trim() 会把它整个吃掉，splitRule 返回 [""]。
        // 既有行为，用测试钉住。
        assertEquals(listOf(""), split("@"))
    }

    @Test
    fun `全是分隔符时返回单元素空串`() {
        assertEquals(listOf(""), split("@@@"))
    }

    @Test
    fun `空白包围的分隔符返回单元素空串`() {
        assertEquals(listOf(""), split("  @  "))
    }

    @Test
    fun `尾部空格不会被裁掉`() {
        // trim() 只处理开头，结尾空白原样保留
        assertEquals(listOf("a  "), split("  a  "))
    }

    @Test
    fun `纯空白被 trim 后为空`() {
        val result = split("   ")
        assertEquals(1, result.size)
    }

    // ---------------------------------------------------------------- 契约

    @Test
    fun `返回结果按原顺序拼接可还原`() {
        // 切分不应丢失内容（首尾分隔符除外）
        val rule = "div.a@ul.list@li@a.link"
        val parts = split(rule)

        assertEquals(rule, parts.joinToString("@"))
    }

    @Test
    fun `结果元素数量与分隔符数量对应`() {
        val parts = split("a@b@c@d")

        assertEquals(4, parts.size)
        assertTrue(parts.none { it.contains("@") })
    }
}
