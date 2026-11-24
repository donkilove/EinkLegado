# search-page 验证证据

> 生成：2026-11-24 · 需求：搜索页改为底栏入口、书架右上角改备份、搜索入口可开关
> 状态：完成（模拟器验证）
> 原始输出：[search-page-check.txt](search-page-check.txt)

## 需求与实现

| 需求 | 实现 |
|---|---|
| 搜索页从底栏进入 | `main_bnv.xml` 新增 `menu_search_page`；点击 `startActivity<SearchActivity>()` |
| 去掉书架右上角搜索按钮 | `main_bookshelf.xml` 的 `menu_search` 替换为 `menu_backup` |
| 右上角改为备份按钮 | 复用 `Backup.backupLocked()`；未配路径时拉起目录选择器，选完自动备份；前台 WaitDialog 可取消 |
| 搜索入口可开关 | `pref_config_other.xml` 的「语言与界面」分组新增 `SwitchPreference` |

## AC ↔ 验证映射

| 验收标准 | 做法 | 结果 | 原始输出 |
|---|---|---|---|
| AC-1 底栏入口能打开搜索页 | 模拟器点击 + `dumpsys` 查 topResumedActivity | 通过：`topResumedActivity=SearchActivity`，搜索页正常显示 | 段 [5] |
| AC-2 右上角为备份按钮 | uiautomator 查 content-desc | 通过：`content-desc="Backup and restore"` 在 (903,213)，放大镜已不存在 | 段 [5] |
| AC-3 关闭开关后底栏隐藏且右上角不恢复 | 注入 `showSearchPage=false` 后重启 | 通过：底栏只剩 Bookshelf/Me；右上角仍是备份图标 | 段 [5] |
| AC-4 底栏顺序与映射正确 | 关闭态下点「我的」 | 通过：正确进入 Me 页，5 项完整，选中态正确 | 段 [5] |
| AC-5 构建与既有单测 | `assembleAppDebug` / `testAppDebugUnitTest` | 通过：构建成功，143 用例 0 失败 | 段 [5] |

截图：[底栏入口](search-page-1-bottom-entry.png) · [搜索页](search-page-2-opened.png) · [返回保持选中态](search-page-3-back-keeps-tab.png) · [关闭后](search-page-4-disabled.png) · [我的页正常](search-page-5-my-ok.png)

## 实现中发现并修正的两个隐患

这两个都不是需求本身要求的，是我在动手时发现**不改就会出错**的地方。

### 隐患 A：底栏菜单项按下标访问会错位

原代码：

```kotlin
binding.bottomNavigationView.menu[realPositions[position]].isChecked = true
```

`realPositions` 存的是逻辑 id（书架 0 / 发现 1 / 订阅 2 / 我的 3）。它们**历史上恰好**与
`main_bnv.xml` 里菜单项的顺序一致，所以用下标碰巧正确。

插入「搜索」后，`menu_discovery` 从索引 1 变 2、`menu_rss` 从 2 变 3、`menu_my_config`
从 3 变 4——巧合失效，会勾错菜单项。

修正：新增 `menuItemIdOf(logicalId)` 显式映射，改用 `menu.findItem(id)?.isChecked = true`。

### 隐患 B：`realPositions` 残留旧值

`upBottomMenu()` 只填充不清空。先开启某开关再关闭，数组里会留下上次的 id，
`getFragmentId()` 可能返回一个已不存在的页面，导致切页空白或错位。

修正：填充前 `realPositions.fill(idBookshelf)`。

这两点都在 AC-4 里被实际验证（关闭态下点「我的」正常进入）。

## 一处默认值决策

`AppConfig.showSearchPage` 的代码默认值取 `true`，与 XML 的 `defaultValue="true"` 一致。

**没有**沿用 `showDiscovery` / `showRSS` 的写法（代码默认 `false`、靠 XML 首次写入 `true`）。
实测发现那两项在全新安装的模拟器上确实不显示（底栏只有书架+我的）——这是既有行为，
不是本次引入的。但搜索入口是新功能，默认不显示会让人以为功能不存在，所以选 `true`。

## 过程中的两次编译失败

1. `selectBackupDir.launch()` 少传参数 —— `HandleFileContract` 的输入类型是
   `(HandleFileParam.() -> Unit)?`，不能省略，改为 `launch(null)`
2. `Unresolved reference 'backuping'` —— 字符串不存在，补加到中英 values

## 未覆盖 / 残余风险

- **备份按钮未实际点按验证**：模拟器为全新安装，无备份路径也未授予存储权限，
  点击会进入目录选择器。备份主流程 `Backup.backupLocked()` 未改动、只是复用，
  且「备份与恢复」页此前已有该功能。**风险中低，但确实没点过。**
- **未在真机验证**：用户设备不在手边，全部在模拟器完成。
- **设置页开关未走 UI 切换**：直接改 SharedPreferences 验证读取路径。pref 键与
  `SwitchPreference` 的绑定与相邻的 `showDiscovery`/`showRss` 完全同构，未额外验证。
- **底栏图标是新建的 vector**：按现有 `ic_bottom_*` 风格画的，视觉效果未与设计稿核对。
