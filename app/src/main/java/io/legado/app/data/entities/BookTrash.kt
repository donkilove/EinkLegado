package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 书架回收站。
 *
 * 为什么单独一张表，而不是给 `books` 加 `isDeleted` 标记列：
 * `BookDao` 里有十几处查询（`flowAll` / `flowRoot` / `flowLocal` / `allBookUrls` …），
 * 加标记列必须逐一补 `where` 条件，漏一处就会让已删除的书重新出现在书架上。
 * 把行搬到独立表里，书架查询天然看不到它，无需改动任何既有查询。
 *
 * 字段是 `Book` 的**显式子集**，不用 JSON 序列化整行：
 * `Book.ReadConfig` 内含 `java.time.LocalDate`，Gson 反射序列化在 JDK 17 上直接失败
 * （实测 `InaccessibleObjectException`），在 Android 上也属不可靠路径。
 */
@Entity(
    tableName = "book_trash",
    indices = [Index(value = ["deletedAt"])]
)
data class BookTrash(
    @PrimaryKey
    var bookUrl: String = "",
    var name: String = "",
    var author: String = "",
    var origin: String = "",
    var originName: String = "",
    @ColumnInfo(defaultValue = "0")
    var type: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var `group`: Long = 0,
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var durChapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var durChapterPos: Int = 0,
    var durChapterTitle: String? = null,
    @ColumnInfo(defaultValue = "0")
    var durChapterTime: Long = 0,
    @ColumnInfo(defaultValue = "0")
    var totalChapterNum: Int = 0,
    var latestChapterTitle: String? = null,
    @ColumnInfo(defaultValue = "0")
    var latestChapterTime: Long = 0,
    var kind: String? = null,
    var coverUrl: String? = null,
    var intro: String? = null,
    var customTag: String? = null,
    var customCoverUrl: String? = null,
    var customIntro: String? = null,
    /** 移入回收站的时间，30 天后才允许物理删除 */
    var deletedAt: Long = System.currentTimeMillis()
) {

    /** 逻辑身份：与 `books` 表的 `(name, author)` 唯一索引一致 */
    fun key(): String = name + "\u0001" + author

    /**
     * 还原成可写回书架的 [Book]。
     *
     * 只恢复书架与阅读必需字段；`readConfig`（每本书的排版覆盖）不保存，
     * 恢复后回落到全局排版设置。这是刻意的取舍：保存它需要序列化 LocalDate，
     * 而排版覆盖的损失远小于「回收站整体不可用」。
     */
    fun toBook(): Book = Book(
        bookUrl = bookUrl,
        name = name,
        author = author,
        origin = origin,
        originName = originName,
        type = type,
        group = `group`,
        order = order,
        durChapterIndex = durChapterIndex,
        durChapterPos = durChapterPos,
        durChapterTitle = durChapterTitle,
        durChapterTime = durChapterTime,
        totalChapterNum = totalChapterNum,
        latestChapterTitle = latestChapterTitle,
        latestChapterTime = latestChapterTime,
        kind = kind,
        coverUrl = coverUrl,
        intro = intro,
        customTag = customTag,
        customCoverUrl = customCoverUrl,
        customIntro = customIntro
    )

    companion object {

        fun from(book: Book, deletedAt: Long = System.currentTimeMillis()): BookTrash = BookTrash(
            bookUrl = book.bookUrl,
            name = book.name,
            author = book.author,
            origin = book.origin,
            originName = book.originName,
            type = book.type,
            `group` = book.group,
            order = book.order,
            durChapterIndex = book.durChapterIndex,
            durChapterPos = book.durChapterPos,
            durChapterTitle = book.durChapterTitle,
            durChapterTime = book.durChapterTime,
            totalChapterNum = book.totalChapterNum,
            latestChapterTitle = book.latestChapterTitle,
            latestChapterTime = book.latestChapterTime,
            kind = book.kind,
            coverUrl = book.coverUrl,
            intro = book.intro,
            customTag = book.customTag,
            customCoverUrl = book.customCoverUrl,
            customIntro = book.customIntro,
            deletedAt = deletedAt
        )
    }
}
