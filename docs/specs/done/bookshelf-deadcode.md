# 书架死代码清理：移除 style2 与网格/紧凑列表分支

> 状态：done · 日期：2026-11-23

## 目标

删除因墨水屏改造而永不可达的书架代码，消除「代码看起来支持、实际进不去」的认知负担，并让 `MainActivity` 的书架分发逻辑与真实行为一致。

## 背景

`TODO.md` T-6 原描述为「网格书架分页未真机验证」。核实后结论变更：不是「未验证」，而是**不可达**。

两个配置 getter 被固定：

| 属性 | 位置 | 现值 |
|---|---|---|
| `AppConfig.bookGroupStyle` | `AppConfig.kt:233-237` | `get() = 0`（标签分组） |
| `AppConfig.bookshelfLayout` | `AppConfig.kt:240-244` | `get() = 0`（列表视图） |

由此产生的不可达路径：

- `MainActivity.kt:491` `if (AppConfig.bookGroupStyle == 1) idBookshelf2 else idBookshelf1`
  → `bookGroupStyle` 恒为 0，**`idBookshelf2` 分支永不执行**，整套 `style2` 包（4 文件）不可达。
- `BooksFragment.kt:74-84` `when (bookshelfLayout)` 的 `1 ->` 与 `else ->` 分支
  → `bookshelfLayout` 恒为 0，**`BooksAdapterList2` 与 `BooksAdapterGrid` 永不实例化**。
- `BooksFragment.kt` 中 6 处、`BookshelfFragment2.kt` 中 5 处 `bookshelfLayout >= 2` 判断恒为 false。

## 范围与非目标

### 范围内

**阶段 1：删除 style2 整套**

- 删除 `app/src/main/java/io/legado/app/ui/main/bookshelf/style2/`（4 文件）：
  `BaseBooksAdapter.kt`、`BooksAdapterGrid.kt`、`BooksAdapterList.kt`、`BookshelfFragment2.kt`
- 删除 `app/src/main/res/layout/fragment_bookshelf2.xml`
- 删除 style2 专属 item 布局：`item_bookshelf_grid_group2.xml`、`item_bookshelf_grid2.xml`、`item_bookshelf_list2.xml`
- `MainActivity.kt`：删除 `idBookshelf2` 常量、`BookshelfFragment2` 导入、`getFragmentId` 中的条件分支，以及 `line 137`、`line 525-526`、`line 538-539` 的相关处理

**阶段 2：删除 style1 内的网格与紧凑列表**

- 删除 `style1/books/BooksAdapterGrid.kt`、`style1/books/BooksAdapterList2.kt`
- 删除 `item_bookshelf_grid.xml`、`item_bookshelf_grid_group.xml`（若仅被上述适配器引用）
- `BooksFragment.kt`：`booksAdapter` 的 `when` 简化为直接用 `BooksAdapterList`；删除 `bookshelfLayout >= 2` 的 6 处判断与 `GridLayoutManager` 相关分支
- `EinkPager` 的 `columns` 参数固定为 1（原为 `if (bookshelfLayout >= 2) bookshelfLayout else 1`）

### 非目标

- **不删除** `AppConfig.bookGroupStyle` / `bookshelfLayout` 这两个属性本身——`PreferKey`、`BackupConfig` 仍引用它们的 key，删除会牵动备份恢复逻辑。
- **不删除** `Preference` 中对应的 key（`PreferKey.bookshelfLayout`、`PreferKey.bookGroupStyle`），避免旧备份恢复失败。
- 不改动 `BaseBookshelfFragment`、`BookshelfViewModel`、`BookshelfFragment1` 的其它逻辑。
- 不改 `dialog_bookshelf_config.xml`（布局设置对话框已在阶段 7 精简过）。

## 验收标准

- AC-1：上述文件删除后 `gradlew :app:compileAppDebugKotlin` 成功，无 unresolved reference。
- AC-2：全仓检索 `style2`、`BookshelfFragment2`、`BooksAdapterGrid`、`BooksAdapterList2`、`item_bookshelf_grid`、`item_bookshelf_list2` 的符号级引用为零（排除 `assets/web` 的 CSS 同名类）。
- AC-3：`gradlew :app:testAppDebugUnitTest` 全部通过（不得因删除而减少既有用例数）。
- AC-4：`gradlew assembleAppDebug` 成功并产出 APK。
- AC-5：真机安装后书架正常显示书籍列表、分类 Tab 可切换、翻页栏正常、左滑不误触。
- AC-6：文档门禁 `tools/check-docs.ps1` 通过。

