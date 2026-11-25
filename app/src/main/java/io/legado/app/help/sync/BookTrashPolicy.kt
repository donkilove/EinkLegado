package io.legado.app.help.sync

import io.legado.app.data.entities.BookTrash

/**
 * 回收站保留策略。
 *
 * 删除需要跨设备传播，但 `Book.delete()` 会连本地文件一起删，误删不可逆。
 * 回收站把「传播删除」与「销毁数据」拆成两个时刻：30 天缓冲期内任何一端都能恢复。
 *
 * 边界取**严格大于**：第 30 天当天仍可恢复，第 31 天才清理。
 * 这样「保留 30 天」在用户理解上不会少一天。
 */
object BookTrashPolicy {

    const val RETENTION_DAYS = 30L

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun isExpired(deletedAt: Long, now: Long): Boolean =
        now - deletedAt > RETENTION_DAYS * DAY_MILLIS

    fun expired(items: List<BookTrash>, now: Long): List<BookTrash> =
        items.filter { isExpired(it.deletedAt, now) }

    /** 剩余天数，已过期返回 0 */
    fun remainingDays(deletedAt: Long, now: Long): Long {
        val elapsed = now - deletedAt
        val left = RETENTION_DAYS * DAY_MILLIS - elapsed
        if (left <= 0) return 0
        return left / DAY_MILLIS
    }
}
