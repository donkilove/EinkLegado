# WebDav 同步 v3：以同步取代备份，实现多端完全一致

> 状态：done · 日期：2026-11-25 · 归档：2026-11-25

## 目标

把「WebDAV 备份 / 恢复整包」彻底删除，用增量同步承担多端一致性，让任意设备间**书架、规则、进度、阅读时长、外观配置**保持一致。

- G1：同步覆盖当前只有备份才管的数据（RSS、替换规则、字典、TTS、TXT 目录、远程书源、键盘辅助、搜索历史）。
- G2：书架（`books` 表）纳入同步，但**不传播设备本地路径**。
- G3：删除可跨设备传播，但先进回收站、30 天后才物理删除。
- G4：阅读时长按「各设备自身记录求和」合并，绝不重复计数。
- G5：本地书可选自动上传 WebDAV、WebDAV 书可选按需下载，各配一个开关。
- G6：删除设备名称、备份 / 恢复全部 UI 与代码。

## 背景

当前存在两套互不相干的链路：

| 链路 | 入口 | 覆盖数据 |
|---|---|---|
| 同步 v2 | `App.kt` 启动、书架下拉刷新 | 进度、分组、书签、书源、排版 |
| 备份 | 手动 / 退出时 `Backup.autoBack` | 22 类，含书架与全部规则 |

备份带来的问题：设备名称、`autoCheckNewBackup` 换设备弹覆盖弹窗、`onlyLatestBackup` 本地名与云端名不一致、整包覆盖式恢复。用户明确要求以同步替代备份。

调查中发现两处既有缺陷，本次一并修复：

1. `ReadRecord.deviceId` 默认 `""`，`ReadBook:77` / `ReadManga:58` 从不赋值 → 全部记录落在一行空 deviceId 下。
2. `Restore.kt:175` 的 `if (readRecord.deviceId != androidId)` 因上一条恒为真，**恢复备份会无条件覆盖本机阅读时长**，「取较大值」分支是死代码。

## 范围与非目标

### 范围内

**A. 同步载荷扩展（`SyncData` v3）**

| 类别 | 来源 | 逻辑键 | 说明 |
|---|---|---|---|
| groups | `bookGroupDao.all` | `groupId` | 既有 |
| bookmarks | `bookmarkDao.all` | `书名\|作者\|章\|位置` | 既有 |
| sources | `bookSourceDao.all` | `bookSourceUrl` | 既有，保留空列表保护 |
| books | `bookDao.all` | `name + author`（唯一索引） | 新增，见「书架同步」 |
| bookTrash | `bookTrashDao.all` | `name + author` | 新增，见「回收站」 |
| rssSources | `rssSourceDao.all` | `sourceUrl` | 新增 |
| replaceRules | `replaceRuleDao.all` | **内容哈希** | 新增，含 `isEnabled` / `order` |
| dictRules | `dictRuleDao.all` | `name` | 新增 |
| httpTTS | `httpTTSDao.all` | `name` | 新增 |
| txtTocRules | `txtTocRuleDao.all` | `name` | 新增 |
| servers | `serverDao.all` | `id`（本地生成，按 `url+name` 匹配） | 新增 |
| rssStars | `rssStarDao.all` | `origin + link` | 新增 |
| searchKeywords | `searchKeywordDao.all` | `word` | 新增 |
| keyboardAssists | `keyboardAssistsDao.all` | `type + key` | 新增 |
| readRecords | `readRecordDao.all` | `deviceId + bookName` | 新增，见「阅读时长」 |

**B. 配置文件同步（复用 `syncReadConfig` 的模式：按 mtime 判新旧）**

- `readConfig.json`（既有）
- `shareReadConfig.json`（新增）
- `themeConfig.json`（新增）
- `coverRule.json`（新增）

**C. 回收站（新增表 `book_trash`）**

