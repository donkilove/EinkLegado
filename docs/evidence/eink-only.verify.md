# eink-only 验证证据

> 生成：2026-11-21 · 规格：[docs/specs/eink-only.md](../specs/eink-only.md) · 状态：阶段 1、2 完成，阶段 3 未开始
> 原始输出：[eink-only-check.txt](eink-only-check.txt)

## AC ↔ 验证映射

| 验收标准 | 做法 | 结果 | 原始输出 |
|---|---|---|---|
| AC-1 `assembleAppDebug` 成功 | 本机构建 | 通过（9m24s） | [5] 段 |
| AC-2 `assembleAppRelease` 成功 | 本机构建（含 R8 + 资源收缩） | 通过（15m9s） | [6] 段 |
| AC-3 被删符号零残留 | 11 个符号全仓检索 | 通过 | [7] 段 |
| AC-4 `java.openVideoPlayer` 空实现 | 代码审查 | 通过：`SourceHelp.openVideoPlayer` 保留签名，仅 `toastOnUi` 提示 | — |
| AC-5 release 体积降幅 ≥15% | 体积对比 | **部分验证**：debug 37.00→31.51 MB（-14.8%）；release 16.29 MB 为新基线，改造前未采集 release 基线 | [8] 段 |
| AC-6 翻页动画只剩两种 | 代码审查 | 通过：`PageAnim` 仅 `scrollPageAnim`/`noAnim` 可达，设置项已收敛为两项 | — |
| AC-7 文档门禁通过且可检出破坏 | 正跑 + 四种负向自测 | 通过 | [2][3] 段 |

## Red（失败先行）

本任务以删除与视觉调整为主，无可测逻辑，不适用 TDD。以两类失败证据替代：

1. 文档门禁负向自测：四种破坏场景均确认 exit=1，见 [3] 段。
2. 阶段 2 的两次真实构建失败（资源链接错误、未解析引用），见 [5] 段——说明删除不彻底会被构建拦下，而非静默通过。

## Green（通过）

- `assembleAppDebug`：BUILD SUCCESSFUL in 9m24s
- `assembleAppRelease`：BUILD SUCCESSFUL in 15m9s
- 残留核查：11 个符号零命中
- 文档门禁：通过（44 个 .md、1954 个文件）
- 负向自测：5/5 通过

## 未达成 / 主动放弃

- AC-5 仅为部分验证：改造前只采集了 debug 基线，未构建 release 基线，无法给出 release 的严格降幅。如需严格对照，需在未改动的 `main` 上另跑一次 `assembleAppRelease` 作基线。
- 阶段 3（RSS、发现页、自动更新、字典/段评、主题收敛）不在本轮范围，未实施。

## 残余风险与后续

- **真机行为未验证**：EInk 主题下是否真的无闪烁、`AppTheme.EInk` 的 ripple 覆盖是否对所有控件生效、朗读是否仍正常，都需要设备实测。当前只有编译级验证。
- 音频/视频书籍的入口已改为提示，但书源仍可能返回音视频类型的书，打开后会提示不支持——属于预期行为，需真机确认提示可见。
- `AudioPlay` / `VideoPlay` 已改为状态容器空壳，`AudioPlay.book` 永远为 null，依赖它的登录/RSS 分支逻辑不再被执行。
