# 工程债清理：文档一致性、核心纯逻辑单测、门禁启用

> 状态：done · 日期：2026-11-23

## 目标

在不改动任何生产代码的前提下，清掉三笔已确认的工程债：

- G1：`webdav-sync-v2.md` 的正文结论与文末变更记录互相矛盾，读者无法判断哪些项真的完成了。
- G2：`eink-only.verify.md` 只覆盖阶段 1–2，阶段 4–7 的证据已存在于 `eink-only-check.txt` 但未被汇总引用。
- G3：仓库无 `TODO.md`，未闭环事项无处登记。
- G4：两个零 Android 依赖的纯逻辑类（`Paginator`、`BookProgressBundle`）没有任何单测，重构无安全网。
- G5：文档门禁的提交钩子已版本化但未启用，门禁实际不生效。

## 范围与非目标

### 范围内

任务 1（文档一致性，无可测逻辑）：

- `docs/specs/webdav-sync-v2.md`：修正文末「变更记录」末条与 AC 表/阶段 B 表的矛盾。
- `docs/evidence/eink-only.verify.md`：补阶段 4–7 的 AC ↔ 证据映射，引用 `eink-only-check.txt` 的 [10]–[17b] 段。
- 新建 `TODO.md`（仓库根），登记本次未能闭环的事项。

任务 2（单测，有可测逻辑 ⇒ TDD）：

- 新增 `app/src/test/java/io/legado/app/utils/PaginatorTest.kt`
- 新增 `app/src/test/java/io/legado/app/data/entities/BookProgressBundleTest.kt`

任务 3（门禁启用）：

- 执行 `git config core.hooksPath .githooks`，使 `.githooks/pre-commit` 生效。

### 非目标

- **不改任何生产代码**（`app/src/main/**` 零改动）。
- 不测 `UserDataSync`：依赖 `appDb`（Room 实例 + `appCtx`），需 Robolectric 或改生产代码。
- 不测 `PendingProgressQueue`、`SyncState`：依赖 `appCtx.filesDir`。
- 不测 `BookProgressBundle.applyTo(book: Book)` 与 `isAheadOf(BookProgress, Book)`：`Book` 依赖 Room 注解、`Parcelable`、`appDb`、`AppConfig`、`ReadBook` 等，JVM 单测无法加载。
- 不引入 Robolectric / Mockito 等新测试依赖。
- 不修 `gradle.properties` 中写死的 `kotlin.build.report.dir=D:/EinkLegado/...`（独立问题，需单独确认）。
- 不改 `isEInkMode` 等 EInk 相关生产逻辑。

## 验收标准

- AC-1：`webdav-sync-v2.md` 全文对「AC-5 是否完成」「阶段 B 是否开始」的表述唯一且一致，与 `eink-only-check.txt` 的 [18c]/[19b]/[19c] 段相符。
- AC-2：`eink-only.verify.md` 含阶段 4–7 的 AC ↔ 证据映射，每条都指向 `eink-only-check.txt` 中实际存在的段落编号。
- AC-3：仓库根存在 `TODO.md`，且其中每一条都是本次确认的未闭环事项，不含已完成项。
- AC-4：`PaginatorTest` 与 `BookProgressBundleTest` 在 Red 阶段真实失败（编译失败或断言失败），保留失败输出。
- AC-5：同一批测试在 Green 阶段全部通过，记录实际用例数与耗时。
- AC-6：`tools/check-docs.ps1` 退出码 0（新增 `TODO.md` 与规格文件后三条不变式仍成立）。
- AC-7：`core.hooksPath` 值为 `.githooks`，且 `install-hooks.ps1 -Check` 退出码 0。

## 验收标准 ↔ 测试用例映射

| 验收标准 | 测试用例 | 位置 |
|---|---|---|
| AC-1 | 人工核对文末记录与 AC 表表述一致 | `docs/specs/webdav-sync-v2.md` |
| AC-2 | 逐条比对引用的段落编号在 `eink-only-check.txt` 中存在 | `docs/evidence/eink-only.verify.md` |
| AC-3 | 人工核对条目均为未闭环项 | `TODO.md` |
| AC-4 | `gradlew testAppDebugUnitTest --tests '*PaginatorTest' --tests '*BookProgressBundleTest'` 失败输出 | `docs/evidence/engineering-debt-red.txt` |
| AC-5 | 同上命令通过，用例数与耗时 | `docs/evidence/engineering-debt-green.txt` |
| AC-6 | `pwsh -File tools/check-docs.ps1` 退出码 0 | `docs/evidence/engineering-debt-check.txt` |
| AC-7 | `pwsh -File tools/install-hooks.ps1 -Check` 退出码 0 | `docs/evidence/engineering-debt-check.txt` |

## 数据流与接口变化

无。本次不触碰生产代码、不改 Room schema、不改任何对外接口。

## 边界情况与失败处理

