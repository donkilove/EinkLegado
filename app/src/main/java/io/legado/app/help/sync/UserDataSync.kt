package io.legado.app.help.sync

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReadRecord
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule

/**
 * 用户数据同步快照：书架分组、书签、书源。
 *
 * 合并语义是「最后修改者胜」：靠 [SyncState] 里记录的指纹判断哪一端自上次同步后改动过，
 * 由改动过的一端覆盖另一端。这样用户在两台设备上新增或删除都能正确传播。
 *
 * 唯一的例外是书源：若一侧书源列表为空而另一侧有数据，视为异常（如首次安装、数据损坏），
 * 不会用空列表去覆盖对方的书源。
 */
data class SyncData(
    val version: Int = 3,
    val groups: List<BookGroup> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val sources: List<BookSource> = emptyList(),

    // ---------------------------------------------------------------- v3 新增
    //
    // 这些字段**必须可空且默认 null**，不能默认 emptyList()。
    //
    // 旧版本客户端上传的 v2 JSON 里没有这些键。若默认成空列表，解析结果是
    // 「云端该类目确实为空」，同步就会用空列表覆盖本地，把用户的 RSS、
    // 替换规则、字典全部清空——升级当次的数据丢失事故。
    //
    // null 表示「这份载荷不携带该类目」，同步时整体跳过；
    // 空列表才是「云端确实没有」，允许删除传播。
    val books: List<BookSync>? = null,
    /** 回收站条目：删除靠它跨设备传播，而不是靠 books 差异 */
    val bookTrash: List<BookTrashSync>? = null,
    val rssSources: List<RssSource>? = null,
    val replaceRules: List<ReplaceRule>? = null,
    val dictRules: List<DictRule>? = null,
    val httpTTS: List<HttpTTS>? = null,
    val txtTocRules: List<TxtTocRule>? = null,
    val servers: List<Server>? = null,
    val rssStars: List<RssStar>? = null,
    val searchKeywords: List<SearchKeyword>? = null,
    val keyboardAssists: List<KeyboardAssist>? = null,
    val readRecords: List<ReadRecord>? = null
) {
    fun isEmpty(): Boolean = groups.isEmpty() && bookmarks.isEmpty() && sources.isEmpty()
            && books.isNullOrEmpty() && bookTrash.isNullOrEmpty() && rssSources.isNullOrEmpty()
            && replaceRules.isNullOrEmpty() && dictRules.isNullOrEmpty()
            && httpTTS.isNullOrEmpty() && txtTocRules.isNullOrEmpty()
            && servers.isNullOrEmpty() && rssStars.isNullOrEmpty()
            && searchKeywords.isNullOrEmpty() && keyboardAssists.isNullOrEmpty()
            && readRecords.isNullOrEmpty()

    /**
     * 规范化顺序：保证同一份数据无论插入先后，算出的指纹一致。
     *
     * **null 必须保持 null**：不能把「载荷不携带该类目」规范化成空列表，
     * 否则指纹会把两种不同语义算成同一个，同步决策随之失真。
     */
    fun normalized(): SyncData = copy(
        groups = groups.sortedBy { it.groupId },
        bookmarks = bookmarks.sortedBy { it.time },
        sources = sources.sortedBy { it.bookSourceUrl },
        books = books?.sortedWith(compareBy({ it.name }, { it.author })),
        bookTrash = bookTrash?.sortedWith(compareBy({ it.name }, { it.author })),
        rssSources = rssSources?.sortedBy { it.sourceUrl },
        replaceRules = replaceRules?.sortedBy { SyncKeys.replaceRuleKey(it) },
        dictRules = dictRules?.sortedBy { it.name },
        httpTTS = httpTTS?.sortedBy { it.name },
        txtTocRules = txtTocRules?.sortedBy { it.name },
        servers = servers?.sortedBy { it.name },
        rssStars = rssStars?.sortedWith(compareBy({ it.origin }, { it.link })),
        searchKeywords = searchKeywords?.sortedBy { it.word },
        keyboardAssists = keyboardAssists?.sortedWith(compareBy({ it.type }, { it.key })),
        readRecords = readRecords?.sortedWith(compareBy({ it.deviceId }, { it.bookName }))
    )
}

object UserDataSync {

