# sync-testable 验证证据

> 生成：2026-11-23 · 规格：[docs/specs/sync-testable.md](../specs/sync-testable.md) · 状态：完成
> 原始输出：[sync-testable-check.txt](sync-testable-check.txt)

## AC ↔ 验证映射

| 验收标准 | 做法 | 结果 | 原始输出 |
|---|---|---|---|
| AC-1 `decide` 是纯函数 | 审查 `SyncDecision.kt` 的 import | 通过：只 import 同包的 `SyncData`，无 `appDb`/`appCtx`/网络/文件引用 | 段 [4] |
| AC-2 `planReplace` 是纯函数 | 审查 `SyncPlan.kt` 的 import | 通过：只 import 三个实体类（纯数据），无 `appDb`/`appCtx`/网络/文件引用 | 段 [4] |
| AC-3 决策 5 种 + 2 个边界 | `SyncDecisionTest` | 通过：17 个用例，覆盖 5 种决策、首次同步、旧版指纹、书源保护边界 | 段 [4] |
| AC-4 差异计算全覆盖 | `SyncPlanTest` | 通过：17 个用例，覆盖三类数据增删、书签去重键、书源空保护、两端一致、不改入参 | 段 [4] |
| AC-5 行为与重构前一致 | 测试钉住现有语义 + 真机数据核对 | 通过：真机三次同步场景（无变更 / 有变更上传 / 回到一致），数据量与基线完全一致 | 段 [6] |
| AC-6 发现的缺陷逐条记录 | 规格「变更记录」+ 原始输出段 [8] | 通过：记录 2 条（1 条未修并说明理由，1 条文档注释与实现不符已修） | 段 [8] |
| AC-7 单测全绿、既有用例不减 | `gradlew :app:testAppDebugUnitTest --rerun-tasks` | 通过：106 个用例 0 失败；既有 72 个完整保留 | 段 [4] |
| AC-8 构建成功 | `gradlew assembleAppDebug` | 通过：BUILD SUCCESSFUL in 23s，31.51 MB | 段 [5] |
| AC-9 真机主链路未退化 | 设备 WWZHIBUKSKCM5LGA 三场景 | 通过：同步正常执行、指纹正确变化、无崩溃、无数据丢失 | 段 [6] |
| AC-10 文档门禁 | `check-docs.ps1` | 通过 | 见 [README.md](README.md) 末次结果 |

## Red（失败先行）

命令：`gradlew :app:testAppDebugUnitTest --tests "*SyncDecisionTest" --tests "*SyncPlanTest"`

结果：编译失败，`Unresolved reference 'SyncDecision'`。

说明：被测语义已由现有代码确定，因此 Red 表现为「类不存在」的编译失败——它证明测试真的依赖待实现的行为，不是先写实现再补测试。

**Green 阶段的一次真实失败**（测试自身写错，非生产代码缺陷）：

```
SyncPlanTest > 云端多出的书签被补进本地     expected:<[200]> but was:<[]>
SyncPlanTest > 本地多出的书签被删除         expected:<[200]> but was:<[]>
SyncPlanTest > 书源保护不影响分组与书签的处理  书签仍应插入 expected:<1> but was:<0>
SyncPlanTest > 三类数据可同时增删           expected:<1> but was:<0>
```

原因是测试辅助函数的默认参数让多条书签的去重键完全相同，它们被**正确地**判定为同一条书签。修正测试，未改生产代码。

## Green（通过）

- 单测：`BUILD SUCCESSFUL in 55s`，**106 个用例，0 失败，0 跳过**
  - 本次新增 34 个：`SyncDecisionTest` 17、`SyncPlanTest` 17
  - 既有 72 个完整保留
- 编译：`BUILD SUCCESSFUL in 42s`（仅既有无关警告）
- 构建：`BUILD SUCCESSFUL in 23s`，APK 31.51 MB
- 真机（用户真实数据，测试前后逐项核对）：

| 场景 | 操作 | 结果 |
|---|---|---|
| A 无变更 | 启动应用 | `lastHash` 不变，无多余写入 |
| B 本地有变更 | 新增 1 个分组后启动 | `lastHash` 由 `85a46f…` → `a217d7…`，分组 17 个 |
| C 回到一致 | 删除该分组后启动 | `lastHash` 回到 `85a46f…`，走 `ReplaceLocal` 路径 |

最终数据与基线完全一致：`books 10→10`、`book_groups 16→16`、`bookmarks 0→0`、`book_sources 1→1`。
`logcat -b crash` 全程无本应用崩溃。

## 未达成 / 主动放弃

- **DAO 写入层仍未测试**：`planReplace` 产出的计划如何落到 `appDb`，只经过真机验证。引入 Robolectric 需要处理 `appDb` 全局单例、`onOpen` 预种分组、89 版本库开销，已列为独立后续任务。
- **缺陷 1 主动不修**：首次同步会多一次上传。修复需改变判定顺序、牵动所有分支语义，风险大于收益。已用测试钉住该行为并注明原因。
- 无未达成的验收标准。

## 残余风险与后续

- **真机验证用的是用户真实数据**：虽然测试前后做了逐项核对且结果一致，但过程中确实在真实库上增加了测试分组再删除。数据库完整备份在测试开始前已导出，测试后已删除备份文件（含用户书籍与书源隐私数据，不留在仓库）。
- **`SyncDecision` 的判定顺序是隐性契约**：四个分支的先后顺序决定了结果，改动顺序会静默改变行为。测试已按顺序组织并注明，后续修改需重读 `SyncDecisionTest`。
- **书签去重键的边界**：同一本书同一章节同一位置被视为同一条书签。若两处不同的书签恰好落在完全相同的四元组上，会被误判为重复。该风险在重构前就存在，本次只是把它显式化并测试。
- **`replaceLocal` 现在会重新 `export()` 一次**：重构后它内部调用 `export()` 取本地快照，而 `syncUserData` 之前已调用过一次。多一次数据库全表读取，数据量大时（数千书签/书源）有轻微开销，但换来的是 `replaceLocal` 不再依赖调用方传入的数据、语义更清晰。
