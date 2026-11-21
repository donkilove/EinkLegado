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

