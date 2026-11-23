# EInk 专属化改造

> 状态：in-progress · 日期：2026-11-21

## 目标

把本项目从「通用阅读器 + 墨水屏模式」改造为「墨水屏专属阅读器」，删除在墨水屏上无实际价值或纯属视觉负担的功能与特效。

- G1：删除视频、弹幕、音频播放器、Firebase 统计四个模块及其专属依赖。
- G2：墨水屏下不再出现水波纹、淡入淡出、布局过渡、半透明遮罩等对电子纸无意义的视觉效果。
- G3：翻页动画收敛为「无动画」「滚动」两种；阅读背景只保留纯色。
- G4：阅读主链路（书架／搜索／书源／阅读／本地导入／漫画／备份恢复／朗读／Web 端编辑）不受影响。
- G5：书源 JS 兼容性不退化——被删功能的公开函数保留签名与空实现。

## 范围与非目标

### 范围内

阶段 1（视觉与交互，不删功能）：

- `app/src/main/res/values*/styles.xml`、`themes.xml`：EInk 主题下重定义 `selectableItemBackground` 系列属性，全局关闭水波纹。
- `app/src/main/java/io/legado/app/utils/ConstraintModify.kt`：EInk 下跳过 `beginDelayedTransition`。
- `app/src/main/java/io/legado/app/model/BookCover.kt`：去掉 `crossFade(1500)`。
- `app/src/main/java/io/legado/app/ui/widget/recycler/scroller/FastScroller.kt`、`ui/widget/checkbox/SmoothCheckBox.kt`、`ui/widget/anima/RotateLoading.kt`、`ui/widget/anima/explosion_field/`：EInk 下关闭动画。
- `app/src/main/java/io/legado/app/ui/book/read/ReadMenu.kt`：半透明背景改不透明。
- 翻页动画：删除 `ui/book/read/page/delegate/CoverPageDelegate.kt`、`SlidePageDelegate.kt`、`SimulationPageDelegate.kt`，同步 `constant/PageAnim.kt`、`ReadView.upPageAnim`、翻页设置项。
- 阅读背景：删除 `ReadBookConfig` 中 `bgType` 的图片/文件分支与选图 UI。

阶段 2（删除档 A 模块）：

- 视频：`ui/video/`、`service/VideoPlayService.kt`、`help/gsyVideo/`、`help/exoplayer/ExoVideoManager.kt`、`ExoPlayerManager.kt`、`Exo2MediaPlayer.kt` 及全部入口。
- 弹幕：随视频一并删除，移除 danmaku 依赖。
- 音频播放器：`ui/book/audio/`、`service/AudioPlayService.kt` 及全部入口，移除 LyricViewX 依赖。
- Firebase：`com.google.gms.google-services` 插件、`firebase-bom`、`firebase-analytics`、`firebase-perf`、`app/google-services.json`。
- 书源兼容：`help/JsExtensions.kt` 的 `openVideoPlayer` 重载保留签名，内部记录日志后返回。
- 设置项：删除 `videoSetting`、`mediaButtonOnExit`、`readAloudByMediaButton`（保留朗读本身）中仅服务于已删模块的条目。

### 非目标

- 不修改 Room schema（`app/schemas/` 不变，RSS／字典数据实体保留）。
- 不修改包名（`io.legado.app`）与签名配置。
- 不重构 UI 外壳、不改导航结构（留给后续「路线 3」）。
- 不删除 RSS／发现页／自动更新／字典段评／朗读／Web 端，见下节「已取消的范围」。
- 不优化书源规则引擎。

### 阶段 4：设置页分页（2026-11-21）

背景：电子纸上滚动会持续整屏刷新并累积残影，设置页项数一多就必须长距离滚动。改为「一屏点完」的层级结构。

| 页面 | 改造前 | 改造后 |
|---|---|---|
| 「我的」`pref_main` | 13 项，需滚约 0.5 屏 | 3 个二级入口（内容管理 4 项 / 设置 / 其它），首屏零滚动 |
| 「其他设置」`pref_config_other` | 37 项，需滚 4 屏 | 6 个二级入口（不含「声音与按键」），首屏零滚动 |
| 「阅读设置」`pref_config_read` | 30 项平铺，需滚 3 屏 | 5 个二级入口，首屏零滚动 |

实现要点：

