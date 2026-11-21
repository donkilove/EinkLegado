package io.legado.app.model

import android.content.Context
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource

/**
 * 音频播放状态容器。
 *
 * 墨水屏专属版本已移除音频播放器与播放服务，本对象只保留书源与书籍状态字段，
 * 供书源身份识别、书源登录、RSS 等既有代码读取；所有播放操作均为空实现。
 */
object AudioPlay {

    var book: Book? = null
    var bookSource: BookSource? = null
    var durChapter: BookChapter? = null
    var durChapterIndex = 0
    var durChapterPos = 0
    var inBookshelf = false

    @Suppress("UNUSED_PARAMETER")
    fun pause(context: Context) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun resume(context: Context) = Unit

    fun stop() = Unit
}
