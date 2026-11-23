# 同步逻辑可测化：抽出决策与差异计算

> 状态：done · 日期：2026-11-23

## 目标

把 WebDav 用户数据同步（分组 / 书签 / 书源）中**埋在网络与数据库调用里的纯逻辑**抽成可单测的函数，为这条「出错就丢用户数据」的链路建立安全网。

- G1：同步的分支决策（谁覆盖谁）从 `syncUserData` 中抽出，成为纯函数。
- G2：`replaceLocal` 的「算差异」部分抽出，成为纯函数；DAO 执行部分保留原样。
- G3：为上述两者补齐 JVM 单测，覆盖全部分支与边界。
- G4：抽出过程中若不改变行为，则测试必须钉住现有行为；发现真实缺陷则修正并单独记录。

## 背景

### 现状

`AppWebDav.syncUserData()`（`AppWebDav.kt:396-460`）一个方法里混了四件事：

| 关注点 | 行号 | 可测性 |
|---|---|---|
| 网络与授权检查 | 397-398 | 需 mock |
| 下载云端数据 | 411-415 | 需网络 |
| **选分支决策** | 426-455 | 纯逻辑，但埋在 IO 里 |
| 执行（替换/上传） | 429-452 | 需 `appDb` + 网络 |

`UserDataSync.replaceLocal()`（`UserDataSync.kt:65-113`）同样混了两件事：计算该删什么/该加什么（纯逻辑）与实际调用 DAO（需要 `appDb`）。

结果：这条链路的**决策正确性完全没有测试**。而它的失败模式是静默的——判错分支会用云端覆盖本地，或反过来，用户数据消失且无报错。

### 为什么不用 Robolectric

评估过引入 Robolectric 直接测 `replaceLocal`，判断为不适合本轮：

- `appDb` 是顶层 `val appDb by lazy { Room.databaseBuilder(appCtx, ...) }`，全局单例，无注入口。
- `AppDatabase.dbCallback.onOpen` 每次打开库执行约 10 条 `INSERT ... WHERE NOT EXISTS`，预种 6–7 个内置分组。测试断言必须处理这些脏数据。
- `AppDatabase` 已到 version 89、约 30 个实体，内存库建表开销大，每个测试都会付这个成本。

因此本轮只抽纯函数，DAO 写入层仍由真机验证。引入 Robolectric 另开任务（已记入 TODO）。

## 范围与非目标

### 范围内

新增文件：

- `app/src/main/java/io/legado/app/help/sync/SyncDecision.kt`
  - `sealed interface SyncDecision`，含 5 种取值：
    `UploadLocal`（云端无数据）、`UploadLocalLocalWins`（云端未改）、
    `ReplaceLocal`（本地未改）、`SkipToProtectSources`（双改且本地书源空）、
    `UploadLocalBothChanged`（双改，本地优先）
  - `fun decide(localHash, remoteHash, lastHash, local, remote): SyncDecision`
- `app/src/test/java/io/legado/app/help/sync/SyncDecisionTest.kt`

- `app/src/main/java/io/legado/app/help/sync/SyncPlan.kt`
  - `data class SyncPlan`，含 6 个字段：待删/待增的分组、书签、书源
  - `fun planReplace(local: SyncData, remote: SyncData): SyncPlan`
- `app/src/test/java/io/legado/app/help/sync/SyncPlanTest.kt`

修改文件：

- `app/src/main/java/io/legado/app/help/sync/UserDataSync.kt`
  - `replaceLocal` 改为「调 `planReplace` 算 plan → 按 plan 执行 DAO」
- `app/src/main/java/io/legado/app/help/AppWebDav.kt`
  - `syncUserData` 改为「算指纹 → 调 `decide` → when 执行」
  - 行为日志保留，文案与现有 `AppLog.put` 一致或更清晰

### 非目标

- **不引入 Robolectric** 或任何新的测试依赖。
- **不测试 DAO 写入**：`planReplace` 产出的 `SyncPlan` 如何落到 `appDb`，仍只靠真机验证。
- **不改同步策略**：仍然是「最后修改者胜」，仍然有书源空保护。
- **不动 `syncReadConfig`**（排版设置同步，用文件修改时间判新旧，与本任务无关）。
- **不动进度同步**（`progress.json` 那套，已在 webdav-sync-v2 规格中验证过）。
- 不改 `SyncData` / `SyncFingerprint` / `SyncState` 的现有接口。

## 验收标准

