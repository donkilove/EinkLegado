package io.legado.app.help.sync

import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File

/**
 * 记录上次同步完成时的数据指纹。
 *
 * 用途：判断「本地自上次同步以来是否改动过」。
 * - 没改动 → 以云端为准整体替换本地，删除才能传播下来
 * - 改动过 → 上传本地，删除才能传播上去
 */
object SyncState {

    private val file by lazy { File(appCtx.filesDir, "sync_state.json") }

    var lastHash: String? = null
        private set

    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (file.exists()) {
                val json = file.readText()
                if (json.isNotBlank()) {
                    lastHash = GSON.fromJson(json, HashMap::class.java)
                        ?.get("lastHash") as? String
                }
            }
        }
    }

    fun get(): String? {
        ensureLoaded()
        return lastHash
    }

    fun save(hash: String) {
        ensureLoaded()
        lastHash = hash
        runCatching {
            file.writeText(GSON.toJson(mapOf("lastHash" to hash)))
        }
    }
}
