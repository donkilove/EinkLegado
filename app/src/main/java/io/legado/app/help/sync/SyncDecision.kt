package io.legado.app.help.sync

/**
 * 同步决策：本次同步该由哪一端覆盖哪一端。
 *
 * 这些决策原本埋在 [io.legado.app.help.AppWebDav.syncUserData] 的 `when` 里，
 * 与网络请求、数据库写入混在一起，无法单独验证。而这条链路的失败是**静默**的——
 * 判错分支就会用云端覆盖本地（或反过来），用户数据消失且没有任何报错。
 *
 * 所以把它抽成纯函数：不碰 [io.legado.app.data.appDb]、不碰网络、不碰文件系统，
 * 只根据三个指纹与两端数据得出结论。
 */
sealed interface SyncDecision {

    /** 云端还没有数据，直接上传本地。 */
    data object UploadLocal : SyncDecision

    /** 本地自上次同步后未改动，以云端为准整体替换本地（删除得以传播下来）。 */
    data object ReplaceLocal : SyncDecision

    /** 云端未改动而本地改过，上传本地（删除得以传播上去）。 */
    data object UploadLocalLocalWins : SyncDecision

    /** 两边都改过，本地优先上传。 */
    data object UploadLocalBothChanged : SyncDecision

    /** 两边都改过，但本地书源为空而云端有书源——跳过上传以保护云端书源资产。 */
    data object SkipToProtectSources : SyncDecision

    companion object {

        /**
         * 计算同步决策。
         *
         * 判定顺序敏感，与既有的 `when` 分支逐一对应：
         * 1. 云端无数据
         * 2. 本地未改
         * 3. 云端未改
         * 4. 双改（含书源空保护）
         *
         * @param localHash 本地当前数据的指纹
         * @param remoteHash 云端数据的指纹
         * @param lastHash 上次同步完成时记录的指纹，`null` 表示从未同步过
         * @param local 本地全量快照
         * @param remote 云端全量快照，`null` 表示云端无数据或解析失败
         */
        fun decide(
            localHash: String,
            remoteHash: String,
            lastHash: String?,
            local: SyncData,
            remote: SyncData?
        ): SyncDecision {
            // 1. 云端没有数据：没有可比较的对象，直接上传本地。
            //    注意这与「云端数据解析失败」共用同一分支，是既有行为。
            if (remote == null) return UploadLocal

            // 2. 本地自上次同步后没动过：老实接受云端，让云端的删除也能传播下来。
            if (localHash == lastHash) return ReplaceLocal

            // 3. 云端没动、本地动了：上传本地，让本地的删除传播上去。
            if (remoteHash == lastHash) return UploadLocalLocalWins

            // 4. 两边都动过：本地优先。但本地书源为空、云端有书源时不上传，
            //    避免首次安装或数据损坏时用空列表冲掉云端书源。
            //    注意 protection 只在本地书源为空**且**云端有书源时生效：
            //    两端都空时没有可保护的东西，正常走上传。
            if (local.sources.isEmpty() && remote.sources.isNotEmpty()) {
                return SkipToProtectSources
            }
            return UploadLocalBothChanged
        }
    }
}
