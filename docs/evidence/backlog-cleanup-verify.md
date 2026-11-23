# backlog-cleanup 验证证据

> 生成：2026-11-23 · 依据：[TODO.md](../../TODO.md) 的 T-1 ~ T-7 · 状态：部分完成（已在 TODO 标注剩余项）
> 原始输出：[backlog-cleanup-check.txt](backlog-cleanup-check.txt)

本次是对遗留项的集中清理，无独立规格文件——事项定义来自 `TODO.md`，逐条编号 T-1 ~ T-7，
处理结果回写到 `TODO.md` 的「已完成」表。因此下方映射以 TODO 编号为准。

## 事项 ↔ 验证映射

| 事项 | 做法 | 结果 | 原始输出 |
|---|---|---|---|
| T-1 构建报告路径写死 | 实测属性是否生效，再决定「改路径」还是「删除」 | 改为**删除**：`kotlin.build.report.dir` 已被 Kotlin 2.3.10 废弃，赋值被忽略且每次构建报警告。删除后警告消失、报告照常生成 | 段 [1] |
| T-2 构建环境未文档化 | 按文档纪律改写进既有 `README.md` | 通过：新增「Build-构建环境」一节，含 JDK 17 与 `GRADLE_USER_HOME` 同盘符两个前置条件、报错原文、命令 | 段 [2] |
| T-3 测试覆盖窄 | 挑选零 Android 依赖且业务关键的类补测 | 部分完成：本轮补 `SyncFingerprint`(9)、`AlphanumComparator`(17)；其余仍待补，留在 TODO | 段 [5][6] |
| T-5 指纹用 32 位 hashCode | TDD 重写为 SHA-256，设计升级兼容 | 通过：新增 `SyncFingerprint`，含 `v2:` 前缀与 `isLegacy` 旧值识别；升级当次走「本地为准」保守分支 | 段 [4] |
| T-6 网格分页未验证 | 先核实用户「已删网格书架」的说法 | **结论变更**：网格入口已删但代码未清理，`bookshelfLayout` 恒为 0 使所有 `>= 2` 分支不可达。不是「未验证」而是「不可达」，改为待清理项 | 段 [3] |
| T-7 门禁未进 CI | 用户决定不使用 CI | 改以文档方式闭环：README 门禁一节明确「新克隆必须手动跑 install-hooks.ps1」 | 段 [2] |

## Red（失败先行）

本任务含可测逻辑（指纹算法、自然排序），按判据 TDD。

**Red 一（编译期）**：新增 `SyncFingerprintTest` 后先跑，8 处 `Unresolved reference 'SyncFingerprint'`，
证明测试确实依赖待实现的行为，不是先有实现再补测试。

**Red 二（断言期）**：实现后仍有 1 条失败——
`SyncFingerprintTest > 空数据与非空数据指纹不同 FAILED`。
原因是**测试写错**：`SyncData()` 的默认 `version` 就是 2，与 `SyncData(version = 2)` 完全相同。
同类问题另一条一并修正。未改生产代码——判据是「测试对代码的理解错」而非「代码有缺陷」。

**Red 三（编译期，生产代码）**：`AppWebDav` 引入 `SyncFingerprint` 后首次编译失败
`Unresolved reference 'SyncFingerprint'`（漏 import），补齐后 `BUILD SUCCESSFUL in 10s`。

## Green（通过）

- 命令：`gradlew :app:testAppDebugUnitTest`
- 结果：`BUILD SUCCESSFUL in 57s`
- 全量 72 个用例，0 失败、0 跳过
  - 本轮新增 26 个：`SyncFingerprintTest` 9 个、`AlphanumComparatorTest` 17 个
  - 既有 46 个仍全绿（`PaginatorTest` 22、`BookProgressBundleTest` 16、`JsTest` 7、`ExampleUnitTest` 1）
- 生产代码编译：`gradlew :app:compileAppDebugKotlin` → `BUILD SUCCESSFUL in 10s`
- 文档门禁：`check-docs.ps1` → 48 个 `.md`、1936 个文件，exit=0

## 未达成 / 主动放弃

- **T-3 未全部完成**：仅补两个类，其余纯逻辑类仍未覆盖。属长期工作，非本次范围。
- **T-4 未处理**：`UserDataSync` 的端到端流程（读云端 → 比对指纹 → 选分支 → 写回）需 `appDb` + 网络，无测试。本轮只覆盖了它的指纹部分。
- **T-6 未清理**：网格死代码仍在。清理涉及多个 UI 文件，需单独确认范围后再动。
- **T-7 未进 CI**：用户明确要求不使用 CI，改以文档说明闭环。
- **T-8 新增**：旧指纹无显式迁移，等同步成功后由 `SyncState.save` 自然覆盖。

## 残余风险与后续

- **升级当次会以本地为准上传**。旧指纹与新指纹必然不等，`syncUserData` 落入「两边都改动」分支。
  该分支有「本地书源为空则不上传」保护，但仍意味着升级后首次同步不会拉取云端的新增分组/书签。
  已加日志（「检测到旧版指纹」），观察一个版本再决定是否需要显式迁移（TODO T-8）。
- **指纹换成 SHA-256 未做跨设备实测**。单测覆盖了「旧值与新值不等」这个前提，
  但真实双设备升级场景未验证。若两端都在旧版本，行为不变；若一端先升级，先升级端会上传本地数据。
- **`AlphanumComparator` 的前导零行为**是按代码逻辑固化的（`"010" > "10"`），
  这是现有实现的选择而非规范要求。若将来要求 `010` 与 `10` 等价，该测试会先失败提示。
- **删除 `kotlin.build.report.dir` 后**，报告固定在 `build/reports/kotlin-build/`。
  README 已写明不要加回，但无法阻止后续有人误加。