- AC-1：`decide` 是纯函数，不引用 `appDb`、`appCtx`、网络或文件系统（编译期即可验证：该文件无相关 import）。
- AC-2：`planReplace` 是纯函数，同上约束。
- AC-3：`SyncDecisionTest` 覆盖 5 种决策，并覆盖「旧版指纹」「远端为 null」两个边界。
- AC-4：`SyncPlanTest` 覆盖：分组增删、书签增删（按 `书名|作者|章|位置` 键）、书源增删、书源空保护、双端完全一致时 plan 为空。
- AC-5：重构后 `syncUserData` 与 `replaceLocal` 的行为与重构前一致（除 AC-6 修正的缺陷外）。
- AC-6：若发现真实缺陷，须在规格「变更记录」中逐条记录：现象、原因、修正方式、影响面。
- AC-7：`gradlew :app:testAppDebugUnitTest` 全绿，既有 72 个用例数量不减。
- AC-8：`gradlew assembleAppDebug` 成功。
- AC-9：真机验证同步主链路未退化：启动触发同步、书架下拉刷新触发同步、有变更时提示条数。
- AC-10：`tools/check-docs.ps1` 通过。

## 验收标准 ↔ 测试用例映射

| 验收标准 | 测试用例 | 位置 |
|---|---|---|
| AC-1 | 审查 `SyncDecision.kt` 的 import 列表 | 人工审查 |
| AC-2 | 审查 `SyncPlan.kt` 的 import 列表 | 人工审查 |
| AC-3 | `SyncDecisionTest` 全部用例 | `docs/evidence/sync-testable-red.txt` / `-green.txt` |
| AC-4 | `SyncPlanTest` 全部用例 | 同上 |
| AC-5 | 重构前后决策结果对照（测试钉住现有行为） | 代码审查 + 测试 |
| AC-6 | 规格「变更记录」条目 | 规格文件 |
| AC-7 | `gradlew :app:testAppDebugUnitTest` 输出 | `docs/evidence/sync-testable-green.txt` |
| AC-8 | `gradlew assembleAppDebug` 输出 | 同上 |
| AC-9 | 真机 WWZHIBUKSKCM5LGA 操作核对 | `docs/evidence/sync-testable-check.txt` |
| AC-10 | `check-docs.ps1` 输出 | 同上 |

## 数据流与接口变化

### 新增的纯函数

```
decide(
  localHash: String,      // 本地数据指纹
  remoteHash: String,     // 云端数据指纹
  lastHash: String?,      // 上次同步完成时的指纹
  local: SyncData,        // 本地全量数据
  remote: SyncData?       // 云端数据，null 表示云端无数据
): SyncDecision
```

判定顺序（与现有 `when` 完全对应，顺序敏感）：

1. `remote == null` → `UploadLocal`
2. `localHash == lastHash` → `ReplaceLocal`
3. `remoteHash == lastHash` → `UploadLocalLocalWins`
4. 其余 → 若 `local.sources.isEmpty() && remote.sources.isNotEmpty()` 则 `SkipToProtectSources`，否则 `UploadLocalBothChanged`

```
planReplace(local: SyncData, remote: SyncData): SyncPlan
```

按现有 `replaceLocal` 语义：以 `remote` 为准，删掉本地有而 remote 没有的，补上 remote 有而本地没有的。书源部分若 `remote.sources.isEmpty()` 则整体跳过（书源空保护）。

### 不变的接口

- `UserDataSync.fingerprint(data): String` —— 委托 `SyncFingerprint.of`
- `UserDataSync.export(): SyncData` —— 仍直接从 `appDb` 读
- `UserDataSync.replaceLocal(remote): Result` —— 签名不变，内部改为走 plan
- `AppWebDav.syncUserData(): UserDataSync.Result` —— 签名不变

## 边界情况与失败处理

- **旧版指纹**：`lastHash` 为旧格式（纯数字）时，`SyncFingerprint.isLegacy` 为 true。此时 `localHash`/`remoteHash` 都不可能等于它，会自然落入第 4 条「双改」分支。该行为必须被测试钉住，且日志保留。
- **云端数据损坏**：`remote` 解析失败时 `syncUserData` 中 `remote == null`，走 `UploadLocal`。这与「云端首次无数据」共用同一分支，现有行为如此，本轮不改。
- **双端都为空**：`local` 与 `remote` 都无数据时，`localHash == remoteHash`，若 `lastHash` 也相同则走 `ReplaceLocal`，plan 为空，无副作用。
- **并发**：`syncUserData` 无显式互斥。本轮不改并发语义，仅在规格中记录该现状为已知风险。