    data class Result(
        val groups: Int = 0,
        val bookmarks: Int = 0,
        val sources: Int = 0,
        val books: Int = 0,
        val rssSources: Int = 0,
        val replaceRules: Int = 0,
        val dictRules: Int = 0,
        val httpTTS: Int = 0,
        val txtTocRules: Int = 0,
        val servers: Int = 0,
        val rssStars: Int = 0,
        val searchKeywords: Int = 0,
        val keyboardAssists: Int = 0,
        val readRecords: Int = 0,
        val bookTrash: Int = 0
    ) {
        val total: Int get() = groups + bookmarks + sources + books + rssSources +
                replaceRules + dictRules + httpTTS + txtTocRules + servers +
                rssStars + searchKeywords + keyboardAssists + readRecords + bookTrash
    }

    /**
     * 导出本地全量数据
     */
    fun export(): SyncData = SyncData(
        groups = appDb.bookGroupDao.all,
        bookmarks = appDb.bookmarkDao.all,
        sources = appDb.bookSourceDao.all,
        // v3：以下类目此前只有「备份」才管，现在纳入同步。
        // 每一项都用 runCatching 兜底：单个表读取失败不应让整次同步失败，
        // 失败时给 null（等价于「本次不携带该类目」），比给空列表安全得多。
        books = runCatching { appDb.bookDao.all.map { BookSync.from(it) } }.getOrNull(),
        bookTrash = runCatching { appDb.bookTrashDao.all.map { BookTrashSync.from(it) } }.getOrNull(),
        rssSources = runCatching { appDb.rssSourceDao.all }.getOrNull(),
        replaceRules = runCatching { appDb.replaceRuleDao.all }.getOrNull(),
        dictRules = runCatching { appDb.dictRuleDao.all }.getOrNull(),
        httpTTS = runCatching { appDb.httpTTSDao.all }.getOrNull(),
        txtTocRules = runCatching { appDb.txtTocRuleDao.all }.getOrNull(),
        servers = runCatching { appDb.serverDao.all }.getOrNull(),
        rssStars = runCatching { appDb.rssStarDao.all }.getOrNull(),
        searchKeywords = runCatching { appDb.searchKeywordDao.all }.getOrNull(),
        keyboardAssists = runCatching { appDb.keyboardAssistsDao.all }.getOrNull(),
        readRecords = runCatching { appDb.readRecordDao.all }.getOrNull()
    )

    /**
     * 数据指纹：用于判断本地自上次同步后是否改动过。
     *
     * 实现在 [SyncFingerprint]，此处保留方法名以兼容既有调用点。
     */
    fun fingerprint(data: SyncData): String = SyncFingerprint.of(data)

    /**
     * 用云端数据整体覆盖本地：既补上云端新增，也删除云端已删的条目
     *
     * 差异计算在 [SyncPlan.planReplace]（纯函数，可单测），此处只负责把计划翻译成 DAO 调用。
     * @return 发生变化的条目数
     */
    fun replaceLocal(remote: SyncData): Result {
        val local = export()
        val plan = SyncPlan.planReplace(local, remote)
        var groupCount = 0
        var bookmarkCount = 0
        var sourceCount = 0

        runCatching {
            plan.groupsToDelete.forEach { groupId ->
                local.groups.firstOrNull { it.groupId == groupId }?.let {
                    appDb.bookGroupDao.delete(it)
                    groupCount++
                }
            }
            plan.groupsToInsert.forEach {
                appDb.bookGroupDao.insert(it)
                groupCount++
            }
        }

        runCatching {
            val localByKey = local.bookmarks.associateBy { SyncPlan.bookmarkKey(it) }
            plan.bookmarksToDelete.forEach { bookmark ->
                localByKey[SyncPlan.bookmarkKey(bookmark)]?.let {
                    appDb.bookmarkDao.delete(it)
                    bookmarkCount++
                }
            }
            plan.bookmarksToInsert.forEach {
                appDb.bookmarkDao.insert(it)
                bookmarkCount++
            }
        }

        runCatching {
            val localByUrl = local.sources.associateBy { it.bookSourceUrl }
            plan.sourcesToDelete.forEach { url ->
                localByUrl[url]?.let {
                    appDb.bookSourceDao.delete(it)
                    sourceCount++
                }
            }
            plan.sourcesToInsert.forEach {
                appDb.bookSourceDao.insert(it)
                sourceCount++
            }
        }

        val v3 = applyV3(remote)

        return Result(
            groups = groupCount,
            bookmarks = bookmarkCount,
            sources = sourceCount,
            books = v3.books,
            rssSources = v3.rssSources,
            replaceRules = v3.replaceRules,
            dictRules = v3.dictRules,
            httpTTS = v3.httpTTS,
            txtTocRules = v3.txtTocRules,
            servers = v3.servers,
            rssStars = v3.rssStars,
            searchKeywords = v3.searchKeywords,
            keyboardAssists = v3.keyboardAssists,
            readRecords = v3.readRecords
        )
    }

