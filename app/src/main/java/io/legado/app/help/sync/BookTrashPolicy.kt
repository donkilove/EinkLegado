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

    /**
     * 剩余天数，已过期返回 0。
     *
     * 用**向上取整**而不是整除：删除 1 天零 1 秒后，剩余 28.99 天，
     * 整除会显示「28 天」，而用户预期是「29 天」——刚删一天就少两天，看着像 bug。
     * 向上取整后，只要还有不足一天的时间，就仍显示 1 天。
     */
    fun remainingDays(deletedAt: Long, now: Long): Long {
        val elapsed = now - deletedAt
        val left = RETENTION_DAYS * DAY_MILLIS - elapsed
        if (left <= 0) return 0
        return (left + DAY_MILLIS - 1) / DAY_MILLIS
    }
}
