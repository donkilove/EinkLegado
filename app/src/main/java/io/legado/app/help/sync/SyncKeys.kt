package io.legado.app.help.sync

import io.legado.app.data.entities.ReplaceRule
import java.security.MessageDigest

/**
 * 各类目的逻辑身份算法。
 *
 * 为什么不能用主键 `id`：
 * `ReplaceRule.id` 是 `@PrimaryKey(autoGenerate = true)`，
 * 各设备各自生成，同一条规则在两端 id 必然不同。拿它做身份，
 * 同一规则会被当成两条，删除永远无法传播，合并不断制造重复。
 *
 * 所以身份由**内容**决定：内容相同即同一条，内容变了就是新规则。
 * 代价是「改内容」表现为「删旧增新」，而不是「就地更新」；
 * 对用户要的是两端状态一致，这个代价可接受。
 */
object SyncKeys {

    /**
     * 字段分隔符。
     *
     * 用不可见字符而不是普通分隔符：若用 `|`，则「name=ab, pattern=c」与
     * 「name=ab|, pattern=c」会拼接成同一个字符串，两条不同规则被误判为一条。
     */
    private const val SEP = "\u0001"

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private fun part(value: String?): String = value ?: ""

    /**
     * 替换规则的身份。
     *
     * 参与身份的字段（语义字段，决定规则「是什么」）：
     * `name`, `group`, `pattern`, `replacement`, `scope`,
     * `scopeTitle`, `scopeContent`, `excludeScope`, `isRegex`, `timeoutMillisecond`
     *
     * **不参与身份**：`id`、`order`、`isEnabled`。
     * 这三个要参与同步（用户要开关状态与排序一致），但改变它们不代表换了规则，
     * 否则每次拖动排序都会产生一份副本。
     */
    fun replaceRuleKey(rule: ReplaceRule): String {
        val raw = buildString {
            append(part(rule.name)).append(SEP)
            append(part(rule.group)).append(SEP)
            append(part(rule.pattern)).append(SEP)
            append(part(rule.replacement)).append(SEP)
            append(part(rule.scope)).append(SEP)
            append(rule.scopeTitle).append(SEP)
            append(rule.scopeContent).append(SEP)
            append(part(rule.excludeScope)).append(SEP)
            append(rule.isRegex).append(SEP)
            append(rule.timeoutMillisecond).append(SEP)
        }
        return sha256Hex(raw)
    }
}