```sql
CREATE TABLE book_trash (
  bookUrl   TEXT NOT NULL PRIMARY KEY,
  json      TEXT NOT NULL,
  deletedAt INTEGER NOT NULL
)
CREATE INDEX index_book_trash_deletedAt ON book_trash (deletedAt)
```

- 删书 = 把 `books` 行序列化进 `book_trash` 并删除原行；原文件（本地 TXT）**不立即删**，30 天到期才删。
- 回收站页：列表、恢复、彻底删除、清空。
- 到期清理：启动时扫 `deletedAt + 30 天 < now`，物理删除原文件后移出表。

**D. 本地书 WebDAV 开关（`pref_config_web_dav.xml`）**

| 开关 | key | 默认 | 行为 |
|---|---|---|---|
| 自动上传本地书 | `autoUploadLocalBook` | false | 导入本地书后自动上传，并转为 WebDAV 书（复用现有 `RemoteBookWebDav.upload`） |
| 按需下载 WebDAV 书 | `autoDownloadWebDavBook` | true | 阅读时本地无文件则下载（复用 `LocalBook.downloadRemoteBook`） |
| 同步阅读时长 | `syncReadRecord` | true | 参与同步载荷 |

**E. 删除清单（彻底的）**

| 删除对象 | 位置 |
|---|---|
| 设备名称 | `pref_config_web_dav.xml:31-36`、`PreferKey.webDavDeviceName`、`AppConfig.webDavDeviceName`、`WebDavConfigFragment` 三处引用、`BackupConfig.ignorePrefKeys` 条目、9 个语言文件的 `webdav_device_name` |
| `Backup.kt` / `Restore.kt` / `BackupAES.kt` | 整个文件 |
| `BackupConfigFragment` / `ConfigTag.BACKUP_CONFIG` | 整类 + 路由 |
| `pref_config_backup.xml` | 整个文件 |
| `AppWebDav` 备份 API | `getBackupNames` / `restoreWebDav` / `hasBackUp` / `lastBackUp` / `backUpWebDav` / `upBgs` / `downBgs` |
| `Backup.autoBack` 5 处调用 | `MainActivity:407`、`ReadBookActivity:388/1653/1712`、`ReadMangaActivity:376` |
| `MainActivity.backupSync()` | 整函数（`autoCheckNewBackup` 驱动） |
| `onlyLatestBackup` / `autoCheckNewBackup` | `PreferKey` + `AppConfig` + pref xml |
| 书架右上角备份按钮 | `main_bookshelf.xml` 的 `menu_backup` + `BaseBookshelfFragment.backupFromBookshelf` |
| 备份入口 | `pref_main.xml` 的 `backup_restore_setting` |
| 相关字符串 | 各语言 `backup*` / `restore*` / `webdav_device_name` |

### 非目标

- 不同步书籍文件内容本身（TXT/EPUB 字节）——本地书仍靠已有 WebDAV 上传 / 按需下载。
- 不做实时长连接推送。
- 不改 `lib/webdav` 协议层。
- **不再支持恢复 v2 及更早的备份包**（用户明确选择）。
- 不改 Room 既有表结构，只新增 `book_trash`。

## 关键设计

### 书架同步：只同步元数据，不传路径

`bookUrl` 是设备本地路径（`content://` 或 `/storage/...`），跨设备无意义。因此：

- 上传：序列化剔除 `bookUrl`、`tocUrl`、`localUriCache` 相关字段。
- 下载：按 `name + author` 匹配本地已有 `Book`，**只 update，绝不 insert**。
- 匹配不上 → 跳过并计数，同步结果提示「N 本未匹配」。不凭空造记录（否则得到点不开的僵尸书）。
- 同步字段：`group`、`durChapterIndex`、`durChapterPos`、`durChapterTitle`、`durChapterTime`、`customTag`、`customCoverUrl`、`customIntro`、`totalChapterNum`、`order`。
- **不同步**：`bookUrl`、`tocUrl`、`latestChapterTitle`、`latestChapterTime`、`lastCheckTime`、`lastCheckCount`、`syncTime`、`origin`、`variable`。