    /**
     * 应用 v3 新增的类目。
     *
     * 每个类目都遵循同一条铁律：**云端字段为 null 就整体跳过**。
     * 旧客户端上传的载荷没有这些键，若当成空列表处理，就会把本地数据删光。
     *
     * 书源与 RSS 源额外开启 `keepWhenRemoteEmpty`：这两类是重资产，
     * 云端为空更可能是「新装设备还没配」而不是「用户全删了」，宁可不动。
     */
    private fun applyV3(remote: SyncData): Result {
        val local = export()
        var rssSourceCount = 0
        var dictRuleCount = 0
        var httpTTSCount = 0
        var txtTocRuleCount = 0
        var serverCount = 0
        var rssStarCount = 0
        var searchKeywordCount = 0
        var keyboardAssistCount = 0

        // 书架：只更新已存在的书，绝不新增。
        // 新增会造出 bookUrl 为空、点不开的僵尸条目；删除由回收站传播，不走这里。
        var bookCount = 0
        runCatching {
            val remoteBooks = remote.books ?: return@runCatching
            val localByKey = local.books.orEmpty().associateBy { it.key() }
            remoteBooks.forEach { remoteBook ->
                val localBook = localByKey[remoteBook.key()] ?: return@forEach
                val book = appDb.bookDao.getBook(localBook.name, localBook.author) ?: return@forEach
                // 进度只前进，不因同步回退
                if (remoteBook.durChapterIndex > book.durChapterIndex ||
                    (remoteBook.durChapterIndex == book.durChapterIndex &&
                            remoteBook.durChapterPos > book.durChapterPos)
                ) {
                    book.durChapterIndex = remoteBook.durChapterIndex
                    book.durChapterPos = remoteBook.durChapterPos
                    book.durChapterTitle = remoteBook.durChapterTitle
                    book.durChapterTime = remoteBook.durChapterTime
                }
                book.group = remoteBook.group
                book.order = remoteBook.order
                book.totalChapterNum = remoteBook.totalChapterNum
                book.customTag = remoteBook.customTag
                book.customCoverUrl = remoteBook.customCoverUrl
                book.customIntro = remoteBook.customIntro
                appDb.bookDao.update(book)
                bookCount++
            }
        }

        runCatching {
            val diff = SyncMerge.diff(local.rssSources, remote.rssSources, keepWhenRemoteEmpty = true) {
                it.sourceUrl
            }
            diff.toDelete.forEach { appDb.rssSourceDao.delete(it) }
            diff.toInsert.forEach { appDb.rssSourceDao.insert(it) }
            rssSourceCount = diff.toDelete.size + diff.toInsert.size
        }

        var replaceRuleCount = 0
        runCatching {
            val diff = SyncMerge.diff(local.replaceRules, remote.replaceRules) {
                SyncKeys.replaceRuleKey(it)
            }
            diff.toDelete.forEach { appDb.replaceRuleDao.delete(it) }
            // 插入时保留云端的 isEnabled / order，用户要的是两端完全一致
            diff.toInsert.forEach { appDb.replaceRuleDao.insert(it) }
            replaceRuleCount = diff.toDelete.size + diff.toInsert.size
        }

        runCatching {
            val diff = SyncMerge.diff(local.dictRules, remote.dictRules) { it.name }
            diff.toDelete.forEach { appDb.dictRuleDao.delete(it) }
            diff.toInsert.forEach { appDb.dictRuleDao.insert(it) }
            dictRuleCount = diff.toDelete.size + diff.toInsert.size
        }

        runCatching {
            val diff = SyncMerge.diff(local.httpTTS, remote.httpTTS) { it.name }
            diff.toDelete.forEach { appDb.httpTTSDao.delete(it) }
            diff.toInsert.forEach { appDb.httpTTSDao.insert(it) }
            httpTTSCount = diff.toDelete.size + diff.toInsert.size
        }

        runCatching {
            val diff = SyncMerge.diff(local.txtTocRules, remote.txtTocRules) { it.name }
            diff.toDelete.forEach { appDb.txtTocRuleDao.delete(it) }
            diff.toInsert.forEach { appDb.txtTocRuleDao.insert(it) }
            txtTocRuleCount = diff.toDelete.size + diff.toInsert.size
        }

        runCatching {
            val diff = SyncMerge.diff(local.servers, remote.servers) { it.name }
            diff.toDelete.forEach { appDb.serverDao.delete(it) }
            diff.toInsert.forEach { appDb.serverDao.insert(it) }
            serverCount = diff.toDelete.size + diff.toInsert.size
        }

        runCatching {
            val diff = SyncMerge.diff(local.rssStars, remote.rssStars, keepWhenRemoteEmpty = true) {
                it.origin + "\u0001" + it.link
            }
            diff.toDelete.forEach { appDb.rssStarDao.delete(it.origin, it.link) }
            diff.toInsert.forEach { appDb.rssStarDao.insert(it) }
            rssStarCount = diff.toDelete.size + diff.toInsert.size
        }

        runCatching {
            val diff = SyncMerge.diff(local.searchKeywords, remote.searchKeywords) { it.word }
            diff.toDelete.forEach { appDb.searchKeywordDao.delete(it) }
            diff.toInsert.forEach { appDb.searchKeywordDao.insert(it) }
            searchKeywordCount = diff.toDelete.size + diff.toInsert.size
        }

        runCatching {
            val diff = SyncMerge.diff(local.keyboardAssists, remote.keyboardAssists) {
                it.type.toString() + "\u0001" + it.key
            }
            diff.toDelete.forEach { appDb.keyboardAssistsDao.delete(it) }
            diff.toInsert.forEach { appDb.keyboardAssistsDao.insert(it) }
            keyboardAssistCount = diff.toDelete.size + diff.toInsert.size
        }

        // 回收站：删除的跨设备传播靠这里。
        // 云端有、本地书架还有、且本地回收站没有 → 说明别的设备删了它，本机跟着移入。
        // 反向（本地恢复了但云端还有）由 SyncDecision 的「本地已改动 → 上传本地」处理，
        // 不能在这里自动恢复，否则用户的恢复操作会被下一次同步抹掉。
        var trashCount = 0
        runCatching {
            val remoteTrash = remote.bookTrash ?: return@runCatching
            val localTrashKeys = local.bookTrash.orEmpty().mapTo(HashSet()) { it.key() }
            remoteTrash.forEach { remoteItem ->
                if (remoteItem.key() in localTrashKeys) return@forEach
                val book = appDb.bookDao.getBook(remoteItem.name, remoteItem.author) ?: return@forEach
                // 沿用云端的删除时间，避免各设备各自起算导致保留期错开
                BookTrashHelper.moveToTrash(book, remoteItem.deletedAt)
                trashCount++
            }
        }

        // 阅读记录：按 (deviceId, bookName) 合并，取较大 readTime。
        // 各设备只写自己那一行，因此「求和」天然不会重复计数。
        var readRecordCount = 0
        runCatching {
            val remoteRecords = remote.readRecords ?: return@runCatching
            val localByKey = local.readRecords.orEmpty()
                .associateBy { it.deviceId + "\u0001" + it.bookName }
            remoteRecords.forEach { remoteRecord ->
                val key = remoteRecord.deviceId + "\u0001" + remoteRecord.bookName
                val localRecord = localByKey[key]
                if (localRecord == null || remoteRecord.readTime > localRecord.readTime) {
                    appDb.readRecordDao.insert(remoteRecord)
                    readRecordCount++
                }
            }
        }

        return Result(
            books = bookCount,
            rssSources = rssSourceCount,
            replaceRules = replaceRuleCount,
            dictRules = dictRuleCount,
            httpTTS = httpTTSCount,
            txtTocRules = txtTocRuleCount,
            servers = serverCount,
            rssStars = rssStarCount,
            searchKeywords = searchKeywordCount,
            keyboardAssists = keyboardAssistCount,
            readRecords = readRecordCount,
            bookTrash = trashCount
        )
    }
}
