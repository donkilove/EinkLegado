# p3-deadcode 调查结论

> 生成：2026-11-23 · 性质：只读调查（未改动任何代码） · 结论：**不清理**
> 原始输出：[p3-deadcode-check.txt](p3-deadcode-check.txt)

## 要回答的问题

`docs/specs/done/eink-only.md` 的变更记录写着：

> 主题虽不做「收敛」（浅色/深色的代码分支与 style 全部保留），但已按用户要求固定为 EInk 且移除切换入口。

问题是：这些保留的浅色/深色分支，现在到底是死代码还是活代码？值不值得清理？

## 调查结论

**分三类，只有一小部分是真死代码，且不建议清理。**

### A 类：78 处 `isEInkMode` 引用中的绝大多数 —— 不是死代码

形态是 `if (AppConfig.isEInkMode) { 不做动画 } else { 做动画 }`。
`isEInkMode` 恒为 `true`，运行时永远走 EInk 支——

但这是**当前生效的实现，不是死代码**。删 EInk 支会破坏功能；删 else 支会失去规格明确要保留的回退能力。

涉及：`ReadMenu`、`MangaMenu`、`SearchMenu`、`RotateLoading`、`FastScroller`、
`SmoothCheckBox`、`ConstraintModify`、`Snackbars`、`ExplosionView`、`TitleBar`、
`BaseActivity`、`PageDelegate`、`BookCover`、`AutoPager` 等 30 余处。

### B 类：`isNightTheme`（深色渲染）—— 完全不是死代码

这是调查中最容易误判的部分。`isNightTheme` 看似与主题绑定，实际是**独立的机制**，
控制「墨水屏下黑底白字」的渲染：

- `ReadBookActivity.kt:1187` 阅读菜单的日夜切换按钮仍在调用
- `ThemeConfigFragment` 设置页仍在读写
- `ReadBookConfig`、`BookCover`、`WebViewPool`、`PreferenceCategory`、`ViewExtensions`
  都按它选色

**它活跃可用，不能动。**

### C 类：真正不可达的部分 —— 只剩 `Theme.Light` / `Theme.Dark` 的主题样式分支

`ThemeConfig.getTheme()`：

```kotlin
fun getTheme() = when {
    AppConfig.isEInkMode -> Theme.EInk     // 恒命中
    AppConfig.isNightTheme -> Theme.Dark   // 永不命中
    else -> Theme.Light                    // 永不命中
}
```

这两个**主题样式分支**确实永不返回，但它们只影响 `setTheme(R.style.AppTheme_*)` 这类外观切换。

## 为什么建议不清理

| 维度 | 评估 |
|---|---|
| 收益 | 极小：约 6 个文件、十几行 |
| 风险 | 不小：`BookInfoActivity:116` 显式传 `toolBarTheme = Theme.Dark`，绕过了 `getTheme()`；枚举值不能删，只删分支则清理不彻底 |
| 与规格冲突 | 直接冲突。`eink-only.md` 明确决策「保留分支作为回退退路」，删掉等于推翻该决策 |
| 性质对比 | 与已清理的 `style2` 书架不同：style2 是整包不可达且无回退价值；这里的分支是规格明确要保留的退路 |

## 若将来确实要清理，正确顺序

1. 先把 `AppConfig.isEInkMode` 从 `val = true` 改成可配置的真实开关
2. 确认产品上不再需要浅色/深色主题
3. 再统一删除，并同步更新 `eink-only.md` 的决策记录

跳过第 1 步直接删分支，会得到一个「既不能切主题、代码里又没有切主题痕迹」的半成品。

## 与 TODO T-9 的一致性

T-9 记录的是 `AppConfig.bookGroupStyle` / `bookshelfLayout` 两个恒为 0 的 getter。
与本轮的 `isEInkMode` 属同类现象（常量化的配置项），建议一致：**保留而非删除**。

理由相同：它们把「这是墨水屏专属、某项能力被固定」这一设计决策写在代码里，
删除会让后续维护者失去上下文，误以为那里只是普通配置。

## 未做的事

- 未改动任何生产代码
- 未清理 `Theme.Light/Dark` 分支（按结论不该清）
- 未核实 `setTheme` 涉及的 style 资源是否存在无用项（属于资源层，与本问题不同维度，
  且 Android 资源有 shrinkResources 兜底）
