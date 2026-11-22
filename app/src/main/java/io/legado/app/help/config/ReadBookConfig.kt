package io.legado.app.help.config

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PageAnim
import io.legado.app.constant.PreferKey
import io.legado.app.help.DefaultData
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getFile
import io.legado.app.utils.getMeanColor
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.hexString
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.resizeAndRecycle
import splitties.init.appCtx
import java.io.File
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor

/**
 * 阅读界面配置
 */
@Suppress("ConstPropertyName")
@Keep
object ReadBookConfig {
    const val configFileName = "readConfig.json"
    const val shareConfigFileName = "shareReadConfig.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)
    val shareConfigFilePath = FileUtils.getPath(appCtx.filesDir, shareConfigFileName)
    val configList: ArrayList<Config> = arrayListOf()
    lateinit var shareConfig: Config
    var durConfig
        get() = getConfig(styleSelect)
        set(value) {
            configList[styleSelect] = value
            if (shareLayout) {
                shareConfig = value
            }
        }

    var isComic: Boolean = false
    var bg: Drawable? = null
    var bgMeanColor: Int = 0
    val textColor: Int get() = durConfig.curTextColor()
    val textAccentColor: Int get() = durConfig.curTextAccentColor()
    var isNineBgImg = false

    init {
        initConfigs()
        initShareConfig()
    }

    @Synchronized
    fun getConfig(index: Int): Config {
        if (configList.size < 5) {
            resetAll()
        }
        return configList.getOrNull(index) ?: configList[0]
    }

