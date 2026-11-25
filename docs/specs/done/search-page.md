# 搜索页改为底栏入口，书架右上角改为备份

> 状态：done · 日期：2026-11-24 · 归档：2026-11-25

## 目标

- G1：搜索从「进书架 → 点右上角放大镜」改为**底栏一级入口**，点击直接打开搜索页。
- G2：书架右上角空出的位置改为**备份入口**，让高频操作少一层路径。
- G3：搜索入口**可开关**，不需要的用户能把底栏收回两图标布局。
- G4：不改坏底栏既有页面（书架 / 发现 / 订阅 / 我的）的切换与选中态。

## 背景

该功能在 `TODO.md` 之外直接实施，**实施时未立规格文件**，只提交了证据（`docs/evidence/search-page-check.txt`、`search-page-verify.md` 与 9 张截图），提交 `c50d9e021` 的说明里也写明「规格：无」。

按仓库约定，`docs/evidence/` 的每条证据都应能指回 `docs/specs/done/` 的归档规格，而 `docs/specs/done/README.md` 的编号此前停在 5、无对应条目。本规格是**事后补登记**：内容以已发生的实现与证据为准，本次不新增任何生产代码改动。

## 范围与非目标

### 范围内

新增资源：

- `res/drawable/ic_bottom_search.xml`、`ic_bottom_search_e.xml`（未选中，描边）、`ic_bottom_search_s.xml`（选中，实心）
- `res/values/strings.xml`、`res/values-zh/strings.xml`：`show_search_page`、`show_search_page_summary`、`backuping`

修改：

- `res/menu/main_bnv.xml`：书架之后插入 `menu_search_page`
- `res/menu/main_bookshelf.xml`：`menu_search`（放大镜）→ `menu_backup`（`showAsAction="always"`）
- `res/xml/pref_config_other.xml`：「语言与界面」分组内、`showRss` 之后新增 `SwitchPreference`（`defaultValue="true"`）
- `constant/PreferKey.kt`：新增 `showSearchPage` 常量
- `help/config/AppConfig.kt`：新增 `showSearchPage` getter，代码默认值 `true`
- `ui/main/MainActivity.kt`：底栏点击处理（`menu_search_page` → `startActivity<SearchActivity>()` 并 `return false`）、`upBottomMenu()` 显隐、新增 `menuItemIdOf()` 显式映射
- `ui/main/bookshelf/BaseBookshelfFragment.kt`：菜单项改 `menu_backup`，新增 `backupFromBookshelf()` / `backupTo(path)`

### 非目标

- **不**把 `SearchActivity` 改造成 ViewPager 内的 Fragment——搜索页有独立返回栈，改造面远大于收益。
- **不**改 `Backup.backupLocked()` 主流程，只复用；书架入口与「备份与恢复」页行为保持一致。
- **不**改 `showDiscovery` / `showRSS` 的既有语义与默认值写法。
- **不**调整底栏顺序（搜索固定插在书架与发现之间）。
- **不**为底栏图标做视觉设计评审（见「假设与风险」）。

## 验收标准

- AC-1：底栏出现搜索入口，点击后打开 `io.legado.app.ui.book.search.SearchActivity`，且返回后底栏选中态仍停留在进入前的页面。
- AC-2：书架右上角为备份按钮，原搜索放大镜不再存在。
- AC-3：关闭 `showSearchPage` 后，底栏隐藏搜索入口，且书架右上角**不**恢复搜索按钮。
- AC-4：底栏逻辑页（书架 / 发现 / 订阅 / 我的）切换与选中态正确，插入搜索项后不出现索引错位；开关反复开关后无残留页位。
- AC-5：`gradlew :app:assembleAppDebug` 成功产出 APK；`gradlew :app:testAppDebugUnitTest` 全部通过且用例数不少于改动前的 143。
- AC-6：文档门禁 `tools/check-docs.ps1` 退出码 0。
- AC-7：真机复核——底栏三图标正常、可进入搜索页、关闭开关后剩两图标、重新开启后恢复三图标。

## 验收标准 ↔ 证据映射

| 验收标准 | 做法 | 结果 | 证据位置 |
|---|---|---|---|
| AC-1 | 模拟器 `adb` 点击底栏 + `dumpsys` 查 `topResumedActivity`；返回后截图核对选中态 | 通过：`topResumedActivity=SearchActivity`；返回后底栏仍选中书架 | `search-page-check.txt` 段 [5]、截图 `search-page-1`/`2`/`3` |
| AC-2 | `uiautomator` 查 `content-desc` | 通过：`content-desc="Backup and restore"` 位于 (903,213)，放大镜已不存在 | `search-page-check.txt` 段 [5]、截图 `search-page-1` |
| AC-3 | 注入 `showSearchPage=false` 后重启，`uiautomator` 核对底栏与书架菜单 | 通过：底栏只剩 Bookshelf/Me，右上角仍为备份图标 | `search-page-check.txt` 段 [5]、截图 `search-page-4` |
| AC-4 | 关闭态下点「我的」；并核对 `menuItemIdOf` 与 `realPositions.fill` 的修正 | 通过：正确进入 Me 页，5 项完整，选中态正确 | `search-page-check.txt` 段 [2]、[5]，截图 `search-page-5` |
| AC-5 | `assembleAppDebug` / `testAppDebugUnitTest` | 通过：BUILD SUCCESSFUL（APK 31.52 MB）；143 用例 0 失败 | `search-page-check.txt` 段 [5] |
| AC-6 | `pwsh -File tools/check-docs.ps1` | 通过（补归档后复跑，退出码 0） | `search-page-archive-check.txt` |
| AC-7 | 真机（1216×2640）截图 4 张：底栏三图标 → 进搜索页 → 关闭后两图标 → 重新开启恢复三图标 | 通过（截图可见，时间为 19:50–19:54，晚于模拟器轮次 7:32–7:35） | 截图 `search-page-6`/`7`/`8`/`9`；补记见 `search-page-check.txt` 段 [7] |

