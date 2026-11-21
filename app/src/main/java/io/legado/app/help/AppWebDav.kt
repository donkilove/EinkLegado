package io.legado.app.help

import android.net.Uri
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookProgressBundle
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.Restore
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import io.legado.app.lib.webdav.WebDavException
import io.legado.app.lib.webdav.WebDavFile
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isJson
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.removePref
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import java.io.File

/**
 * webDav初始化会访问网络,不要放到主线程
 */
object AppWebDav {
    private const val defaultWebDavUrl = "https://dav.jianguoyun.com/dav/"
    private val bookProgressUrl get() = "${rootWebDavUrl}bookProgress/"

    /**
     * 聚合进度文件：所有书的进度集中一处，一次请求即可完成全量对齐。
     * 旧格式（每本书一个 json）保留在 bookProgress/ 目录下，仅在迁移时读取。
     */
    private val progressBundleUrl get() = "${rootWebDavUrl}bookProgress.json"

    /** 内存中的聚合进度，避免重复下载 */
    private var progressBundle: BookProgressBundle? = null
    private val progressMutex = Mutex()
    private val exportsWebDavUrl get() = "${rootWebDavUrl}books/"
    private val bgWebDavUrl get() = "${rootWebDavUrl}background/"

    var authorization: Authorization? = null
        private set

    var defaultBookWebDav: RemoteBookWebDav? = null

    val isOk get() = authorization != null

    val isJianGuoYun get() = rootWebDavUrl.startsWith(defaultWebDavUrl, true)

    init {
        runBlocking {
            upConfig()
        }
    }

    private val rootWebDavUrl: String
        get() {
            val configUrl = appCtx.getPrefString(PreferKey.webDavUrl)?.trim()
            var url = if (configUrl.isNullOrEmpty()) defaultWebDavUrl else configUrl
            if (!url.endsWith("/")) url = "${url}/"
            AppConfig.webDavDir?.trim()?.let {
                if (it.isNotEmpty()) {
                    url = "${url}${it}/"
                }
            }
            return url
        }

    suspend fun upConfig() {
        kotlin.runCatching {
            authorization = null
            defaultBookWebDav = null
            val account = appCtx.getPrefString(PreferKey.webDavAccount)
            val password = appCtx.getPrefString(PreferKey.webDavPassword)
            if (!account.isNullOrEmpty() && !password.isNullOrEmpty()) {
                val mAuthorization = Authorization(account, password)
                checkAuthorization(mAuthorization)
                WebDav(rootWebDavUrl, mAuthorization).makeAsDir()
                WebDav(bookProgressUrl, mAuthorization).makeAsDir()
                WebDav(exportsWebDavUrl, mAuthorization).makeAsDir()
                WebDav(bgWebDavUrl, mAuthorization).makeAsDir()
                val rootBooksUrl = "${rootWebDavUrl}books/"
                defaultBookWebDav = RemoteBookWebDav(rootBooksUrl, mAuthorization)
                authorization = mAuthorization
            }
        }
    }

    @Throws(WebDavException::class)
    private suspend fun checkAuthorization(authorization: Authorization) {
        if (!WebDav(rootWebDavUrl, authorization).check()) {
            appCtx.removePref(PreferKey.webDavPassword)
            appCtx.toastOnUi(R.string.webdav_application_authorization_error)
            throw WebDavException(appCtx.getString(R.string.webdav_application_authorization_error))
        }
    }

    @Throws(Exception::class)
    suspend fun getBackupNames(): ArrayList<String> {
        val names = arrayListOf<String>()
        authorization?.let {
            var files = WebDav(rootWebDavUrl, it).listFiles()
            files = files.sortedWith { o1, o2 ->
                AlphanumComparator.compare(o1.displayName, o2.displayName)
            }.reversed()
            files.forEach { webDav ->
                val name = webDav.displayName
                if (name.startsWith("backup")) {
                    names.add(name)
                }
            }
        } ?: throw NoStackTraceException("webDav没有配置")
        return names
    }

    @Throws(WebDavException::class)
    suspend fun restoreWebDav(name: String) {
        authorization?.let {
            val webDav = WebDav(rootWebDavUrl + name, it)
            webDav.downloadTo(Backup.zipFilePath, true)
            FileUtils.delete(Backup.backupPath)
            ZipUtils.unZipToPath(File(Backup.zipFilePath), Backup.backupPath)
            Restore.restoreLocked(Backup.backupPath)
        }
    }

    suspend fun hasBackUp(backUpName: String): Boolean {
        authorization?.let {
            val url = "$rootWebDavUrl${backUpName}"
            return WebDav(url, it).exists()
        }
        return false
    }

    suspend fun lastBackUp(): Result<WebDavFile?> {
        return kotlin.runCatching {
            authorization?.let {
                var lastBackupFile: WebDavFile? = null
                WebDav(rootWebDavUrl, it).listFiles().reversed().forEach { webDavFile ->
                    if (webDavFile.displayName.startsWith("backup")) {
                        if (lastBackupFile == null
                            || webDavFile.lastModify > lastBackupFile.lastModify
                        ) {
                            lastBackupFile = webDavFile
                        }
                    }
                }
                lastBackupFile
            }
        }
    }

    /**
     * webDav备份
     * @param fileName 备份文件名
     */
    @Throws(Exception::class)
    suspend fun backUpWebDav(fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        authorization?.let {
            val putUrl = "$rootWebDavUrl$fileName"
            WebDav(putUrl, it).upload(Backup.zipFilePath)
        }
    }

