package io.legado.app.help.sync

import io.legado.app.data.entities.BookTrash

/**
 * 回收站条目的同步载荷。
 *
 * 只带 `name + author + deletedAt`：
 * - `bookUrl` 是设备本地路径，跨设备无意义；
 * - 其余字段留在本机回收站里，恢复时用本机那份即可。
 *
 * `deletedAt` 必须同步，否则各设备各自从「收到通知的时刻」起算 30 天，
 * 保留期会因同步延迟而互相错开。
 */
data class BookTrashSync(
    val name: String = "",
    val author: String = "",
    val deletedAt: Long = 0L
) {

    fun key(): String = name + "\u0001" + author

    companion object {
        fun from(trash: BookTrash): BookTrashSync =
            BookTrashSync(name = trash.name, author = trash.author, deletedAt = trash.deletedAt)
    }
}
