package io.legado.app.help

import io.legado.app.data.appDb
import splitties.init.appCtx
import java.io.File

/**
 * 数据库维护：回收 WAL 与整理碎片。
 *
 * 项目原先只提供 VACUUM，但 SQLite 默认使用 WAL 模式，写入先进 `-wal` 文件，
 * 长期不做 checkpoint 时该文件会持续膨胀（实测曾达 18 MB，占总占用三分之一）。
 * 仅 VACUUM 不会回收它，必须显式 `wal_checkpoint(TRUNCATE)`。
 */
object DbMaintenance {

    private const val DB_NAME = "legado.db"

    private val dbDir: File
        get() = File(appCtx.getDatabasePath(DB_NAME).parent ?: appCtx.filesDir.path)

    /**
     * 启动时调用：调小自动 checkpoint 阈值，让 WAL 不会长期膨胀。
     * 默认 1000 页（约 4MB）在频繁写入下容易被长事务拖住，这里收紧到 400 页。
     */
    fun configure() {
        runCatching {
            appDb.openHelper.writableDatabase
                .query("PRAGMA wal_autocheckpoint=400")
                .close()
        }
    }

    /**
     * 把 WAL 内容合并回主库并截断文件，回收其占用空间。
     * 耗时通常在几十毫秒内，适合在启动后台执行。
     */
    fun checkpoint(): Boolean = runCatching {
        appDb.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .close()
        true
    }.getOrDefault(false)

    /**
     * 收缩数据库：先合并 WAL，再 VACUUM 重建主库去除碎片，最后再截断一次 WAL。
     * 耗时可达数秒，只应在用户主动触发时执行。
     */
    fun shrink(): Boolean = runCatching {
        val db = appDb.openHelper.writableDatabase
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        db.execSQL("VACUUM")
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        true
    }.getOrDefault(false)

    /**
     * 数据库相关文件（主库 + WAL + SHM）总占用字节数
     */
    fun totalSize(): Long {
        if (!dbDir.exists()) return 0L
        return dbDir.listFiles()
            ?.filter { it.name.startsWith(DB_NAME) }
            ?.sumOf { it.length() }
            ?: 0L
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