- 三个 `pref_*.xml` 改为嵌套 `<androidx.preference.PreferenceScreen>`；子项 **key 与属性一字未改**，因此各页原有的点击处理逻辑完全不需要迁移。
- 三个宿主结构不同，各自接管导航（AndroidX 默认的 `showPreferenceScreen` 在这三种宿主下都会替换错容器）：
  - `MyFragment`：实现 `OnPreferenceStartScreenCallback`，`childFragmentManager` + 返回栈 + 标题栏返回箭头。
  - `OtherConfigFragment`：在 `onPreferenceTreeClick` 里拦截 `PreferenceScreen`，用 `parentFragmentManager` 压栈。
  - `MoreConfigDialog`：自建标题行（返回箭头 + 分类名），并拦截对话框返回键。
- `ConfigActivity` 新增 `upTitleBar(title, showBack, onBack)` 供子页更新标题与返回入口，并注册「返回栈优先」的返回处理，避免子屏时直接关闭页面。
- 新增 13 个分类名中英文字符串。

验收：

- 三个设置页首屏无需滚动即可看到全部入口。
- 子屏返回：标题栏箭头与系统返回键都能退回上级列表，且不关闭宿主页面（ConfigActivity / 对话框）。
- 真机 BOOX Poke6 上三个页面均已验证。


### 阶段 5：长列表分页（2026-11-21）

背景：书源、替换规则、TXT 目录规则、字典规则四个页面是**动态长列表**（书源可达数百条），电子纸上滑到底要刷新几十屏，残影累积严重。与设置页不同，这里不是固定项数，需要给列表加翻页器。

实现：

- 新增通用 `Paginator<T>`：**每页条数按实际项高校准**，不写死。数据变化时越界自动收敛；只做切片，不持有业务语义。
- 新增 `PageBar` 翻页栏控件：上一页 / 「第 x / y 页（共 n 条）」/ 下一页；只有一页时整条隐藏；到达首尾时按钮置灰。
- 四个列表页的数据注入改为「全量进 `Paginator`、切片进 Adapter」：
  `BookSourceActivity`、`ReplaceRuleActivity`、`DictRuleActivity`、`TxtTocRuleActivity`。
- **书架同样分页**：`BooksFragment` 接入 `Paginator` 与 `PageBar`，数据源仍是 `flowByGroup`，每个 Tab 独立分页。条数自适应时区分布局：列表模式按项高逐项累加；**网格模式按列步进累加行高再乘列数**，保证每页始终是整行。翻页栏放在下拉刷新容器之外。（网格模式已于 2026-11-23 随死代码清理删除，现存代码只有列表路径。）
- **条数校准（逐项累加）**：各列表项高度差异很大（TXT 目录规则单条约 175px、替换净化约 113px），任何固定条数都会出现「页内仍需滚动」或「一页空太多」。四个页面在首次渲染后**逐项累加实际高度，累加到超过可视区为止**，得到的条数即该页精确容量（下限 3）。首次触发时列表可能尚未渲染完成，按 50ms 重试至多 20 次；校准只做一次以免反复重排。
- 翻页后列表回到顶部；搜索仍作用于全表（数据层未变），结果变化后页码由 `Paginator` 自动收敛。
- **选中状态跨页保留**：四个 Adapter 的选中集合按对象维护（如 `selected: LinkedHashSet<BookSourcePart>`），不依赖 position，切片不影响。

验收：

- 每页条数按项高自动决定，**一屏放得下、页内不需要滚动**。
- 底部显示「第 x / y 页（共 n 条）」；仅一页时翻页栏自动隐藏。
- 翻页后选中状态保留（真机验证：第 2 页全选 8 条 → 第 3 页显示 0/5 → 翻回第 2 页仍为 8/8）。
- 真机 BOOX Poke6 实测：TXT 目录规则 29 条 → **5 项/页共 6 页**；替换净化 21 条 → **8 项/页共 3 页**。两页按各自项高得到不同条数，末项底部均在翻页栏之上，无需滚动。
- 书架实测：8 本 → 「第 1 / 2 页（共 8 本）」，列表模式每页 4 本。

### 阶段 6：书架与主界面的滑动、刷新动画（2026-11-21）

背景：电子纸上滑动跟手动画与循环旋转图标都会持续整屏刷新并留下残影；另外发现从「我的」二级页切走后返回仍滞留在二级页。