## 验收标准 ↔ 测试用例映射

| 验收标准 | 测试用例 | 位置 |
|---|---|---|
| AC-1 | `gradlew :app:compileAppDebugKotlin` 退出码 0 | `docs/evidence/t6-deadcode-check.txt` |
| AC-2 | 符号检索脚本输出为空 | 同上 |
| AC-3 | `gradlew :app:testAppDebugUnitTest` 用例数与通过数 | 同上 |
| AC-4 | `gradlew assembleAppDebug` 退出码 0 + APK 体积 | 同上 |
| AC-5 | 真机操作核对（设备 WWZHIBUKSKCM5LGA） | 同上 |
| AC-6 | `check-docs.ps1` 退出码 0 | 同上 |

## 数据流与接口变化

- `MainActivity.getFragmentId(position)` 的返回集合由 `{idBookshelf1, idBookshelf2}` 收敛为 `{idBookshelf1}`（对书架位置而言）。
- `BooksFragment.booksAdapter` 的类型由 `BaseBooksAdapter<*>` 具体化为 `BooksAdapterList`。
- 无数据库、无 SharedPreferences key、无对外接口变化。

## 边界情况与失败处理

- 若某 item 布局被两个适配器共用（`item_bookshelf_list.xml` 可能同时被 `BooksAdapterList` 与 `BooksAdapterList2` 使用），删除前须确认引用方，只删确定仅有 grid/紧凑列表使用的布局。
- `MainActivity` 的 fragment 复用逻辑（`line 525-526`）同时判断两种类型，删掉 style2 分支时要保证 `idBookshelf1` 分支仍能正确复用，否则切换 Tab 会重建 Fragment 导致列表位置丢失。
- 删除后若残留 `R.layout.xxx` 引用，编译会立即失败，不会静默通过。

## 测试方案

- 编译门禁：`compileAppDebugKotlin` 与 `assembleAppDebug`。
- 静态核查：全仓符号检索。
- 单测：沿用既有 `testAppDebugUnitTest`（本任务不新增测试，因为删的是不可达 UI 代码，无可测逻辑）。
- 真机：设备 WWZHIBUKSKCM5LGA，安装 debug 包后按 AC-5 逐项操作。

## 假设与风险

- 假设：用户确认永远不需要网格书架与文件夹分组。规格 `eink-only.md` 阶段 7 记录「分组样式固定标签、视图固定列表」，且用户本轮明确选择删除整套 style2。
- 风险 1：`MainActivity` 的 fragment 复用逻辑较绕，改错会导致切 Tab 时列表重建或空白。缓解：真机验证切换。
- 风险 2：误删共用布局导致其它界面缺资源。缓解：编译期 `R` 引用检查 + 逐个核对引用方。
- 风险 3：删除 981 行后若用户将来想要回网格视图，需从 git 历史恢复。缓解：单次提交可整体 `git revert`。
- 回滚方案：`git revert <commit>` 一次还原全部删除。

## 变更记录

- 2026-11-23：创建。用户确认选 A 盘（删整套 style2 + style1 网格/紧凑列表），并连上真机供验证。
- 2026-11-23：完成并经真机验证。删除 13 个文件（6 源码 + 7 布局），修改 3 个源文件 + 1 个规格索引。

## 决策记录（完成后追加）

- 决策日期：2026-11-23
- 决策：
  1. 删除整套 `style2` 与 style1 的网格/紧凑列表适配器，而不只是「保留代码、隐藏入口」。
  2. 保留 `AppConfig.bookGroupStyle` / `bookshelfLayout` 两个属性及其 `PreferKey` key。
- 理由：
  1. 两个 getter 恒为 0 使其完全不可达，保留只会造成「代码看起来支持、实际进不去」的误解。
     删除后编译期即可发现任何遗漏引用，比留着更安全。
  2. `BackupConfig` 与旧备份文件引用这两个 key，删除 key 会导致恢复旧备份时报错或丢配置。
     属性本身只有两个 getter，维护成本极低，不值得为它承担兼容性风险。
- 被放弃的备选：
  - 只删 style2、保留 style1 网格（B 盘）——用户明确选择 A 盘，一次清理干净。
  - 连 `AppConfig` 两个属性一并删除——放弃，因会牵动备份恢复，超出本次范围。
- 未达成的 AC：无。AC-1 ~ AC-6 全部通过，证据见 `docs/evidence/bookshelf-deadcode-verify.md`。