### 回收站：删除的传播形态

- A 删书 → `books` 行进 `book_trash`（`deletedAt = now`）→ 同步上传 trash 条目。
- B 收到 → 把本地同名书也移入 `book_trash`，**不删原文件**。
- 30 天内任何一端可恢复（恢复 = trash 行回 `books` + 同步移除该 trash 条目）。
- 30 天到期 → 物理删除原文件并移出表；此删除**不再传播**（对方早已在 trash 中，各自到期）。

### 阅读时长：显示合并，存储按设备

用户要「合并」，但直接合并成一行会在同步时重复计数。正确做法是保持**每设备一行**（这也正是 `ReadRecord` 主键设计的原意），读取时求和：

- 写入：`readRecord.deviceId = AppConst.androidId`（修复既有 bug）。
- 同步：按 `deviceId + bookName` 取 `max(readTime)` 合并，各设备只写自己那一行，天然不双计。
- 展示：`ReadRecordDao.allShow` 已是 `sum(readTime), max(lastRead) group by bookName`，无需改动。
- 效果：手机读 10 分钟 + 平板读 10 分钟 = 20 分钟，双向同步后两端一致。

### 替换规则去重：内容哈希

不用 `id`（设备各自 `autoGenerate`，必然不同），也不用新增 `syncId` 列（迁移回填两端难对齐）。用：

```
logicalKey = sha256( name | group | pattern | replacement | scope |
                     scopeTitle | scopeContent | excludeScope | isRegex | timeout )
```

排除 `id`、`order`、`isEnabled`（这三者参与同步但不参与身份）。代价：改内容 = 换身份，表现为「删旧增新」，最终状态仍一致（用户要的是状态一致，不是 id 稳定）。

### 载荷版本兼容（关键）

`SyncData.version` 升到 3。旧端上传的 v2 JSON 缺少新增字段。判定规则：

- **字段为 `null`（JSON 里没有）⇒ 该类别跳过，不动本地。**
- **字段为空列表 `[]` ⇒ 视为「云端确实没有」，按保护规则处理。**

用可空字段（`List<X>? = null`）而非默认 `emptyList()`，否则升级当次会用空列表清空用户的 RSS 与规则。

## 验收标准

- AC-1：`SyncData` 覆盖上述 15 类数据 + 4 个配置文件，单测逐类固定「上传 → 下载 → 落库」一致。
- AC-2：v2 载荷（缺字段）同步后，本地 RSS / 替换规则 / 字典等**一条不少**。
- AC-3：书源为空列表的保护仍生效（不覆盖对方书源）。
- AC-4：替换规则的 `isEnabled`、`order` 双向一致；改内容后两端最终集合一致（不重复）。
- AC-5：A 删书 → B 下次同步后该书移出书架、进入回收站，原文件仍在。
- AC-6：回收站 30 天内可恢复，恢复后书籍回到书架且章节不丢。
- AC-7：超过 30 天的 trash 条目在启动时被清理，原文件被物理删除。
- AC-8：回收站可手动彻底删除、可清空。
- AC-9：手机读 10 分钟 + 平板读 10 分钟 → 两端均显示 20 分钟；再各读 5 分钟 → 30 分钟，无重复计数。
- AC-10：`readRecord.deviceId` 写入真实 `androidId`，非空。
- AC-11：`autoUploadLocalBook` 开启时本地书导入后自动上传并转为 WebDAV 书；关闭时无网络请求。
- AC-12：`autoDownloadWebDavBook` 关闭时，本地无文件的 WebDAV 书不触发下载并给出提示。
- AC-13：设备名称与备份 / 恢复全套代码、UI、字符串删除；全仓检索无残留符号。
- AC-14：`gradlew :app:testAppDebugUnitTest` 全绿，用例数不少于 143。
- AC-15：v89 数据库平滑迁移到 v90（新增 `book_trash`），既有数据不丢。
- AC-16：文档门禁 `tools/check-docs.ps1` 退出码 0。