    /**
     * 获取云端所有背景名称
     */
    private suspend fun getAllBgWebDavFiles(): Result<List<WebDavFile>> {
        return kotlin.runCatching {
            if (!NetworkUtils.isAvailable())
                throw NoStackTraceException("网络未连接")
            authorization.let {
                it ?: throw NoStackTraceException("webDav未配置")
                WebDav(bgWebDavUrl, it).listFiles()
            }
        }
    }

    /**
     * 上传背景图片
     */
    suspend fun upBgs(files: Array<File>) {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bgWebDavFiles = getAllBgWebDavFiles().getOrThrow()
            .map { it.displayName }
            .toSet()
        files.forEach {
            if (!bgWebDavFiles.contains(it.name) && it.exists()) {
                WebDav("$bgWebDavUrl${it.name}", authorization)
                    .upload(it)
            }
        }
    }

    /**
     * 下载背景图片
     */
    suspend fun downBgs() {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bgWebDavFiles = getAllBgWebDavFiles().getOrThrow()
            .map { it.displayName }
            .toSet()
    }

    @Suppress("unused")
    suspend fun exportWebDav(byteArray: ByteArray, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            authorization?.let {
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, it).upload(byteArray, "text/plain")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    suspend fun exportWebDav(uri: Uri, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            authorization?.let {
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, it).upload(uri, "text/plain")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    /**
     * 把一本书的进度写入聚合文件并上传
     */
    suspend fun uploadBookProgress(
        book: Book,
        toast: Boolean = false,
        onSuccess: (() -> Unit)? = null
    ) {
        if (!AppConfig.syncBookProgress) return
        if (!NetworkUtils.isAvailable()) return
        try {
            progressMutex.withLock {
                val bundle = ensureProgressBundle() ?: BookProgressBundle()
                bundle.put(BookProgress(book))
                pushProgressBundle(bundle)
            }
            book.syncTime = System.currentTimeMillis()
            onSuccess?.invoke()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e, toast)
        }
    }

    suspend fun uploadBookProgress(bookProgress: BookProgress, onSuccess: (() -> Unit)? = null) {
        if (!AppConfig.syncBookProgress) return
        if (!NetworkUtils.isAvailable()) return
        try {
            progressMutex.withLock {
                val bundle = ensureProgressBundle() ?: BookProgressBundle()
                bundle.put(bookProgress)
                pushProgressBundle(bundle)
            }
            onSuccess?.invoke()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e)
        }
    }

    /**
     * 取一本书的云端进度（来自聚合文件）
     */
    suspend fun getBookProgress(book: Book): BookProgress? {
        val bundle = ensureProgressBundle() ?: return null
        return bundle.get(book.name, book.author)
    }

    /**
     * 全量同步：下载聚合文件并与本地逐本比较，取更靠后的进度写回本地记录。
     * @return 实际更新的书籍数量，0 表示无变化或同步失败
     */
    suspend fun downloadAllBookProgress(): Int {
        if (!AppConfig.syncBookProgress) return 0
        if (!NetworkUtils.isAvailable()) return 0
        authorization ?: return 0
        return try {
            migrateOldProgressIfNeeded()
            val bundle = fetchProgressBundle(force = true) ?: return 0
            var updated = 0
            appDb.bookDao.all.forEach { book ->
                if (bundle.applyTo(book)) {
                    book.syncTime = System.currentTimeMillis()
                    appDb.bookDao.update(book)
                    updated++
                }
            }
            updated
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("同步阅读进度失败\n${e.localizedMessage}", e)
            0
        }
    }

    private suspend fun ensureProgressBundle(): BookProgressBundle? {
        progressBundle?.let { return it }
        return fetchProgressBundle(force = true)
    }

    /**
     * 下载聚合进度文件
     * @param force 为 true 时忽略内存缓存
     */
    private suspend fun fetchProgressBundle(force: Boolean): BookProgressBundle? {
        val authorization = authorization ?: return null
        if (!force) progressBundle?.let { return it }
        return kotlin.runCatching {
            val bytes = WebDav(progressBundleUrl, authorization).download()
            val json = String(bytes)
            if (!json.isJson()) return@runCatching null
            GSON.fromJsonObject<BookProgressBundle>(json).getOrNull()?.also {
                progressBundle = it
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("拉取聚合进度失败\n${it.localizedMessage}", it)
        }.getOrNull()
    }

    private suspend fun pushProgressBundle(bundle: BookProgressBundle) {
        val authorization = authorization ?: return
        val json = GSON.toJson(bundle)
        WebDav(progressBundleUrl, authorization).upload(json.toByteArray(), "application/json")
        progressBundle = bundle
    }

    /**
     * 首次升级迁移：把「每本书一个 json」的旧格式合并进聚合文件。
     * 迁移后旧文件保留不删，后续只读写聚合文件。
     */
    private suspend fun migrateOldProgressIfNeeded() {
        val authorization = authorization ?: return
        if (fetchProgressBundle(force = true) != null) return
        val files = kotlin.runCatching {
            WebDav(bookProgressUrl, authorization).listFiles()
        }.getOrNull() ?: return
        val jsonFiles = files.filter { it.displayName.endsWith(".json", true) }
        if (jsonFiles.isEmpty()) return
        val bundle = BookProgressBundle()
        jsonFiles.forEach { file ->
            kotlin.runCatching {
                val bytes = WebDav("$bookProgressUrl${file.displayName}", authorization).download()
                val json = String(bytes)
                if (json.isJson()) {
                    GSON.fromJsonObject<BookProgress>(json).getOrNull()?.let { bundle.merge(it) }
                }
            }
        }
        if (bundle.books.isNotEmpty()) {
            pushProgressBundle(bundle)
            AppLog.put("进度迁移完成，共 ${bundle.books.size} 本")
        }
    }

}