    fun initConfigs() {
        val configFile = File(configFilePath)
        var configs: List<Config>? = null
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                configs = GSON.fromJsonArray<Config>(json).getOrThrow()
            } catch (e: Exception) {
                AppLog.put("读取排版配置文件出错", e)
            }
        }
        (configs ?: DefaultData.readConfigs).let {
            configList.clear()
            configList.addAll(it)
        }
    }

    fun initShareConfig() {
        val configFile = File(shareConfigFilePath)
        var c: Config? = null
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                c = GSON.fromJsonObject<Config>(json).getOrThrow()
            } catch (e: Exception) {
                e.printOnDebug()
            }
        }
        shareConfig = c ?: configList.getOrNull(5) ?: Config()
    }

    fun upBg(width: Int, height: Int) {
        val drawable = durConfig.curBgDrawable(width, height)
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            bgMeanColor = drawable.bitmap.getMeanColor()
        } else if (drawable is ColorDrawable) {
            bgMeanColor = drawable.color
        }
        val tmp = bg
        bg = drawable
        if (tmp is BitmapDrawable) { //太快执行，可能还正在被使用，延时防崩溃
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                tmp.bitmap?.recycle()
            }
        }
    }

    fun save() {
        Coroutine.async {
            synchronized(this) {
                GSON.toJson(configList).let {
                    FileUtils.delete(configFilePath)
                    FileUtils.createFileIfNotExist(configFilePath).writeText(it)
                }
                GSON.toJson(shareConfig).let {
                    FileUtils.delete(shareConfigFilePath)
                    FileUtils.createFileIfNotExist(shareConfigFilePath).writeText(it)
                }
            }
        }
    }

    fun getAllPicBgStr(): ArrayList<String> {
        val list = arrayListOf<String>()
        configList.forEach {
            if (it.bgType == 2) {
                list.add(it.bgStr)
            }
            if (it.bgTypeNight == 2) {
                list.add(it.bgStrNight)
            }
            if (it.bgTypeEInk == 2) {
                list.add(it.bgStrEInk)
            }
        }
        return list
    }

    fun deleteDur(): Boolean {
        if (configList.size > 5) {
            val removeIndex = styleSelect
            configList.removeAt(removeIndex)
            if (removeIndex <= readStyleSelect) {
                readStyleSelect -= 1
            }
            if (removeIndex <= comicStyleSelect) {
                comicStyleSelect -= 1
            }
            return true
        }
        return false
    }

    fun clearBgAndCache() {
        val bgs = hashSetOf<String>()
        configList.forEach { config ->
            repeat(3) {
                config.getBgPath(it)?.let { path ->
                    bgs.add(path)
                }
            }
        }
        appCtx.externalFiles.getFile("bg").listFiles()?.forEach {
            if (!bgs.contains(it.absolutePath)) {
                it.delete()
            }
        }
        FileUtils.delete(appCtx.externalCache.getFile("readConfig"))
        val configZipPath = FileUtils.getPath(appCtx.externalCache, "readConfig.zip")
        FileUtils.delete(configZipPath)
    }

    private fun resetAll() {
        DefaultData.readConfigs.let {
            configList.clear()
            configList.addAll(it)
            save()
        }
    }

    //配置写入读取
    var readBodyToLh = appCtx.getPrefBoolean(PreferKey.readBodyToLh, true)
    var autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 10)
        set(value) {
            field = value
            appCtx.putPrefInt(PreferKey.autoReadSpeed, value)
        }
    var styleSelect: Int
        get() = if (isComic) comicStyleSelect else readStyleSelect
        set(value) {
            if (isComic) {
                comicStyleSelect = value
            } else {
                readStyleSelect = value
            }
        }
    var readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
        set(value) {
            field = value
            if (appCtx.getPrefInt(PreferKey.readStyleSelect) != value) {
                appCtx.putPrefInt(PreferKey.readStyleSelect, value)
            }
        }
    var comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect, readStyleSelect)
        set(value) {
            field = value
            if (appCtx.getPrefInt(PreferKey.comicStyleSelect) != value) {
                appCtx.putPrefInt(PreferKey.comicStyleSelect, value)
            }
        }
    var shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
        set(value) {
            field = value
            if (appCtx.getPrefBoolean(PreferKey.shareLayout) != value) {
                appCtx.putPrefBoolean(PreferKey.shareLayout, value)
            }
        }

    /**
     * 两端对齐
     */
    val textFullJustify get() = appCtx.getPrefBoolean(PreferKey.textFullJustify, true)

    /**
     * 底部对齐
     */
    val textBottomJustify get() = appCtx.getPrefBoolean(PreferKey.textBottomJustify, true)

    var hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
    var hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
    var useZhLayout = appCtx.getPrefBoolean(PreferKey.useZhLayout)

    val config get() = if (shareLayout) shareConfig else durConfig

    var bgAlpha: Int
        get() = config.bgAlpha
        set(value) {
            config.bgAlpha = value
        }

    var pageAnim: Int
        get() = config.curPageAnim()
        set(@PageAnim.Anim value) {
            config.setCurPageAnim(value)
        }

    var textFont: String
        get() = config.textFont
        set(value) {
            config.textFont = value
        }

    var textBold: Int
        get() = config.textBold
        set(value) {
            config.textBold = value
        }

    var textSize: Int
        get() = config.textSize
        set(value) {
            config.textSize = value
        }

    var letterSpacing: Float
        get() = config.letterSpacing
        set(value) {
            config.letterSpacing = value
        }

    var lineSpacingExtra: Int
        get() = config.lineSpacingExtra
        set(value) {
            config.lineSpacingExtra = value
        }

    var paragraphSpacing: Int
        get() = config.paragraphSpacing
        set(value) {
            config.paragraphSpacing = value
        }

    /**
     * 标题位置 0:居左 1:居中 2:隐藏
     */
    var titleMode: Int
        get() = config.titleMode
        set(value) {
            config.titleMode = value
        }
    var titleSize: Int
        get() = config.titleSize
        set(value) {
            config.titleSize = value
        }

    /**
     * 是否标题居中
     */
    val isMiddleTitle get() = titleMode == 1

    var titleTopSpacing: Int
        get() = config.titleTopSpacing
        set(value) {
            config.titleTopSpacing = value
        }

    var titleBottomSpacing: Int
        get() = config.titleBottomSpacing
        set(value) {
            config.titleBottomSpacing = value
        }

    var paragraphIndent: String
        get() = config.paragraphIndent
        set(value) {
            config.paragraphIndent = value
        }

    var underlineMode: Int
        get() = config.underlineMode
        set(value) {
            config.underlineMode = value
        }

    var paddingBottom: Int
        get() = config.paddingBottom
        set(value) {
            config.paddingBottom = value
        }

    var paddingLeft: Int
        get() = config.paddingLeft
        set(value) {
            config.paddingLeft = value
        }

    var paddingRight: Int
        get() = config.paddingRight
        set(value) {
            config.paddingRight = value
        }

    var paddingTop: Int
        get() = config.paddingTop
        set(value) {
            config.paddingTop = value
        }

    var headerPaddingBottom: Int
        get() = config.headerPaddingBottom
        set(value) {
            config.headerPaddingBottom = value
        }

    var headerPaddingLeft: Int
        get() = config.headerPaddingLeft
        set(value) {
            config.headerPaddingLeft = value
        }

    var headerPaddingRight: Int
        get() = config.headerPaddingRight
        set(value) {
            config.headerPaddingRight = value
        }

    var headerPaddingTop: Int
        get() = config.headerPaddingTop
        set(value) {
            config.headerPaddingTop = value
        }

    var footerPaddingBottom: Int
        get() = config.footerPaddingBottom
        set(value) {
            config.footerPaddingBottom = value
        }

    var footerPaddingLeft: Int
        get() = config.footerPaddingLeft
        set(value) {
            config.footerPaddingLeft = value
        }

    var footerPaddingRight: Int
        get() = config.footerPaddingRight
        set(value) {
            config.footerPaddingRight = value
        }

    var footerPaddingTop: Int
        get() = config.footerPaddingTop
        set(value) {
            config.footerPaddingTop = value
        }

    var showHeaderLine: Boolean
        get() = config.showHeaderLine
        set(value) {
            config.showHeaderLine = value
        }

    var showFooterLine: Boolean
        get() = config.showFooterLine
        set(value) {
            config.showFooterLine = value
        }

    fun getExportConfig(): Config {
        val exportConfig = durConfig.copy()
        if (shareLayout) {
            exportConfig.textFont = shareConfig.textFont
            exportConfig.textBold = shareConfig.textBold
            exportConfig.textSize = shareConfig.textSize
            exportConfig.letterSpacing = shareConfig.letterSpacing
            exportConfig.lineSpacingExtra = shareConfig.lineSpacingExtra
            exportConfig.paragraphSpacing = shareConfig.paragraphSpacing
            exportConfig.titleMode = shareConfig.titleMode
            exportConfig.titleSize = shareConfig.titleSize
            exportConfig.titleTopSpacing = shareConfig.titleTopSpacing
            exportConfig.titleBottomSpacing = shareConfig.titleBottomSpacing
            exportConfig.paddingBottom = shareConfig.paddingBottom
            exportConfig.paddingLeft = shareConfig.paddingLeft
            exportConfig.paddingRight = shareConfig.paddingRight
            exportConfig.paddingTop = shareConfig.paddingTop
            exportConfig.headerPaddingBottom = shareConfig.headerPaddingBottom
            exportConfig.headerPaddingLeft = shareConfig.headerPaddingLeft
            exportConfig.headerPaddingRight = shareConfig.headerPaddingRight
            exportConfig.headerPaddingTop = shareConfig.headerPaddingTop
            exportConfig.footerPaddingBottom = shareConfig.footerPaddingBottom
            exportConfig.footerPaddingLeft = shareConfig.footerPaddingLeft
            exportConfig.footerPaddingRight = shareConfig.footerPaddingRight
            exportConfig.footerPaddingTop = shareConfig.footerPaddingTop
            exportConfig.showHeaderLine = shareConfig.showHeaderLine
            exportConfig.showFooterLine = shareConfig.showFooterLine
            exportConfig.tipHeaderLeft = shareConfig.tipHeaderLeft
            exportConfig.tipHeaderMiddle = shareConfig.tipHeaderMiddle
            exportConfig.tipHeaderRight = shareConfig.tipHeaderRight
            exportConfig.tipFooterLeft = shareConfig.tipFooterLeft
            exportConfig.tipFooterMiddle = shareConfig.tipFooterMiddle
            exportConfig.tipFooterRight = shareConfig.tipFooterRight
            exportConfig.tipColor = shareConfig.tipColor
            exportConfig.headerMode = shareConfig.headerMode
            exportConfig.footerMode = shareConfig.footerMode
        }
        return exportConfig
    }

    fun import(byteArray: ByteArray): Config {
        val configZipPath = FileUtils.getPath(appCtx.externalCache, "readConfig.zip")
        FileUtils.delete(configZipPath)
        val zipFile = FileUtils.createFileIfNotExist(configZipPath)
        zipFile.writeBytes(byteArray)
        val configDir = appCtx.externalCache.getFile("readConfig")
        configDir.createFolderReplace()
        ZipUtils.unZipToPath(zipFile, configDir)
        val configFile = configDir.getFile(configFileName)
        val config: Config = GSON.fromJsonObject<Config>(configFile.readText()).getOrThrow()
        if (config.textFont.isNotEmpty()) {
            val fontName = config.textFont
            val fontPath =
                FileUtils.getPath(appCtx.externalFiles, "font", fontName)
            val fontFile = configDir.getFile(fontName)
            if (fontFile.exists()) {
                if (!FileUtils.exist(fontPath)) {
                    fontFile.copyTo(File(fontPath))
                }
                config.textFont = fontPath
            } else {
                config.textFont = ""
            }
        }
        if (config.bgType == 2) {
            val bgName = FileUtils.getName(config.bgStr)
            config.bgStr = bgName
            val bgPath = FileUtils.getPath(appCtx.externalFiles, "bg", bgName)
            if (!FileUtils.exist(bgPath)) {
                val bgFile = configDir.getFile(bgName)
                if (bgFile.exists()) {
                    bgFile.copyTo(File(bgPath))
                }
            }
            config.bgStr = bgPath
        } else if (config.bgType == 0) {
            config.bgStr.toColorInt()
        }
        if (config.bgTypeNight == 2) {
            val bgName = FileUtils.getName(config.bgStrNight)
            config.bgStrNight = bgName
            val bgPath = FileUtils.getPath(appCtx.externalFiles, "bg", bgName)
            if (!FileUtils.exist(bgPath)) {
                val bgFile = configDir.getFile(bgName)
                if (bgFile.exists()) {
                    bgFile.copyTo(File(bgPath))
                }
            }
            config.bgStrNight = bgPath
        } else if (config.bgTypeNight == 0) {
            config.bgStrNight.toColorInt()
        }
        if (config.bgTypeEInk == 2) {
            val bgName = FileUtils.getName(config.bgStrEInk)
            config.bgStrEInk = bgName
            val bgPath = FileUtils.getPath(appCtx.externalFiles, "bg", bgName)
            if (!FileUtils.exist(bgPath)) {
                val bgFile = configDir.getFile(bgName)
                if (bgFile.exists()) {
                    bgFile.copyTo(File(bgPath))
                }
            }
            config.bgStrEInk = bgPath
        } else if (config.bgTypeEInk == 0) {
            config.bgStrEInk.toColorInt()
        }
        config.curTextColor()
        config.curTextAccentColor()
        return config
    }

    @Keep
    data class Config(
        var name: String = "",
        var bgStr: String = "#EEEEEE",//白天背景
        var bgStrNight: String = "#000000",//夜间背景
        var bgStrEInk: String = "#FFFFFF",//EInk背景
        var bgAlpha: Int = 100,//背景透明度
        var bgType: Int = 0,//白天背景类型 0:颜色, 1:assets图片, 2其它图片
        var bgTypeNight: Int = 0,//夜间背景类型
        var bgTypeEInk: Int = 0,//EInk背景类型
        private var darkStatusIcon: Boolean = true,//白天是否暗色状态栏
        private var darkStatusIconNight: Boolean = false,//晚上是否暗色状态栏
        private var darkStatusIconEInk: Boolean = true,
        private var textColor: String = "#3E3D3B",//白天文字颜色
        private var textColorNight: String = "#ADADAD",//夜间文字颜色
        private var textColorEInk: String = "#000000",
        private var textAccentColor: String = "#E53935",//白天强调文字颜色
        private var textAccentColorNight: String = "#FE4D55",//夜间强调文字颜色
        private var textAccentColorEInk: String = "#000000",
        private var pageAnim: Int = 0,//翻页动画
        private var pageAnimEInk: Int = 4,
        var textFont: String = "",//字体
        var textBold: Int = 0,//是否粗体字 0:正常, 1:粗体, 2:细体
        var textSize: Int = 20,//文字大小
        var letterSpacing: Float = 0.1f,//字间距
        var lineSpacingExtra: Int = 12,//行间距
        var paragraphSpacing: Int = 2,//段距
        var titleMode: Int = 0,//标题位置 0:居左 1:居中 2:隐藏
        var titleSize: Int = 0,
        var titleTopSpacing: Int = 0,
        var titleBottomSpacing: Int = 0,
        var paragraphIndent: String = "　　",//段落缩进
        var underlineMode: Int = 0, //下划线
        var paddingBottom: Int = 6,
        var paddingLeft: Int = 16,
        var paddingRight: Int = 16,
        var paddingTop: Int = 6,
        var headerPaddingBottom: Int = 0,
        var headerPaddingLeft: Int = 16,
        var headerPaddingRight: Int = 16,
        var headerPaddingTop: Int = 0,
        var footerPaddingBottom: Int = 6,
        var footerPaddingLeft: Int = 16,
        var footerPaddingRight: Int = 16,
        var footerPaddingTop: Int = 6,
        var showHeaderLine: Boolean = false,
        var showFooterLine: Boolean = true,
        var tipHeaderLeft: Int = ReadTipConfig.time,
        var tipHeaderMiddle: Int = ReadTipConfig.none,
        var tipHeaderRight: Int = ReadTipConfig.battery,
        var tipFooterLeft: Int = ReadTipConfig.chapterTitle,
        var tipFooterMiddle: Int = ReadTipConfig.none,
        var tipFooterRight: Int = ReadTipConfig.pageAndTotal,
        var tipColor: Int = 0,
        var tipDividerColor: Int = -1,
        var headerMode: Int = 0,
        var footerMode: Int = 0
    ) {

        /**
         * 正文颜色。
         *
         * 墨水屏：不再支持在阅读界面单独设置，统一跟随主题。
         * 关键是要跟「背景色」配对：背景本身也可能被主题改成深色，
         * 如果文字色只看白天/夜间开关，就会出现浅底浅字（实测白屏）。
         * 所以这里按背景的明暗反推正文色。
         */
        fun curTextColor(): Int {
            // 注意 getPrimaryTextColor(dark) 的参数语义是「用深色文字」：
            // dark=true 取到 #DE000000（黑），dark=false 取到 #FFFFFFFF（白）。
            // 所以背景是浅色时正好传 true。
            val bg = runCatching { curBgColorInt() }.getOrDefault(bgStrEInk.toColorInt())
            return appCtx.getPrimaryTextColor(io.legado.app.utils.ColorUtils.isColorLight(bg))
        }

        /** 强调色（章节标题、链接等）。墨水屏：统一跟随主题强调色，不再单独设置。 */
        fun curTextAccentColor(): Int =
            runCatching {
                ThemeConfig.getDurConfig(appCtx).accentColor.toColorInt()
            }.getOrDefault(appCtx.accentColor)

        fun setCurStatusIconDark(isDark: Boolean) {
            when {
                AppConfig.isEInkMode -> darkStatusIconEInk = isDark
                AppConfig.isNightTheme -> darkStatusIconNight = isDark
                else -> darkStatusIcon = isDark
            }
        }

        fun curStatusIconDark(): Boolean {
            return when {
                AppConfig.isEInkMode -> darkStatusIconEInk
                AppConfig.isNightTheme -> darkStatusIconNight
                else -> darkStatusIcon
            }
        }

        fun setCurPageAnim(@PageAnim.Anim anim: Int) {
            // 墨水屏专属：只保留「滚动」与「无动画」两种翻页方式
            val animValue = if (anim == PageAnim.scrollPageAnim) {
                PageAnim.scrollPageAnim
            } else {
                PageAnim.noAnim
            }
            when {
                AppConfig.isEInkMode -> pageAnimEInk = animValue
                else -> pageAnim = animValue
            }
        }

        fun curPageAnim(): Int {
            return when {
                AppConfig.isEInkMode -> pageAnimEInk
                else -> pageAnim
            }
        }

        fun setCurBg(bgType: Int, bg: String) {
            when {
                AppConfig.isEInkMode -> {
                    // 墨水屏专属：只支持纯色背景，忽略图片背景请求
                    bgTypeEInk = 0
                    bgStrEInk = bg
                }

                AppConfig.isNightTheme -> {
                    bgTypeNight = bgType
                    bgStrNight = bg
                }

                else -> {
                    this.bgType = bgType
                    bgStr = bg
                }
            }
        }

        fun curBgStr(): String {
            // 墨水屏：背景色统一跟随主题，不再单独设置。
            // 注意不能用 R.color.background——它只跟随系统的夜间模式，
            // 与 App 自己的「夜间主题」开关无关。
            return "#${curBgColorInt().hexString}"
        }

        /**
         * 当前主题的背景色（Int）。取不到时回落到原来的墨水屏背景色。
         */
        fun curBgColorInt(): Int {
            return runCatching {
                ThemeConfig.getDurConfig(appCtx).backgroundColor.toColorInt()
            }.getOrDefault(bgStrEInk.toColorInt())
        }

        fun curBgType(): Int {
            return when {
                AppConfig.isEInkMode -> bgTypeEInk
                AppConfig.isNightTheme -> bgTypeNight
                else -> bgType
            }
        }

        fun curBgDrawable(width: Int, height: Int): Drawable {
            // 墨水屏：背景固定为纯色，图片背景相关逻辑已移除
            val color = runCatching { curBgStr().toColorInt() }.getOrDefault(0xFFFFFFFF.toInt())
            return color.toDrawable()
        }

        fun getBgPath(bgIndex: Int): String? {
            val bgType = when (bgIndex) {
                0 -> bgType
                1 -> bgTypeNight
                2 -> bgTypeEInk
                else -> error("unknown bgIndex: $bgIndex")
            }
            if (bgType != 2) {
                return null
            }
            val bgStr = when (bgIndex) {
                0 -> bgStr
                1 -> bgStrNight
                2 -> bgStrEInk
                else -> error("unknown bgIndex: $bgIndex")
            }
            val path = if (bgStr.contains(File.separator)) {
                bgStr
            } else {
                FileUtils.getPath(appCtx.externalFiles, "bg", bgStr)
            }
            return path
        }

        fun toMap() = mapOf(
            "name" to name,
            "bgStr" to bgStr,
            "bgStrNight" to bgStrNight,
            "bgStrEInk" to bgStrEInk,
            "bgAlpha" to bgAlpha,
            "bgType" to bgType,
            "bgTypeNight" to bgTypeNight,
            "bgTypeEInk" to bgTypeEInk,
            "darkStatusIcon" to darkStatusIcon,
            "darkStatusIconNight" to darkStatusIconNight,
            "darkStatusIconEInk" to darkStatusIconEInk,
            "textColor" to textColor,
            "textColorNight" to textColorNight,
            "textColorEInk" to textColorEInk,
            "textAccentColor" to textAccentColor,
            "textAccentColorNight" to textAccentColorNight,
            "textAccentColorEInk" to textAccentColorEInk,
            "pageAnim" to pageAnim,
            "pageAnimEInk" to pageAnimEInk,
            "textFont" to textFont,
            "textBold" to textBold,
            "textSize" to textSize,
            "letterSpacing" to letterSpacing,
            "lineSpacingExtra" to lineSpacingExtra,
            "paragraphSpacing" to paragraphSpacing,
            "titleMode" to titleMode,
            "titleSize" to titleSize,
            "titleTopSpacing" to titleTopSpacing,
            "titleBottomSpacing" to titleBottomSpacing,
            "paragraphIndent" to paragraphIndent,
            "underlineMode" to underlineMode,
            "paddingBottom" to paddingBottom,
            "paddingLeft" to paddingLeft,
            "paddingRight" to paddingRight,
            "paddingTop" to paddingTop,
            "headerPaddingBottom" to headerPaddingBottom,
            "headerPaddingLeft" to headerPaddingLeft,
            "headerPaddingRight" to headerPaddingRight,
            "headerPaddingTop" to headerPaddingTop,
            "footerPaddingBottom" to footerPaddingBottom,
            "footerPaddingLeft" to footerPaddingLeft,
            "footerPaddingRight" to footerPaddingRight,
            "footerPaddingTop" to footerPaddingTop,
            "showHeaderLine" to showHeaderLine,
            "showFooterLine" to showFooterLine,
            "tipHeaderLeft" to tipHeaderLeft,
            "tipHeaderMiddle" to tipHeaderMiddle,
            "tipHeaderRight" to tipHeaderRight,
            "tipFooterLeft" to tipFooterLeft,
            "tipFooterMiddle" to tipFooterMiddle,
            "tipFooterRight" to tipFooterRight,
            "tipColor" to tipColor,
            "tipDividerColor" to tipDividerColor,
            "headerMode" to headerMode,
            "footerMode" to footerMode
        )

    }

}