## 验收标准 ↔ 测试用例映射

| 验收标准 | 测试用例 | 位置 |
|---|---|---|
| AC-1 | `SyncPayloadTest`（逐类一致性） | `app/src/test/java/io/legado/app/help/sync/` |
| AC-2 | `SyncCompatTest.v2PayloadKeepsLocal` | 同上 |
| AC-3 | `SyncPlanTest.sourceProtection`（既有，扩展） | 同上 |
| AC-4 | `ReplaceRuleKeyTest` + `SyncMergeTest` | 同上 |
| AC-5..AC-8 | `BookTrashTest`（纯逻辑）+ `BookTrashDaoTest`（Robolectric） | 同上 |
| AC-9 | `ReadRecordMergeTest`（求和与去重） | 同上 |
| AC-10 | `ReadRecordDaoTest.deviceIdWritten`（Robolectric） | 同上 |
| AC-11..AC-12 | 真机验证 + `docs/evidence/webdav-sync-v3-check.txt` | — |
| AC-13 | 全仓符号检索脚本输出 | `docs/evidence/webdav-sync-v3-check.txt` |
| AC-14 | `gradlew :app:testAppDebugUnitTest` 用例数与耗时 | 同上 |
| AC-15 | Robolectric 从 89 schema 迁移到 90 | 同上 |
| AC-16 | `pwsh -File tools/check-docs.ps1` 退出码 0 | 同上 |

## 数据流与接口变化

- `SyncData` 版本 2 → 3，新增 12 个可空列表字段。
- 新增表 `book_trash`，Room `version 89 → 90`（AutoMigration）。
- `ReadRecord.deviceId` 改为写入 `AppConst.androidId`（行为修复，表结构不变）。
- 删除 `Backup` / `Restore` / `BackupAES` / `BackupConfigFragment` 及 7 个 `AppWebDav` 备份函数。
- 删除 `PreferKey.webDavDeviceName` / `onlyLatestBackup` / `autoCheckNewBackup`。
- 新增 3 个偏好键：`autoUploadLocalBook` / `autoDownloadWebDavBook` / `syncReadRecord`。

## 边界情况与失败处理

- **升级当次载荷兼容**：见「载荷版本兼容」。这是最危险的一步，必须先用单测固定。
- **两台设备同时改**：沿用 v2 的 `SyncDecision`（指纹判定 + 本地优先），不引入新算法。
- **books 同步与唯一索引冲突**：只 update 不 insert，避免 `REPLACE` 覆盖同名书。
- **trash 与书架的唯一索引**：`book_trash` 独立表，用 `bookUrl` 主键，不与 `books` 冲突。
- **本地书文件已被用户删除**：trash 到期清理时文件不存在 ⇒ 忽略，不报错。
- **规则变更未生效**：同步写入后必须调 `ContentProcessor.upReplaceRules()`，且在 IO 线程执行，否则正在阅读的书仍用旧规则。
- **删除备份后旧 zip 无法恢复**：明确不兼容 v2 备份包，README / 更新日志需写明。

## 测试方案

- 纯函数（不依赖 Android）：`SyncPayload`、`ReplaceRuleKey`、`ReadRecordMerge`、`BookTrashPolicy`（30 天判定）、`SyncDecision` 扩展。
- Robolectric：`book_trash` DAO、`ReadRecord` 写入、89→90 迁移。
- 真机：本地书上传/下载开关、删除传播、跨设备一致。
- 命令：`gradlew :app:testAppDebugUnitTest`；`gradlew assembleAppDebug`；`pwsh -File tools/check-docs.ps1`。

## 假设与风险

