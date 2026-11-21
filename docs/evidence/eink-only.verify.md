# eink-only 验证证据

> 生成：2026-11-21 · 规格：[docs/specs/eink-only.md](../specs/eink-only.md) · 状态：阶段 1、2 完成；阶段 3 已取消
> 原始输出：[eink-only-check.txt](eink-only-check.txt)

## AC ↔ 验证映射

| 验收标准 | 做法 | 结果 | 原始输出 |
|---|---|---|---|
| AC-1 `assembleAppDebug` 成功 | 本机构建 | 通过（9m24s） | [5] 段 |
| AC-2 `assembleAppRelease` 成功 | 本机构建（含 R8 + 资源收缩） | 通过（15m9s） | [6] 段 |
| AC-3 被删符号零残留 | 11 个符号全仓检索 | 通过 | [7] 段 |
| AC-4 `java.openVideoPlayer` 空实现 | 代码审查 | 通过：`SourceHelp.openVideoPlayer` 保留签名，仅 `toastOnUi` 提示 | — |
| AC-5 EInk 下 `selectableItemBackground` 解析为无 ripple 静态 drawable | aapt 资源链接 + 构建 | 通过：debug 与 release 资源阶段均成功，`AppTheme.EInk` 重定义该属性并置空 `colorControlHighlight` | [5][6] 段 |
| AC-6 翻页动画只剩两种 | 代码审查 | 通过：`setCurPageAnim` 钳制为 `scrollPageAnim`/`noAnim`，设置项收敛为两项 | — |
| AC-7 文档门禁通过且可检出破坏 | 正跑 + 四种负向自测 | 通过 | [2][3] 段 |

## 补充指标（非验收项）

| 指标 | 改造前 | 改造后 | 变化 |
|---|---|---|---|
| debug APK | 37.00 MB | 31.51 MB | -14.8% |
| release APK | 19.08 MB（基线工作树实测） | 16.29 MB | -14.6% |
| 依赖 | gsyvideoplayer ×2、danmaku、LyricViewX、Firebase ×3 | 移除 | — |

规划阶段曾设「release 降幅 ≥15%」，实测 14.6% 未达到；经与用户确认，该目标已放弃（理由见规格「已取消的范围」），因此不作为验收项。

## Red（失败先行）

本任务以删除与视觉调整为主，无可测逻辑，不适用 TDD。以两类失败证据替代：

1. 文档门禁负向自测：四种破坏场景均确认 exit=1，见 [3] 段。
2. 阶段 2 的两次真实构建失败（资源链接错误、未解析引用），见 [5] 段——说明删除不彻底会被构建拦下，而非静默通过。

## Green（通过）

- `assembleAppDebug`：BUILD SUCCESSFUL in 9m24s
- `assembleAppRelease`：BUILD SUCCESSFUL in 15m9s
- 残留核查：11 个符号零命中
- 文档门禁：通过（44 个 .md、1914 个文件）
- 负向自测：5/5 通过

## 未达成 / 主动放弃

- **主动放弃**：原「阶段 3」的 RSS、发现页、自动更新、字典/段评裁剪，以及主题收敛。用户在评审时指出这些功能面向电纸书看小说的真实场景应当保留，裁剪会损伤可用性。规格已记录决定与理由。
- **主动放弃**：规划中的「release 降幅 ≥15%」目标（实测 14.6%）。APK 剩余重量集中在 native 库与 `assets/web` 前端，与已删功能无关。
- 无未达成的验收标准。

## 残余风险与后续

- **真机验证覆盖有限**：已在 BOOX Poke6（Android 11）上验证主链路可用、EInk 主题生效、夜间按钮与主题设置项已移除；但长列表快速滚动、删除动画、弹窗过渡这类"一闪而过"的效果未逐项肉眼确认，仍建议持续观察。
- 书源若返回音视频类型的书，入口会提示「墨水屏版本不支持」，属预期行为，需真机确认提示可见。
- `AudioPlay` / `VideoPlay` 已改为状态容器空壳，`AudioPlay.book` 恒为 null，依赖它的登录/RSS 分支不再被执行。
- RSS 视频类型条目（`type == 2`）已改为提示，RSS 其余类型不受影响。
- 主题已固定为 EInk，无法切回浅色/深色；若需回退，改动 `AppConfig.isEInkMode` 一处并恢复设置项即可，配色分支代码未删。
