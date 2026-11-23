# engineering-debt 验证证据

> 生成：2026-11-23 · 规格：[docs/specs/done/engineering-debt.md](../specs/done/engineering-debt.md) · 状态：完成
> 原始输出：[engineering-debt-check.txt](engineering-debt-check.txt)

## AC ↔ 验证映射

| 验收标准 | 做法 | 结果 | 原始输出 |
|---|---|---|---|
| AC-1 `webdav-sync-v2.md` 表述唯一且一致 | 人工核对 AC 表、阶段 B 表与文末变更记录 | 通过：追加 2026-11-23 条目，把「AC-5 未实现 / 阶段 B 尚未开始」更正为已完成，并补引 [18c]–[19c] | 规格文件本身 |
| AC-2 `eink-only.verify.md` 含阶段 4–7 映射 | 逐条比对引用的段落编号是否在原始输出中真实存在 | 通过：新增 9 行映射，引用 [10][11][12][13][13b][13c][14][14b][15][15b][16][16b][17][17b]，均核实存在 | 段 [1] |
| AC-3 `TODO.md` 只装未闭环项 | 人工核对每条均为本次确认的未闭环事项 | 通过：7 条（T-1 ~ T-7），无已完成项 | — |
| AC-4 Red 阶段真实失败并留证据 | 首次运行即出现断言失败 | 通过：37 tests completed, 1 failed，`PaginatorTest.kt:140` AssertionError | 段 [3a] |
| AC-5 Green 阶段全部通过 | 同一任务 `--rerun-tasks` 实跑 | 通过：新增 38 个用例 0 失败；全任务 46 个用例 0 失败 | 段 [4] |
| AC-6 文档门禁退出码 0 | `pwsh -File tools/check-docs.ps1` | 通过：47 个 .md、1929 个文件，exit=0 | 段 [5] |
| AC-7 `core.hooksPath` = `.githooks` 且 `-Check` 退出码 0 | `install-hooks.ps1` 安装 + `-Check` 复核 | 通过：安装 exit=0，`-Check` 输出「钩子已启用」且 exit=0，安装时试跑门禁亦通过 | 段 [6] |

## Red（失败先行）

本任务**有可测逻辑**（`Paginator` 的页边界算法、`BookProgressBundle` 的合并规则），按判据必须 TDD。

真实 Red 证据（段 [3a]）：

```
37 tests completed, 1 failed
PaginatorTest > 页数变少时当前页码自动收敛 FAILED
    java.lang.AssertionError at PaginatorTest.kt:140
BUILD FAILED in 6m 10s
```

失败含义：我最初以为 `commitPageSize` 作用于第 0 页，实际作用于**当前页**
（`Paginator.kt:76-86` 用 `measuredSizes[page]`）。失败证明测试真的在检查行为，
而不是恒真的空壳断言。

处理方式：按真实语义重写该用例为两条断言，**未修改任何生产代码**。
若这次失败指向的是生产代码缺陷，按规格「假设与风险」的约定应记入 `TODO.md` 而
不是就地修（那会超出「不改生产代码」的非目标）。经核对，`Paginator` 行为自洽，
属测试对代码的误解。

顺带留证的第二个 Red：**环境层**的失败（段 [2]），KSP 跨盘符崩溃导致测试根本跑不起来，
与代码无关，按 `GRADLE_USER_HOME=D:\gradle-home` 处理。

## Green（通过）

- 命令：`gradlew :app:testAppDebugUnitTest --rerun-tasks --tests "io.legado.app.utils.PaginatorTest" --tests "io.legado.app.data.entities.BookProgressBundleTest"`
- 结果：`BUILD SUCCESSFUL in 57s`
- 新增用例：38 通过 / 0 失败 / 0 跳过
  - `PaginatorTest` 22 个
  - `BookProgressBundleTest` 16 个
- 全量同任务：46 通过 / 0 失败 / 0 跳过（含既有 `ExampleUnitTest` 1 个、`JsTest` 7 个）

## 未达成 / 主动放弃

- **主动放弃**：为 `UserDataSync`、`PendingProgressQueue`、`SyncState`、
  `BookProgressBundle.applyTo(book: Book)` 编写测试。前三者依赖 `appCtx`，
  后两者依赖 `Book`（Room 注解 + Parcelable + appDb）。覆盖它们需引入 Robolectric
  或改动生产代码，均超出本次已确认范围。已登记为 `TODO.md` 的 T-4。
- 无未达成的验收标准。

## 残余风险与后续

- **构建环境不稳定**：本机跑 Gradle 必须同时满足两个条件——JDK 17 在 PATH/JAVA_HOME
  且 `GRADLE_USER_HOME` 指向与项目同盘符的目录。两者都未固化进仓库配置，
  换机器或清理环境后会重现「toolchain 找不到」与「KSP 跨盘符崩溃」。
  已登记为 `TODO.md` 的 T-1、T-2。
- **测试只覆盖纯逻辑**：`Paginator` 与 `BookProgressBundle` 的核心算法有安全网了，
  但「分页后 UI 上是否真的放得下」仍只能靠真机核对（`EinkPager` 依赖 Android View 测量）。
- **`UserDataSync.fingerprint` 的哈希碰撞风险**未处理，见 `TODO.md` T-5。
