package io.legado.app.help.sync

import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadRecord

/**
 * 阅读时长的按设备合并。
 *
 * `ReadRecord` 的主键是 `(deviceId, bookName)`，设计意图是**每台设备记自己那一行**，
 * 展示时用 `sum(readTime) group by bookName` 汇总。这样：
 *
 * - 手机读 10 分钟 + 平板读 10 分钟 = 20 分钟，两端一致；
 * - 各设备只写自己那一行，同步不会互相覆盖，也不会重复计数。
 *
 * 但 `ReadBook` / `ReadManga` 此前从未给 `deviceId` 赋值（默认空串），
 * 所有设备的时间都挤在同一行，同步时谁后写谁覆盖。
 * [claimLegacyRows] 负责把历史遗留的空 deviceId 行并回本机。
 */
object ReadRecordMerge {

    fun key(record: ReadRecord): String = record.deviceId + "\u0001" + record.bookName

    /**
     * 算出云端有哪些记录需要写进本地。
     *
     * @param remote 云端记录；**null 表示载荷不携带该类目**，此时一条都不动
     * @return 需要插入/覆盖本地的记录
     */
    fun pick(local: List<ReadRecord>?, remote: List<ReadRecord>?): List<ReadRecord> {
        if (remote == null) return emptyList()
        val localByKey = local.orEmpty().associateBy { key(it) }
        return remote.mapNotNull { remoteRecord ->
            val localRecord = localByKey[key(remoteRecord)]
            when {
                localRecord == null -> remoteRecord
                remoteRecord.readTime > localRecord.readTime ->
                    // readTime 变大才合并；lastRead 取两者较大，避免被更旧的值顶回去
                    remoteRecord.copy(lastRead = maxOf(remoteRecord.lastRead, localRecord.lastRead))

                else -> null
            }
        }
    }

    /**
     * 把 deviceId 为空的历史行并入本机那一行。
     *
     * 升级前所有记录都是空 deviceId。若不处理，本机新记录会另起一行，
     * 同一本书出现两行，虽然求和结果仍对，但「本机记录」的语义就乱了
     * （`Restore` 里按 deviceId 判本机的逻辑会失效）。
     *
     * @return 处理的遗留行数
     */
    fun claimLegacyRows(all: List<ReadRecord>, deviceId: String): Int {
        if (deviceId.isEmpty()) return 0
        val legacy = all.filter { it.deviceId.isEmpty() }
        if (legacy.isEmpty()) return 0
        val mineByBook = all.filter { it.deviceId == deviceId }.associateBy { it.bookName }
        legacy.forEach { old ->
            val existing = mineByBook[old.bookName]
            val merged = ReadRecord(
                deviceId = deviceId,
                bookName = old.bookName,
                readTime = maxOf(old.readTime, existing?.readTime ?: 0L),
                lastRead = maxOf(old.lastRead, existing?.lastRead ?: 0L)
            )
            appDb.readRecordDao.insert(merged)
            appDb.readRecordDao.delete(old)
        }
        return legacy.size
    }
}