## 数据流与接口变化

| 变化 | 位置 | 影响 |
|---|---|---|
| 新增偏好键 `showSearchPage`（默认 `true`） | `PreferKey.kt`、`AppConfig.kt`、`pref_config_other.xml` | 首次安装即写入；旧备份恢复不受影响（新键缺省时走代码默认值） |
| 新增菜单项 `menu_search_page` | `main_bnv.xml` | 底栏项数由 2–5 变为 2–6 可变 |
| 菜单项 `menu_search` → `menu_backup` | `main_bookshelf.xml` | 只影响书架菜单；`R.id.menu_search` 在其它 7 个菜单文件里仍存在，互不影响 |
| 新增 `menuItemIdOf(logicalId)` | `MainActivity.kt` | 取代 `menu[realPositions[position]]` 的下标写法 |

无数据库、无 Room schema、无对外 API 变化。

## 边界情况与失败处理

- **底栏下标错位**：`realPositions` 存的是逻辑 id，历史上恰好与 `main_bnv.xml` 顺序一致才碰巧可用。插入搜索项后 `menu_discovery` 由 1 变 2 等，下标失效。处理：新增 `menuItemIdOf()`，改用 `menu.findItem(id)?.isChecked = true`。
- **`realPositions` 残留旧值**：`upBottomMenu()` 原先只填充不清空，开关反复开关后 `getFragmentId()` 可能取到已不存在的页位，导致切页空白。处理：填充前 `realPositions.fill(idBookshelf)`。
- **搜索项不属于 ViewPager**：点击须 `return false`，否则底栏会停在一个并不存在的「搜索页」上。
- **未配置备份路径**：`backupFromBookshelf()` 拉起目录选择器；用户取消则不做任何事。目录选择器回调用 `HandleFileContract`，其输入类型为 `(HandleFileParam.() -> Unit)?`，必须显式 `launch(null)`。

## 测试方案

- 构建：`gradlew :app:assembleAppDebug`。
- 单测：`gradlew :app:testAppDebugUnitTest`，用例数不得少于 143。
- 静态：`uiautomator dump` 核对菜单项与 `content-desc`。
- 设备：模拟器 `emulator-5554`（android-35 google_apis x86_64）覆盖 AC-1~AC-5；真机覆盖 AC-7。
- 门禁：`pwsh -File tools/check-docs.ps1`。

## 假设与风险

- **备份按钮未实际点按**：AC-2 只证明图标存在与点击分发到位。备份主流程 `Backup.backupLocked()` 未改动、只是复用，且「备份与恢复」页此前已有该功能，风险中低；但真机上未点过一次，仍属未验证。
- **设置页开关的真机切换**：模拟器轮次是直接改 SharedPreferences，未走 UI；真机轮次的 8、9 两张截图显示了关闭与重新开启的效果，可视为补上了 UI 路径，但当时未记录操作步骤。
- **底栏图标是新建的 vector**：按现有 `ic_bottom_*` 风格绘制，未与设计稿核对。
- **证据缺口（本次已修正）**：实施时只提交证据未立规格，且 `search-page-check.txt` 段 [6] 与 `search-page-verify.md` 记的「全部在模拟器完成、未上真机」与后续真机截图矛盾。本次补登记时已追加段 [7] 并在 `search-page-verify.md` 中更正，原始记录未删除。

## 变更记录

- 2026-11-24：实施并提交 `c50d9e021`。实施时未立规格文件。
- 2026-11-25：**补归档**。新建本规格；`docs/specs/done/README.md` 增补编号 6；`search-page-check.txt` 追加段 [7] 的真机复核记录；`search-page-verify.md` 更正真机结论并补 AC-6/AC-7；新增 `docs/evidence/search-page-archive-check.txt` 记录本次补登记核查与门禁输出。

## 决策记录（完成后追加）

- 决策日期：2026-11-25
- 决策：
  1. 底栏点击**启动独立 Activity**，不把 `SearchActivity` 改造成 ViewPager 页。
  2. `AppConfig.showSearchPage` 的**代码默认值取 `true`**，与 XML `defaultValue` 一致；不沿用 `showDiscovery`/`showRSS` 的「代码默认 false、靠 XML 首次写入 true」写法。
  3. 底栏选中态改用 `menuItemIdOf` 显式映射，并在填充 `realPositions` 前复位。
- 理由：
  1. 搜索页有独立返回栈与输入法交互，改造成 Fragment 要动导航、返回键与状态保存，收益远小于成本；现有 Activity 直接复用最稳。
  2. 实测全新安装时 `showDiscovery`/`showRSS` 因键尚未写入而取到代码默认 `false`，底栏只显示书架+我的。搜索入口是新功能，若默认不显示会被当成「没做」，故取 `true`。
  3. 逻辑 id 与菜单项顺序之间只是历史巧合，插入新项即失效；显式映射把耦合点收敛到一处，避免以后再加项时重演。
- 被放弃的备选：
  - 把搜索页并入 ViewPager 作为第 4 个页面——放弃，改造面过大。
  - 保持 `showSearchPage` 代码默认为 `false` 与相邻开关一致——放弃，会重演「新功能看起来不存在」。
  - 关闭后把书架右上角恢复成搜索按钮——放弃，用户明确要求不恢复。
- 未达成的 AC：无。AC-1 ~ AC-7 均有证据；其中 AC-2 仅验证到「图标与分发」，实际备份执行未点按，已在「假设与风险」登记。
