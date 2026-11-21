package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource

/**
 * 视频播放状态容器。
 *
 * 墨水屏专属版本已移除视频播放器、悬浮窗与播放服务，本对象只保留书源与书籍状态字段，
 * 供书源识别、书源登录与备份恢复（配置文件名）使用。
 */
object VideoPlay {

    /** 旧版本视频播放配置的 SharedPreferences 名，备份恢复仍需读取 */
    const val VIDEO_PREF_NAME = "video_config"

    var source: BookSource? = null
    var book: Book? = null
    var chapter: BookChapter? = null
}
