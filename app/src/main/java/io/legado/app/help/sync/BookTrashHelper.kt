package io.legado.app.help.sync

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookTrash

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
     * 只删数据库行；本地文件的删除由调用方决定（UI 里会问用户是否连文件一起删）。
     */
    fun purge(trash: BookTrash) {
        appDb.bookTrashDao.delete(trash)
    }

    /**
     * 清理超过保留期的条目。
     * @return 清理的条数
     */
    fun purgeExpired(now: Long = System.currentTimeMillis()): Int {
        val expired = BookTrashPolicy.expired(appDb.bookTrashDao.all, now)
        expired.forEach { appDb.bookTrashDao.delete(it) }
        return expired.size
    }
}
