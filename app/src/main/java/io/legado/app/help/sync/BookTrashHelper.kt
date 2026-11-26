package io.legado.app.help.sync

import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookTrash
import io.legado.app.help.AppWebDav
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.webdav.WebDav
import io.legado.app.utils.FileUtils
import androidx.core.net.toUri
import io.legado.app.utils.isContentScheme
import splitties.init.appCtx

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
     * 「彻底」意味着三样东西都要清掉，缺一样都会留下用户看不见的残留：
     *   1. 回收站行（数据库）
     *   2. 本地书籍文件（本地书 / WebDAV 书的本地副本）
     *   3. WebDAV 上的云端文件（仅 WebDAV 书）
     *
     * 顺序：先删文件再删行。反过来的话，删行成功而删文件失败时，
     * 这本书的记录已经没了，文件就再也没人知道该删——变成永久孤儿。
     * 现在的顺序下最坏情况是「行还在、文件已删」，用户再点一次彻底删除即可。
     *
     * 文件删除失败不影响数据库删除：用户的意图是「这本书消失」，
     * 不能因为一个删不掉的文件把记录留在回收站里。失败只记日志。
     */
    fun purge(trash: BookTrash) {
        deleteLocalFile(trash)
        deleteRemoteIfNeeded(trash)
        appDb.bookTrashDao.delete(trash)
    }

    /**
     * 清理超过保留期的条目（30 天）。
     *
     * 与手动彻底删除走同一套清理动作：保留期满即视为用户放弃这本书，
     * 本地文件与云端文件同样要删，否则回收站会变成一个只清记录、
     * 把文件永久留在磁盘上的黑洞。
     * @return 清理的条数
     */
    fun purgeExpired(now: Long = System.currentTimeMillis()): Int {
        val expired = BookTrashPolicy.expired(appDb.bookTrashDao.all, now)
        expired.forEach { purge(it) }
        return expired.size
    }

    /**
     * 清空整个回收站。
     *
     * 必须逐条走 [purge]，不能直接 `bookTrashDao.clear()`——
     * 那样只清数据库，本地文件与云端文件全部残留。
     * @return 清理的条数
     */
    fun purgeAll(): Int {
        val all = appDb.bookTrashDao.all
        all.forEach { purge(it) }
        return all.size
    }

    /**
     * 删除这本书的本地文件。
     *
     * 只处理「本地书」与「WebDAV 书」：前者文件是本地的唯一副本，
     * 后者是云端文件的本地缓存，两者都该随彻底删除一起清掉。
     * 网络书（书源）的 bookUrl 是网址，不是文件，绝不能当路径删。
     */
    private fun deleteLocalFile(trash: BookTrash) {
        val isLocalFile = trash.type and BookType.local > 0 ||
            trash.origin == BookType.localTag ||
            trash.origin.startsWith(BookType.webDavTag)
        if (!isLocalFile) return
        val bookUrl = trash.bookUrl
        if (bookUrl.isBlank()) return
        kotlin.runCatching {
            if (bookUrl.isContentScheme()) {
                android.provider.DocumentsContract.deleteDocument(
                    appCtx.contentResolver,
                    bookUrl.toUri()
                )
            } else {
                FileUtils.delete(bookUrl)
            }
        }.onFailure {
            AppLog.put("删除本地书籍文件失败\n${it.localizedMessage}", it)
        }
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
