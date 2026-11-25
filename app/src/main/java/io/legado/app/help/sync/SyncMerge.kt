package io.legado.app.help.sync

/**
 * 差异计算：算出「以云端为准」时，本地需要删哪些、补哪些。
 *
 * 这一版的载荷新增了 12 个类目，每个类目都可能面对两种云端状态，必须严格区分：
 *
 * - **字段缺失（`remote == null`）**：这一端（或旧版本客户端）不携带该类目，
 *   不代表「云端没有数据」。**整体跳过，不动本地。**
 * - **字段为空列表（`remote == emptyList()`）**：这是明确的信号，云端确实一条都没有。
 *   此时允许删除本地条目，删除才得以跨设备传播。
 *
 * 把两者混淆的代价是升级当次把用户的数据清空——那是最危险的失败模式。
 */
data class SyncDiff<T>(
    val toDelete: List<T>,
    val toInsert: List<T>
) {
    fun isEmpty(): Boolean = toDelete.isEmpty() && toInsert.isEmpty()
}

object SyncMerge {

    /**
     * 计算单个类目的差异。
     *
     * @param local 本地该类目的全量列表，正常情况不会为 null
     * @param remote 云端该类目的全量列表，**null 表示载荷里没有这个类目**
     * @param keepWhenRemoteEmpty 为 true 时，云端空列表也不删除本地。用于书源这类重资产。
     * @param key 条目的逻辑身份。不能用主键，因为主键由各设备生成、跨设备必然不同。
     *   放在最后一个参数，调用时可直接用尾随 lambda。
     */
    fun <T> diff(
        local: List<T>?,
        remote: List<T>?,
        keepWhenRemoteEmpty: Boolean = false,
        key: (T) -> String
    ): SyncDiff<T> {
        // 云端没有这个类目：跳过，不碰本地
        if (remote == null) return SyncDiff(emptyList(), emptyList())

        val localList = local.orEmpty()
        val remoteList = remote

        // 云端空列表：是否删除本地，取决于保护开关
        val toDelete: List<T> = if (remoteList.isEmpty()) {
            if (keepWhenRemoteEmpty) emptyList() else localList
        } else {
            val remoteKeys = remoteList.mapTo(HashSet()) { key(it) }
            localList.filterNot { key(it) in remoteKeys }
        }

        val toInsert: List<T> = if (remoteList.isEmpty()) {
            emptyList()
        } else {
            val localKeys = localList.mapTo(HashSet()) { key(it) }
            remoteList.filterNot { key(it) in localKeys }
        }

        return SyncDiff(toDelete, toInsert)
    }
}