| 问题 | 处理 |
|---|---|
| 书架左右滑动切分类有跟手动画 | 新增 `NoAnimViewPager`：**禁用手势滑动**，只由 Tab / 底部导航切换，且切换瞬时完成；同时**吞掉水平手势**，避免事件漏到列表上误触（实测会误打开书籍）。垂直滚动不受影响 |
| 主界面左右滑动切大页 | 主界面与书架分类的 ViewPager 全部换用 `NoAnimViewPager` |
| 下拉刷新的旋转箭头 | 新增 `StaticRefreshLayout`：把 `SwipeRefreshLayout` 内置指示器推到可视区外，下拉手势与刷新回调不变；书架顶部改为「刷新中」静态提示，1.5 秒后消失 |
| 书籍右侧刷新图标一直转 | 该图标在墨水屏下不转后几乎不可见，**改为静态文字「更新中」**（7 个书架项布局），适配器改为按可见性控制 |
| 「我的」页滞留二级页 | `MyFragment` 新增 `resetToRoot()`；`MainActivity` 在切到该 tab 与重复点击该 tab 时调用 |

验收（真机 BOOX Poke6）：

- 二级设置页下点击底部「我的」→ 回到根页。
- 书架左右滑动：页面不变、不会误打开书籍；点击分类 Tab 瞬时切换；列表上下滚动正常。
- 下拉刷新：无旋转箭头，顶部出现「刷新中」并在 1.5 秒后消失。
- 书籍刷新状态以「更新中」文字呈现。

> 取舍：书架与主界面的水平滑动已完全禁用（分类只能点 Tab、大页面只能点底部导航）。曾尝试「保留滑动、去掉吸附动画」，但跟手位移本身在墨水屏上就是连续刷新，且嵌套 ViewPager 会互相抢占手势，经与用户确认后改为直接禁用。

### 阶段 7：书架菜单与布局设置精简（2026-11-21）

背景：书架三点菜单里的「添加网址」「导出书单」「导入书单」「日志」在电纸书上没有使用场景；布局设置里的分组样式与多种网格视图只增加选择负担。

| 项目 | 处理 |
|---|---|
| 三点菜单 | 移除上述 4 项，仅保留 更新目录 / 添加本地 / 远程书籍 / 书架管理 / 缓存导出 / 分组管理 / 书架布局 |
| 分组样式 | 移除下拉选择；`AppConfig.bookGroupStyle` 固定返回 `0`（标签分组） |
| 视图 | 移除紧凑列表与网格 2~6 选项，只保留「列表」；`AppConfig.bookshelfLayout` 固定返回 `0` |
| 对话框结构 | 改为纵向三行：书架视图样式（静态文本）/ 列表间距（滑块 + 数值）/ 书籍排序条件（下拉）。原 ConstraintLayout 在视图区只剩一项后变矮，间距滑块会横穿到排序选项中间 |
| 备份与恢复 | 从「我的 → 设置」二级页提升为「我的」根页入口，并**拆成两个独立页面**：「WebDav 设置」（服务器地址/账号/密码/子文件夹/设备名称/同步阅读进度/同步增强，7 项）与「备份与恢复」（备份路径/备份/恢复/忽略列表/仅最新/自动检查，6 项）。「我的」根页由 3 项变为 5 项 |

验收（真机 BOOX Poke6）：

- 书架三点菜单只剩 7 项，四个被移除项不再出现。
- 布局对话框三行排列，**无控件重叠**；排序为下拉选择，可展开 6 个选项。
- 书架仍按标签分组显示，列表视图正常。

**后续（2026-11-23）：死代码清理**

阶段 7 只是移除了入口，代码仍在。经核实 `bookGroupStyle` 与 `bookshelfLayout` 两个 getter
恒为 0，导致整套 `style2` 书架与 style1 的网格/紧凑列表分支**永不可达**。
已一并删除，共 13 个文件（6 源码 + 7 布局），详见
[bookshelf-deadcode.md](done/bookshelf-deadcode.md)。本阶段「视图」与「分组样式」
两行描述的固定值保持不变，只是其依赖的不可达代码已不存在。

### 已取消的范围

原规划的「阶段 3」整体取消（2026-11-21 与用户确认），理由：本软件面向电纸书看小说，以下功能都是真实使用场景，裁剪会损伤可用性而非提升体验。

