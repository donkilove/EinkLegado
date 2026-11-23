package io.legado.app.help.sync

import io.legado.app.utils.GSON
import java.security.MessageDigest

/**
 * 同步数据的指纹计算。
 *
 * 指纹用于判断「本地自上次同步后是否改动过」，判错会直接丢数据：
 * - 误判成「没改动」→ 用云端覆盖本地，本地新增的书源/分组消失
 * - 误判成「改过」→ 上传本地，云端更新被冲掉
 *
 * 早期实现用 `GSON.toJson(data.normalized()).hashCode().toString()`，即 32 位有符号哈希。
 * 32 位空间里碰撞并非纯理论问题（万级条目量级下概率已不可忽略），一旦碰撞就会
 * 把「改过」误判为「没改过」，进而用云端覆盖本地。现改为 SHA-256。
 *
 * ## 版本前缀
 *
 * 新指纹形如 `v2:<64 位十六进制>`。加前缀是为了让旧格式（纯数字）能被明确识别：
 * 升级后 `sync_state.json` 里躺着的是旧值，它与任何新指纹都不相等，
 * 于是同步会落到「两边都动过 → 以本地为准上传」这一保守分支，
 * 不会因为「指纹恰好不等」以外的原因误删数据。
 * [isLegacy] 用于显式判定旧值，便于日志与后续清理。
 */
object SyncFingerprint {

    /** 与 [SyncData.version] 呼应的前缀；格式变更时递增 */
    private const val PREFIX = "v2:"

    /**
     * 计算指纹。输入会先经 [SyncData.normalized] 规范化顺序，
     * 保证同一份数据无论插入先后都得到相同结果。
     */
    fun of(data: SyncData): String {
        val json = GSON.toJson(data.normalized())
        return PREFIX + sha256Hex(json)
    }

    /**
     * 是否为旧格式（32 位 hashCode）指纹。
     *
     * 旧实现的输出是纯数字，可能带负号。空串与 null 不算旧格式——
     * 它们表示「从未同步过」，与「有旧指纹」是两种不同的状态。
     */
    fun isLegacy(fingerprint: String?): Boolean {
        if (fingerprint.isNullOrEmpty()) return false
        return fingerprint.matches(Regex("-?\\d+"))
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            if (v < 0x10) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }
}
