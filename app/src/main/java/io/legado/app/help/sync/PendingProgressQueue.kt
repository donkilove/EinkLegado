package io.legado.app.help.sync

import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookProgressBundle
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import java.io.File

/**
 * 待推送的阅读进度队列。
 *
 * 断网或上传失败时把进度暂存在本地，等网络恢复或下次启动同步时补传，
 * 避免「离线读了一段，进度永远丢失」。
 *
 * 以 `书名_作者` 为键去重：同一本书只保留最新一条。
 */
object PendingProgressQueue {

    private val file by lazy { File(appCtx.filesDir, "pending_progress.json") }
    private val mutex = Mutex()
    private var cache: MutableMap<String, BookProgress>? = null

    private fun loadLocked(): MutableMap<String, BookProgress> {
        cache?.let { return it }
        val loaded = mutableMapOf<String, BookProgress>()
        runCatching {
            if (file.exists()) {
                val json = file.readText()
                if (json.isJson()) {
                    GSON.fromJsonObject<Map<String, BookProgress>>(json).getOrNull()
                        ?.let { loaded.putAll(it) }
                }
            }
        }
        cache = loaded
        return loaded
    }

    private fun saveLocked(map: Map<String, BookProgress>) {
        runCatching {
            if (map.isEmpty()) {
                file.delete()
            } else {
                file.writeText(GSON.toJson(map))
            }
        }
    }

    suspend fun add(progress: BookProgress) = mutex.withLock {
        val map = loadLocked()
        val key = BookProgressBundle.progressKey(progress.name, progress.author)
        val existing = map[key]
        // 同一本书只保留更靠后的那条
        if (existing == null || (progress.durChapterIndex > existing.durChapterIndex) ||
            (progress.durChapterIndex == existing.durChapterIndex &&
                    progress.durChapterPos > existing.durChapterPos)
        ) {
            map[key] = progress
            saveLocked(map)
        }
    }

    suspend fun remove(key: String) = mutex.withLock {
        val map = loadLocked()
        if (map.remove(key) != null) saveLocked(map)
    }

    suspend fun all(): List<BookProgress> = mutex.withLock {
        loadLocked().values.toList()
    }

    suspend fun isEmpty(): Boolean = mutex.withLock {
        loadLocked().isEmpty()
    }
}