| 原计划裁剪项 | 决定 | 理由 |
|---|---|---|
| 发现页 | 保留 | 书源探索找新书的主入口，删掉后只剩搜索 |
| 字典 / 段评 | 保留 | 查词是阅读刚需，段评是网文阅读的一部分 |
| RSS 订阅 | 保留 | 追更、订阅内容的实际用法，墨水屏适合长时间阅读 |
| 自动更新检查 | 保留 | 电纸书安装 APK 麻烦，自动检查更新是加分项 |
| 主题收敛（删配色分支与 style） | 取消 | 只减代码分支，不动功能，收益不足以承担改动风险 |

> 注：主题虽不做「收敛」（浅色/深色的代码分支与 style 全部保留），但已按用户要求固定为 EInk 且移除切换入口，见「变更记录」2026-11-21 第二条。

同时放弃规划中的「release 体积下降 ≥15%」目标：实测 14.6%，APK 剩余重量集中在 native 库（cronet、libarchive、renderscript）与 `assets/web` 前端，与上述功能无关；为凑数字删可用功能属于本末倒置。该目标不作为验收项。


## 验收标准

- AC-1：`assembleAppDebug` 成功，无新增编译错误。
- AC-2：`assembleAppRelease`（含 minify + shrinkResources）成功。
- AC-3：源码中不再存在被删类型／依赖的残留引用（`VideoPlayService`、`AudioPlayService`、`gsyVideo`、`danmaku`、`LyricViewX`、`firebase` 的符号级引用为零）。
- AC-4：`java.openVideoPlayer` 仍可调用，行为为记录日志后返回，不抛异常。
- AC-5：EInk 主题下 `selectableItemBackground` 解析为无 ripple 的静态 drawable（资源可被 aapt 解析、构建通过）。
- AC-6：翻页动画仅剩 `noAnim` 与 `scrollPageAnim` 两种取值可达。
- AC-7：文档门禁 `tools/check-docs.ps1` 通过，且四种破坏场景均可被检出。

## 验收标准 ↔ 测试用例映射

| 验收标准 | 测试用例 | 位置 |
|---|---|---|
| AC-1 | `gradlew assembleAppDebug` 退出码 0 | `docs/evidence/eink-only-check.txt` |
| AC-2 | `gradlew assembleAppRelease` 退出码 0 | `docs/evidence/eink-only-check.txt` |
| AC-3 | 残留符号检索脚本输出为空 + 构建期 Kotlin 编译零 unresolved 引用 | `docs/evidence/eink-only-check.txt` |
| AC-4 | 代码审查 + 编译通过（`openVideoPlayer` 签名存在且空实现） | 人工审查记录 |
| AC-5 | aapt 处理资源成功（包含在 AC-1 构建内） | `docs/evidence/eink-only-check.txt` |
| AC-6 | `PageAnim.kt` 常量与 `ReadView.upPageAnim` 分支审查 | 人工审查记录 |
| AC-7 | 门禁正跑 + 四种负向自测 | `docs/evidence/eink-only-check.txt` |

说明：本仓库无可自动运行的 UI 测试设施，AC-4／AC-6 以代码审查加编译验证替代，真机行为未经验证，列入残余风险。

## 数据流与接口变化

- 删除系统服务：`VideoPlayService`、`AudioPlayService`，同步删除 `AndroidManifest.xml` 中的声明与通知渠道。
- 保留并继续使用：`HttpReadAloudService`、`TTSReadAloudService`、`BaseReadAloudService`（朗读），故 **media3 与 okhttp-datasource 依赖保留**。
- 对外 JS 接口：`openVideoPlayer(String, String)`、`openVideoPlayer(String, String, boolean)` 签名不变，实现改为空操作 + 日志。
- Room 数据库：无变化。
- 依赖变化：移除 `gsyVideoPlayer-java`、`gsyVideoPlayer-exo2`、`danmakuFlameMaster`、`lyricViewx`、`firebase-bom`、`firebase-analytics`、`firebase-perf` 与 `google-services` 插件。

## 边界情况与失败处理

- 书源调用 `java.openVideoPlayer`：记录日志后直接返回，书源流程不中断。
- 用户在旧版本中配置过视频/音频相关设置项：对应键保留在 SharedPreferences 中但不再读取，无崩溃风险。
- 已下载的音频/视频缓存目录：不再读取，不影响启动；不主动删除用户数据。
- 混淆：删除依赖后需确认 `proguard-rules.pro` 中相关 keep 规则同步移除，避免 R8 报 -dontwarn 缺失类警告。

