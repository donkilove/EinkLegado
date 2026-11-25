package io.legado.app.help.sync

import io.legado.app.data.entities.Book

/**
 * 书架同步的传输对象。
 *
 * 为什么不直接序列化 [Book]：
 *
 * 1. **不能带 `bookUrl`**。它是设备本地路径（`content://...` 或 `/storage/...`），
 *    跨设备无意义。传过去只会得到一条点不开的书。用 DTO 把「不该传」变成类型上做不到，
 *    比依赖「记得剔除字段」可靠。
 * 2. **`Book.ReadConfig` 里有 `java.time.LocalDate`**，Gson 反射序列化它会在
 *    JDK 17 上抛 `InaccessibleObjectException`（单元测试直接失败），
 *    在 Android 上同样属于不可靠的反射路径。
 *
 * 身份是 `name + author`，与 `books` 表的唯一索引一致。
 */
data class BookSync(
    val name: String = "",
    val author: String = "",
    /** 所属分组（位掩码），跨设备有意义 */
    val group: Long = 0,
    /** 手动排序 */
    val order: Int = 0,
    val durChapterIndex: Int = 0,
    val durChapterPos: Int = 0,
    val durChapterTitle: String? = null,
    val durChapterTime: Long = 0L,
    val totalChapterNum: Int = 0,
    val customTag: String? = null,
    val customCoverUrl: String? = null,
    val customIntro: String? = null
) {

    /** 逻辑身份：与 `books` 表的 `(name, author)` 唯一索引一致 */
    fun key(): String = name + "\u0001" + author

    companion object {

        /**
         * 从本地书籍提取同步字段。
         *
         * 刻意**不包含**：`bookUrl`、`tocUrl`、`origin`、`originName`、`variable`、
         * `latestChapterTitle`、`latestChapterTime`、`lastCheckTime`、`lastCheckCount`、
         * `syncTime`、`readConfig`。
         * 前几个是设备本地信息；后几个是「上次更新书籍信息」的缓存，
         * 由各设备自行刷新，同步过去只会互相干扰。
         */
        fun from(book: Book): BookSync = BookSync(
            name = book.name,
            author = book.author,
            group = book.group,
            order = book.order,
            durChapterIndex = book.durChapterIndex,
            durChapterPos = book.durChapterPos,
            durChapterTitle = book.durChapterTitle,
            durChapterTime = book.durChapterTime,
            totalChapterNum = book.totalChapterNum,
            customTag = book.customTag,
            customCoverUrl = book.customCoverUrl,
            customIntro = book.customIntro
        )
    }
}