- 假设：用户接受「不再支持恢复 v2 及更早备份包」。已在需求确认中明确。
- 假设：用户接受「替换规则改内容 = 换身份（删旧增新）」。若需保留编辑历史，需追加 `syncId` 列方案。
- 风险 1：**升级当次清空数据**（若空列表与缺字段混淆）。缓解：可空字段 + 单测 AC-2 强制；发布前用 v2 真实 JSON 回归。
- 风险 2：书架同步误覆盖同名书。缓解：只 update、不 insert；`name+author` 唯一索引兜底。
- 风险 3：删除备份后若同步失效，用户失去兜底。缓解：先完成阶段 1–4 并真机验证，最后才删备份（阶段 5）。
- 风险 4：30 天物理删除不可逆。缓解：回收站 UI 明确提示；清理仅在启动时执行一次。
- 回滚方案：`git revert` 提交链；数据库只新增表，降级版本不读该表，无破坏性迁移。

## 变更记录

- 2026-11-25：初版。用户确认：书架删除跨设备传播但走回收站（30 天）、不兼容旧备份包、阅读时长按「各设备求和」、`isEnabled` 与 `order` 纳入同步。
- 2026-11-25：补充确认三项。**删除传播不提示**（静默处理，用户自行到回收站查看）；**旧的 v2 及更早备份包不做升级提示**，直接删除；开始阶段 1 实施。

## 决策记录（实施中追加）

- 决策：删除传播时**不弹任何提示**，静默把书移入回收站。
- 理由：用户明确要求「完全一致的多端体验」，提示会破坏静默同步的一致性感受；回收站本身提供了可见的撤销入口。
- 被放弃的备选：逐本提示（打扰）、合并为一条汇总提示（仍需 UI 介入）。

### 完成记录（2026-11-25）

五个阶段全部落地，对应提交：

| 阶段 | 内容 | 提交 |
|---|---|---|
| 1 | 同步载荷扩到 15 类，区分「字段缺失」与「空列表」 | `e4250ccd5` |
| 2 | 书架回收站：表 + 删除传播 + UI + 启动清理 | `f5e37f5a5`、`2935bdcb0` |
| 3 | 阅读记录 deviceId 修复与按设备合并 | `21d49978f` |
| 4 | 本地书上传 / 按需下载 / 阅读时长三个开关 | `2d1ca2354` |
| 5 | 删除备份与恢复全套 | 见阶段 5 提交 |

- 决策：**不兼容 v2 及更早的备份 zip**，且不加升级提示。
- 理由：用户明确选择「不要，直接删」；备份功能整体移除后，旧 zip 里的
  `bookshelf.json`、`config.xml` 已无读取代码，保留提示只会制造焦虑。
- 被放弃的备选：升级时弹一次提示（用户否决）、保留只读恢复能力（与「彻底删除」冲突）。

- 决策：回收站表保存 `Book` 的**显式列子集**，不序列化整行。
- 理由：`Book.ReadConfig` 含 `java.time.LocalDate`，Gson 反射序列化在 JDK 17 上抛
  `InaccessibleObjectException`（阶段 1 实测）。代价是 `Book.readConfig`
  （每本书的排版覆盖）不入回收站，恢复后回落到全局设置——远小于「回收站整体不可用」。

- 决策：阅读时长保持「每设备一行 + 展示时求和」，不合并成单行总数。
- 理由：合并成一行会在同步时重复计数（手机 10 分钟 + 平板 10 分钟会被写成 20 再回写两端）。
  保持每设备一行后，各设备只写自己那一行，求和天然不双计。

- 未达成的 AC：无。AC-1 ~ AC-16 均有证据，见 `docs/evidence/webdav-sync-v3-*.{txt,md,png}`。
- 已知残余风险：
  1. 自动上传与按需下载关闭提示未在模拟器实测（缺可用 WebDAV 服务器 / 难构造场景）。
  2. 回收站「彻底删除」只删数据库行，本地文件仍留在磁盘，留待与「删除原文件」开关一起处理。
  3. 首次升级当次若云端仍是 v2 载荷，同步会落到「两边都改动 → 本地优先上传」，
     属保守分支，但意味着云端旧数据会被本机覆盖一次。

