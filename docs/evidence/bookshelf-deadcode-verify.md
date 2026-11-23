# bookshelf-deadcode 验证证据

> 生成：2026-11-23 · 规格：[docs/specs/bookshelf-deadcode.md](../specs/bookshelf-deadcode.md) · 状态：完成
> 原始输出：[bookshelf-deadcode-check.txt](bookshelf-deadcode-check.txt)

## AC ↔ 验证映射

| 验收标准 | 做法 | 结果 | 原始输出 |
|---|---|---|---|
| AC-1 编译通过 | `gradlew :app:compileAppDebugKotlin` | 通过：BUILD SUCCESSFUL in 46s，无 warning | 段 [3] |
| AC-2 符号零残留 | 7 个符号 + style2 目录核查 | 通过：全部 0 处，style2 目录已不存在 | 段 [4] |
| AC-3 单测不减少 | `gradlew :app:testAppDebugUnitTest --rerun-tasks` | 通过：72 个用例 0 失败，与删除前完全一致 | 段 [5] |
| AC-4 构建 APK | `gradlew assembleAppDebug` | 通过：BUILD SUCCESSFUL in 1m2s，31.51 MB | 段 [6] |
| AC-5 真机功能正常 | 设备 WWZHIBUKSKCM5LGA 逐项操作 | 通过：渲染、三页翻页、反向翻页、Tab 切换、Fragment 复用均正常 | 段 [7] |
| AC-6 文档门禁 | `check-docs.ps1` | 通过 | 见 README.md 末次门禁结果 |

## Red（失败先行）

本任务删除的是**不可达的 UI 代码**，无可测逻辑，按判据不适用 TDD，也不新增测试。
以编译门禁与真机操作作为「失败即暴露」的手段：

- 删除后若有任何遗漏的 `R.layout.xxx` 或类型引用，`compileAppDebugKotlin` 会立即失败，
  不会静默通过。
- 真机路径覆盖了本次改动最大的风险点——`MainActivity.getFragmentId` 的 Fragment 分发。

需要说明的是：本次改动过程中出现过一次**逻辑走错方向**（未形成 Red 证据，属设计纠错）：
最初把 `getFragmentId` 简化为 `realPositions[position]`，但 `realPositions` 里书架存的是
占位值 `idBookshelf(0)`，直接返回会让 `getItem` 落到 `else -> MyFragment`，
书架变成「我的」页。已纠正为保留 `idBookshelf → idBookshelf1` 的映射并加注释。

## Green（通过）

- 编译：`BUILD SUCCESSFUL in 46s`
- 单测：72 通过 / 0 失败 / 0 跳过（与删除前数量一致，未丢失用例）
- 构建：`BUILD SUCCESSFUL in 1m 2s`，APK 31.51 MB
- 符号核查：7 个符号零命中，`style2` 目录已删除
- 真机（WWZHIBUKSKCM5LGA）：
  - 书架渲染 12 本，底部「第 1 / 3 页（共 12 本）」
  - 左滑 → 第 2 页（06–10）、第 3 页（11–12），页码同步
  - 右滑 → 回到第 2 页
  - 每页末项完整可见，无需滚动
  - 切「我的」→ 5 项正常；切回书架 → **仍停在第 2 页**（Fragment 未重建）
  - `logcat -b crash` 无本应用崩溃

截图：[t6-shelf-page3.png](t6-shelf-page3.png) · [t6-shelf-page2back.png](t6-shelf-page2back.png) · [t6-shelf-reuse.png](t6-shelf-reuse.png) · [t6-my-page.png](t6-my-page.png)

## 未达成 / 主动放弃

- **阅读链路未测**：为验证分页而插入的书籍只有数据库记录、没有实际文件，双击打开会报错。
  阅读界面与本次改动无关，不属范围。
- **未验证删书后页码收敛**：该逻辑由 `Paginator` 承担，已有 22 个单测覆盖，未在真机重复验证。
- 无未达成的验收标准。

## 残余风险与后续

- **真机数据是构造的**：书架里的 12 本书由 Python 直接写 `books` 表生成，非真实导入。
  这足以验证 UI 渲染与分页，但不覆盖「真实书籍的封面加载、进度显示」等路径。
  测试后已清理设备数据（`files/books` 与临时文件）。
- **`AppConfig.bookGroupStyle` / `bookshelfLayout` 两个属性仍保留**：其 getter 恒为 0，
  且 `PreferKey`、`BackupConfig` 仍引用它们的 key。删除属性会牵动备份恢复逻辑，
  属本次非目标。若要彻底清理，需单独评估对旧备份兼容性的影响。
- **未来若要恢复网格视图**，需从 git 历史恢复本次删除的 13 个文件（6 源码 + 7 布局）
  与 5 处调用点改动。单次提交可整体 `git revert`。
