package io.legado.app.help

import android.net.Uri
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookProgressBundle
import io.legado.app.data.entities.isAheadOf
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.sync.PendingProgressQueue
import io.legado.app.help.sync.SyncData
import io.legado.app.help.sync.SyncDecision
import io.legado.app.help.sync.SyncFingerprint
import io.legado.app.help.sync.SyncState
import io.legado.app.help.sync.UserDataSync
import io.legado.app.utils.postEvent
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.constant.EventBus
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import io.legado.app.lib.webdav.WebDavException
import io.legado.app.lib.webdav.WebDavFile
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.UrlUtil
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
    /** 书架分组 / 书签 / 书源的聚合同步文件 */
    private val syncDataUrl get() = "${rootWebDavUrl}syncData.json"

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
    /**
     * 把一本书的进度写入聚合文件并上传。
     * 遵循「进度只前进」：云端已有更靠后的进度时不覆盖，避免旧设备把新进度冲掉。
     */
    /**
     * 把一本书的进度写入聚合文件并上传。
     * 遵循「进度只前进」：云端已有更靠后的进度时不覆盖。
     * 断网或失败时进入待推送队列，等网络恢复或下次同步补传。
     */
    suspend fun uploadBookProgress(
        book: Book,
        toast: Boolean = false,
        onSuccess: (() -> Unit)? = null
    ) {
        if (!AppConfig.syncBookProgress) return
        val progress = BookProgress(book)
        if (!NetworkUtils.isAvailable()) {
            PendingProgressQueue.add(progress)
            return
        }
        try {
            if (pushProgress(progress)) {
                book.syncTime = System.currentTimeMillis()
                onSuccess?.invoke()
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            PendingProgressQueue.add(progress)
            AppLog.put("上传进度失败，已加入待推送队列\n${e.localizedMessage}", e, toast)
        }
    }

    suspend fun uploadBookProgress(bookProgress: BookProgress, onSuccess: (() -> Unit)? = null) {
        if (!AppConfig.syncBookProgress) return
        if (!NetworkUtils.isAvailable()) {
            PendingProgressQueue.add(bookProgress)
            return
        }
        try {
            if (pushProgress(bookProgress)) onSuccess?.invoke()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            PendingProgressQueue.add(bookProgress)
            AppLog.put("上传进度失败，已加入待推送队列\n${e.localizedMessage}", e)
        }
    }

    /**
     * 补传待推送队列中的进度。
     * 在网络恢复、启动同步、手动刷新时调用。
     * @return 成功补传的条数
     */
    suspend fun flushPendingProgress(): Int {
        if (!AppConfig.syncBookProgress) return 0
        if (!NetworkUtils.isAvailable()) return 0
        val pending = PendingProgressQueue.all()
        if (pending.isEmpty()) return 0
        var done = 0
        pending.forEach { progress ->
            kotlin.runCatching {
                if (pushProgress(progress)) done++
                // 无论成功还是被「只前进」拒绝，都不再需要补传这条
                PendingProgressQueue.remove(
                    BookProgressBundle.progressKey(progress.name, progress.author)
                )
            }.onFailure {
                AppLog.put("补传进度失败\n${it.localizedMessage}", it)
            }
        }
        return done
    }

    private suspend fun pushProgress(progress: BookProgress): Boolean {
        return progressMutex.withLock {
            val bundle = ensureProgressBundle() ?: BookProgressBundle()
            val existing = bundle.get(progress.name, progress.author)
            if (existing != null && !progress.isAheadOf(existing)) {
                // 云端进度更靠后，保留云端
                return@withLock false
            }
            bundle.put(progress)
            pushProgressBundle(bundle)
            true
        }
    }

    suspend fun getBookProgress(book: Book): BookProgress? {
        val bundle = ensureProgressBundle() ?: return null
        return bundle.get(book.name, book.author)
    }

    /**
     * 全量同步：下载聚合文件并与本地逐本比较，取更靠后的进度写回本地记录。
     * @return 实际更新的书籍数量，0 表示无变化或同步失败
     */
    suspend fun downloadAllBookProgress(withUserData: Boolean = false): Int {
        if (!AppConfig.syncBookProgress) return 0
        if (!NetworkUtils.isAvailable()) return 0
        authorization ?: return 0
        return try {
            // 先把离线期间积压的进度补传上去，再拉取云端
            flushPendingProgress()
            migrateOldProgressIfNeeded()
            // 云端还没有进度文件时 bundle 为 null，这是「首次同步到空服务器」的正常情况。
            // 此处不能直接 return：那会把下面的 syncUserData() 一起跳过，
            // 导致分组/书签/书源/阅读时长在空服务器上永远同步不出去。
            val bundle = fetchProgressBundle(force = true)
            var updated = 0
            if (bundle != null) {
                appDb.bookDao.all.forEach { book ->
                    if (bundle.applyTo(book)) {
                        book.syncTime = System.currentTimeMillis()
                        appDb.bookDao.update(book)
                        updated++
                    }
                }
            }
            if (withUserData) syncUserData()
            updated
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("同步阅读进度失败\n${e.localizedMessage}", e)
            0
        }
    }

    /**
     * 同步书架分组、书签、书源。
     *
     * 合并语义是「最后修改者胜」：靠 [SyncState] 记录的指纹判断哪一端自上次同步后改动过，
     * 由改动过的一端覆盖另一端。决策逻辑在 [SyncDecision.decide]（纯函数，可单测），
     * 此处只负责取数据、下载、以及按决策执行。
     */
    suspend fun syncUserData(): UserDataSync.Result {
        if (!NetworkUtils.isAvailable()) return UserDataSync.Result()
        val authorization = authorization ?: return UserDataSync.Result()
        return kotlin.runCatching {
            val local = UserDataSync.export()
            val localHash = UserDataSync.fingerprint(local)
            val lastHash = SyncState.get()
            // 升级兼容：旧版存的是 32 位 hashCode 指纹，与新版 SHA-256 必然不等，
            // 因此会自然落到「两边都改动」分支——本地优先上传，且受书源空保护。
            // 这是升级当次最保守的走向。此判断只用于留日志，不改变决策结果。
            if (SyncFingerprint.isLegacy(lastHash)) {
                AppLog.put("syncUserData 检测到旧版指纹，本次按「本地为准」处理")
            }

            val remote = kotlin.runCatching {
                val bytes = WebDav(syncDataUrl, authorization).download()
                val json = String(bytes)
                if (json.isJson()) GSON.fromJsonObject<SyncData>(json).getOrNull() else null
            }.getOrNull()

            val remoteHash = remote?.let { UserDataSync.fingerprint(it) } ?: ""
            when (val decision = SyncDecision.decide(localHash, remoteHash, lastHash, local, remote)) {
                SyncDecision.UploadLocal -> {
                    uploadSyncData(local, authorization)
                    SyncState.save(localHash)
                    AppLog.put("syncUserData 云端无数据，已上传本地")
                    UserDataSync.Result()
                }

                SyncDecision.ReplaceLocal -> {
                    val changed = UserDataSync.replaceLocal(remote!!)
                    SyncState.save(remoteHash)
                    AppLog.put(
                        "syncUserData 云端为准：分组 ${changed.groups} 书签 ${changed.bookmarks} 书源 ${changed.sources}"
                    )
                    changed
                }

                SyncDecision.UploadLocalLocalWins -> {
                    uploadSyncData(local, authorization)
                    SyncState.save(localHash)
                    AppLog.put("syncUserData 本地为准：已上传")
                    UserDataSync.Result()
                }

                SyncDecision.UploadLocalBothChanged -> {
                    uploadSyncData(local, authorization)
                    SyncState.save(localHash)
                    AppLog.put("syncUserData 两边均有改动，以本地为准上传")
                    UserDataSync.Result()
                }

                SyncDecision.SkipToProtectSources -> {
                    AppLog.put("syncUserData 本地书源为空，跳过上传以保护云端书源")
                    UserDataSync.Result()
                }
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("同步用户数据失败\n${it.localizedMessage}", it)
        }.getOrElse { UserDataSync.Result() }
    }

    private suspend fun uploadSyncData(data: SyncData, authorization: Authorization) {
        val json = GSON.toJson(data)
        WebDav(syncDataUrl, authorization).upload(json.toByteArray(), "application/json")
    }

    /**
     * 同步阅读排版设置（字体、行距、边距、主题等）。
     *
     * 保存在 files/readConfig.json（配置档案数组）与 files/shareReadConfig.json（共享配置）。
     * 以文件修改时间判定新旧，云端更新则覆盖本地并重新加载，内容相同则不做任何事。
     * @return 本地配置是否被云端覆盖
     */
    suspend fun syncReadConfig(): Boolean {
        if (!NetworkUtils.isAvailable()) return false
        val authorization = authorization ?: return false
        return kotlin.runCatching {
            val remoteFiles = kotlin.runCatching {
                WebDav(rootWebDavUrl, authorization).listFiles()
            }.getOrNull().orEmpty().associateBy { it.displayName }
            var overwritten = false
            listOf(
                ReadBookConfig.configFileName to ReadBookConfig.configFilePath
            ).forEach { (name, path) ->
                val localFile = File(path)
                val remote = remoteFiles[name]
                val remoteTime = remote?.lastModify ?: 0L
                val localTime = if (localFile.exists()) localFile.lastModified() else 0L
                if (remote != null && remoteTime > localTime) {
                    val bytes = WebDav("$rootWebDavUrl$name", authorization).download()
                    if (!localFile.exists() || !localFile.readBytes().contentEquals(bytes)) {
                        localFile.writeBytes(bytes)
                        overwritten = true
                    }
                } else if (localFile.exists() && localTime > remoteTime) {
                    WebDav("$rootWebDavUrl$name", authorization)
                        .upload(localFile.readBytes(), "application/json")
                }
            }
            if (overwritten) {
                ReadBookConfig.initConfigs()
                postEvent(EventBus.RECREATE, "")
            }
            overwritten
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("同步排版设置失败\n${it.localizedMessage}", it)
        }.getOrDefault(false)
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
