package io.legado.app.help.sync

import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookTrash
import io.legado.app.help.AppWebDav
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.webdav.WebDav

/**
 * 书架删除 / 恢复 / 清理的落库动作。
 *
 * 与 [BookTrashPolicy]（纯函数）分开：策略可单测，这里只负责翻译成 DAO 调用。
 */
object BookTrashHelper {

    /**
     * 把书移入回收站：先写回收站行，再删书架行。
     *
     * 顺序不能反：先删书架再写回收站，中途失败就彻底丢了记录。
     * 反过来的话，最坏情况是回收站里多一条、书架里也还在，用户再删一次即可。
     */
    fun moveToTrash(book: Book) {
        moveToTrash(book, System.currentTimeMillis())
    }

    /**
     * 移入回收站，并指定删除时间。
     *
     * 同步场景要沿用云端的 `deletedAt`：若各设备各自从「收到通知的时刻」起算，
     * 保留期会因同步延迟而互相错开，同一本书在两端的过期时间就不一致了。
     */
    fun moveToTrash(book: Book, deletedAt: Long) {
        appDb.bookTrashDao.insert(BookTrash.from(book, deletedAt))
        appDb.bookDao.delete(book)
    }

    /** 从回收站恢复到书架 */
    fun restore(trash: BookTrash) {
        appDb.bookDao.insert(trash.toBook())
        appDb.bookTrashDao.delete(trash)
    }

    /**
     * 彻底删除一条回收站记录。
     *
     * 除了删数据库行，若这本书来自 WebDAV，还要删掉云端文件——
     * 否则「彻底删除」只删了本地记录，云端会永久留下一个用户看不见的孤儿文件。
     * 云端删除失败不影响本地删除（本地才是用户此刻的意图），只记日志。
     */
    fun purge(trash: BookTrash) {
        appDb.bookTrashDao.delete(trash)
        deleteRemoteIfNeeded(trash)
    }

    /**
     * 清理超过保留期的条目（30 天）。
     *
     * 与手动彻底删除同样处理云端文件：保留期满即视为用户放弃这本书。
     * @return 清理的条数
     */
    fun purgeExpired(now: Long = System.currentTimeMillis()): Int {
        val expired = BookTrashPolicy.expired(appDb.bookTrashDao.all, now)
        expired.forEach {
            appDb.bookTrashDao.delete(it)
            deleteRemoteIfNeeded(it)
        }
        return expired.size
    }

    /**
     * 删除这本书在 WebDAV 上的文件。
     *
     * `origin` 形如 `webDav::<url>`，取前缀之后的地址交给 WebDav 删除。
     * 本地书（`origin == "local"`）直接跳过。
     */
    private fun deleteRemoteIfNeeded(trash: BookTrash) {
        if (!trash.origin.startsWith(BookType.webDavTag)) return
        val url = trash.origin.substring(BookType.webDavTag.length)
        if (url.isBlank()) return
        val authorization = AppWebDav.authorization ?: return
        Coroutine.async {
            kotlin.runCatching {
                WebDav(url, authorization).delete()
            }.onFailure {
                AppLog.put("删除云端书籍失败\n${it.localizedMessage}", it)
            }
        }
    }
}