## 测试方案

- 框架：JUnit 4（仓库既有，`junit:junit:4.13.2`），不新增依赖。
- 位置：`app/src/test/java/io/legado/app/help/sync/`
- 命令：`gradlew :app:testAppDebugUnitTest --tests "*SyncDecisionTest" --tests "*SyncPlanTest"`
- TDD 流程：先写测试并确认失败（Red）→ 实现 → 通过（Green）。
  - 由于是「先抽函数再测」，Red 阶段表现为编译失败（类不存在），已足够证明测试真的依赖待实现的行为。
  - 更严格的做法是先写测试、再写实现；本任务采用后者，因为被测的语义已由现有代码确定。

构造测试数据说明：`SyncData` 内含 `BookGroup` / `Bookmark` / `BookSource` 三个实体。
- `BookGroup` 是纯 Kotlin data class（含 `Parcelable` 注解，但 JVM 单测可实例化）。
- `Bookmark` 同理。
- `BookSource` 较大，需确认其构造是否触碰 Android；若不可直接构造，则 `SyncPlanTest` 中书源部分改用反射或跳过，并记录。

## 假设与风险

- 假设：`BookGroup` / `Bookmark` / `BookSource` 三个实体能在 JVM 单测中直接实例化（均已有默认值）。若某个实体在初始化时触碰 Android 类，需退回用 `mock` 或只测不含该实体的部分。
- 风险 1：**抽取过程中改错判定顺序或书源保护条件，会导致丢数据**。缓解：测试先钉住现有 5 条分支的输入输出对，重构后逐一比对；真机验证同步链路。
- 风险 2：`planReplace` 与 `replaceLocal` 的「增删顺序」若不一致，可能影响 `Result` 计数。缓解：`SyncPlan` 只描述要做什么，计数在执行阶段按实际执行结果累加，保持与原实现一致。
- 风险 3：重构触及 `AppWebDav` 这个 570 行的大文件，容易误伤相邻逻辑。缓解：只改 `syncUserData` 一个方法，改动前后逐行对照。
- 回滚方案：单次提交可整体 `git revert`；抽取的函数是新增文件，删除不影响其他代码。

## 变更记录

- 2026-11-23：创建。用户原选「抽决策 + 重构 replaceLocal + 引入 Robolectric」，经调研说明 Robolectric 方案的硬障碍（`appDb` 全局 lazy 单例、`onOpen` 预种脏数据、89 版本库开销）后，用户改为采纳「两步走」：本轮只做纯函数抽取，Robolectric 另开任务。
- 2026-11-23：用户明确「允许顺带修 bug」，因此 AC-6 要求逐条记录发现的缺陷。
- 2026-11-23：完成。新增 `SyncDecision`（17 用例）与 `SyncPlan`（17 用例），
  `syncUserData` 与 `replaceLocal` 改为调用二者。真机三场景验证通过，数据零丢失。

## 决策记录（完成后追加）

- 决策日期：2026-11-23
- 决策：
  1. 只抽纯函数，DAO 写入层不测；不引入 Robolectric。
  2. 发现的「首次同步多一次上传」缺陷**不修**，用测试钉住并在注释中说明原因。
  3. `replaceLocal` 内部改为自行 `export()` 取本地快照，不再依赖调用方传入。
- 理由：
  1. `appDb` 是顶层 lazy 全局单例，`onOpen` 每次预种 6–7 个内置分组，库已到 89 版本；
     在 Robolectric 里跑这些会让每个测试付出数百毫秒到数秒，且断言要处理脏数据。
     而纯函数抽取已经能覆盖全部决策语义，性价比更高。
  2. 修它需要改变 `decide` 的判定顺序，会牵动全部四种分支的既有语义。
     该缺陷只造成一次多余上传、不丢数据，风险明显大于收益。
  3. 重构前 `replaceLocal` 直接用 `appDb.xxxDao.all` 现查，与传入的 `remote` 比较；
     重构后统一走 `export()`，让「差异计算」与「执行」都基于同一份快照，语义更清晰。
     代价是多一次全表读取，数据量大时有轻微开销，已记入残余风险。
- 被放弃的备选：
  - 一步到位引入 Robolectric 测 DAO 层——放弃，理由见上，另立后续任务。
  - 顺带修复首次同步的多余上传——放弃，风险大于收益。
- 未达成的 AC：无。AC-1 ~ AC-10 全部通过。