## 测试方案

- 构建门禁：`assembleAppDebug` 与 `assembleAppRelease` 必须通过（AC-1、AC-2）。
- 静态核查：PowerShell 脚本检索被删符号残留（AC-3）。
- 文档门禁：`tools/check-docs.ps1` 正跑 + 四种负向破坏自测（AC-7）。
- 无新增可测逻辑，不引入单元测试；沿用仓库既有 `app/src/test` 设施作为基线，不新增测试文件。

## 假设与风险

- 假设：墨水屏设备不需要视频/弹幕/本地音频播放；朗读功能保留（用户明确要求）。
- 风险 1：`media3` 与视频删除存在耦合（`AnalyzeUrl.kt` import `MediaItem`），删视频后需清理该处引用，否则编译失败。缓解：编译门禁。
- 风险 2：删除 `gsyVideo` 目录后 `SourceHelp.openVideoPlayer` 及菜单回调仍引用，需一并改为空实现。缓解：编译器未解析引用错误。
- 风险 3：`app/google-services.json` 删除后若插件未移除会构建失败。缓解：插件与依赖同一提交内移除。
- 回滚方案：本轮改动按阶段独立提交，任一阶段可用 `git revert <commit>` 整体回退；数据库未变，回退不影响用户数据。

## 变更记录

- 2026-11-21：初版规格，用户确认「一次做到阶段 2」，并同意建立 `docs/specs`、`docs/evidence` 与文档门禁。
- 2026-11-21：用户指出本软件面向电纸书看小说，RSS／发现页／自动更新／字典段评均有真实使用场景；据此取消原「阶段 3」全部功能裁剪，主题收敛一并取消，并放弃「release 降幅 ≥15%」目标。范围收敛为本规格的「阶段 1 + 阶段 2」。
- 2026-11-21（第二批）：用户确认「默认 EInk、不提供回退」。据此 `AppConfig.isEInkMode` 改为常量 `true`、`themeMode` 默认值改为 `"3"`，移除「我的」页主题模式设置项、阅读菜单夜间按钮、主题配置界面的昼夜切换项。浅色/深色/透明的代码分支保留未删，需要回退时改动 `isEInkMode` 一处即可。
- 2026-11-21（第三批）：用户指出设置页靠滚动找选项在电子纸上会累积残影，确认为「我的」「其他设置」「阅读设置」三个页面做二级分页，分组方案经用户确认。实现见「阶段 4」。
- 2026-11-21（第四批）：移除「内容管理」中的 Web 服务入口与「其他设置」中的「声音与按键」入口（两者在电纸书设备上无实际用途）；修复子页返回后上级页返回箭头消失的问题（`OtherConfigFragment.upTitleBar` 在根页把 `navigationIcon` 置空，改为箭头常驻）。Web 服务与媒体键的**代码未删除**，仅移除入口，需要彻底清理时另做。
- 2026-11-21（第五批）：按用户要求移除**全部帮助入口**——34 处 `showHelp` 调用（分布在 17 个界面类）、12 个菜单文件里的「帮助」项、`showHelp` 的两个扩展函数、`ReadMenu.CallBack.showHelp` 接口方法与 `ReadBookActivity` 的重写。帮助用的 Markdown 文档仍保留在 `assets/web/help/md/`，未删除。
- 2026-11-21（第六批）：用户要求内容管理下属的四个列表页改为分页展示。新增 `Paginator` 与 `PageBar`，四个列表页每页 8 项；确认「搜索全表、翻页保留选中」。实现见「阶段 5」。
- 2026-11-21（第七批）：用户反馈分页后页内仍需滚动。改为按实际项高自动决定条数（逐项累加填满可视区 + 渲染完成重试），不再写死。
- 2026-11-21（第八批）：用户要求去掉书架与主界面的滑动/刷新动画，并修复「我的」页切走后滞留二级页。实现见「阶段 6」。
- 2026-11-21（第九批）：书架菜单移除「添加网址 / 导出书单 / 导入书单 / 日志」，布局设置去掉分组样式与网格视图（固定标签分组 + 列表）。实现见「阶段 7」。

