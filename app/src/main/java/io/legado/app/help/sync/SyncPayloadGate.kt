package io.legado.app.help.sync

import io.legado.app.data.entities.ReadRecord

/**
 * 载荷类目的开关闸门。
 *
 * 开关关闭时返回 **null** 而不是空列表——这个区别决定数据安全：
 * - null  → 本次载荷不携带该类目，同步整体跳过，不动任何一端；
 * - []    → 云端确认「一条都没有」，会按删除传播清掉对方的记录。
 *
 * 抽成纯函数是为了能脱离 Android 单测：真正容易写错的就是这个 null / [] 的选择。
 */
object SyncPayloadGate {

    fun readRecords(enabled: Boolean, records: () -> List<ReadRecord>?): List<ReadRecord>? =
        if (enabled) records() else null
}