- **Gradle 环境不可用**：证据文件记录 `GRADLE_USER_HOME=D:\gradle-home`，该目录在本机不存在；KSP 跨盘符缺陷可能复现。若 `gradlew test` 无法执行，则 AC-4/AC-5 记为「无法执行」而非「通过」，在证据与汇报中显著声明，并给出替代核验（静态审查测试代码 + 说明未执行）。
- **测试命名与任务名**：`productFlavors` 只有 `app`，单元测试任务名为 `testAppDebugUnitTest`。若实际任务名不符，以 `gradlew tasks` 输出为准并记录实际命令。
- **新建文档触发门禁 I2**：`TODO.md` 与 `docs/specs/engineering-debt.md` 必须登记进索引，否则门禁会拦。`TODO.md` 本身无索引（README 声明为 `—`），只需 `docs/specs/README.md` 登记规格文件。
- **钩子启用后提交被拦**：若门禁未通过，提交会被拒绝；逃生门为 `git commit --no-verify`。

## 测试方案

- 框架：JUnit 4（仓库已有 `testImplementation(libs.junit)`，`junit:junit:4.13.2`），不引入新依赖。
- 目录：`app/src/test/java/io/legado/app/`，与既有 `ExampleUnitTest.kt`、`JsTest.kt` 同级。
- 运行命令：`gradlew testAppDebugUnitTest --tests '*PaginatorTest' --tests '*BookProgressBundleTest'`。
- TDD 流程：先写测试 → 确认失败（Red）→ 若测试暴露真实缺陷则修生产代码（需重新确认，因超出「不改生产代码」范围）→ 通过（Green）。
- 文档类任务（AC-1/2/3）：以人工核对 + 门禁正跑替代自动化测试。

## 假设与风险

- 假设：`Paginator` 的现有实现是正确的，测试只用于固化行为、建立安全网。若测试发现缺陷，按「非目标」约束我不修生产代码，而是把缺陷记入 `TODO.md` 并回流给用户决策。
- 风险 1：Gradle 单测跑不起来（环境问题）。缓解：如实记为「无法执行」，不谎称通过。
- 风险 2：`BookProgressBundle` 依赖 `GSON`（`utils/GsonExtensions.kt`），若其初始化触碰 Android 类，JVM 单测可能抛 `RuntimeException: Method not mocked`。缓解：测试仅使用 `merge`/`get`/`put`/`progressKey`/`isAheadOf` 这些不经过 GSON 的路径；若仍失败，把 `BookProgressBundle` 从测试范围移除并记入 TODO。
- 风险 3：启用钩子后，若后续提交触发门禁失败会被拦。缓解：逃生门 `--no-verify`，且 `install-hooks.ps1 -Uninstall` 可回滚。
- 回滚方案：任务 1、2 均为新增/编辑文件，`git revert` 即可；任务 3 用 `pwsh -File tools/install-hooks.ps1 -Uninstall` 撤销。

## 变更记录

- 2026-11-23：初版规格。用户确认按「文档债 → 单测 → 启用钩子」顺序执行，并认可把单测范围砍到 `Paginator` 与 `BookProgressBundle` 两个零 Android 依赖的类。
- 2026-11-23：全部完成并归档。执行中发现两个未预见的环境前置条件（JDK 17、Gradle 缓存同盘符），已登记 TODO T-1、T-2。

## 决策记录（完成后追加）

- 决策日期：2026-11-23
- 决策：
  1. 单测只覆盖 `Paginator` 与 `BookProgressBundle` 的纯逻辑部分，不为 `UserDataSync`、`PendingProgressQueue`、`SyncState`、`BookProgressBundle.applyTo(Book)`、`isAheadOf(BookProgress, Book)` 编写测试。
  2. 归档时把本次规格迁入 `docs/specs/done/`，`docs/specs/` 只保留进行中的 eink-only 与 webdav-sync-v2。
- 理由：
  1. 后五者依赖 `appCtx`、`Book`（Room 注解 + Parcelable + appDb），JVM 单测无法加载；覆盖它们需引入 Robolectric 或改动生产代码，两者都超出本轮「零生产代码改动」的边界。砍掉它们换来了一个干净、无新依赖、30 秒内跑完的测试基线。
  2. 文档纪律要求完成的规格不得留在 `docs/specs/`，用户亦明确选择一并归档。
- 被放弃的备选：
  - 引入 Robolectric 以覆盖 Room 相关同步逻辑——放弃，因会新增依赖且延长测试耗时，收益与本轮目标（建立基线）不匹配。
  - 就地修改 `Paginator` 让它符合我最初的错误断言——放弃，那是测试理解错而非代码缺陷，改生产代码会让行为偏离真实调用方的预期。
- 未达成的 AC：无。AC-1 ~ AC-7 全部通过，证据见 `docs/evidence/engineering-debt.verify.md`。
