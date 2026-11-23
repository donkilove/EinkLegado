# p2-unit-tests 验证证据

> 生成：2026-11-23 · 依据：[TODO.md](../../TODO.md) 的 T-3 · 状态：本轮完成
> 原始输出：[p2-unit-tests-check.txt](p2-unit-tests-check.txt)

## 事项 ↔ 验证映射

| 事项 | 做法 | 结果 | 原始输出 |
|---|---|---|---|
| T-3 测试覆盖窄 | 先甄别哪些类真正值得测，再补测试 | 部分完成：新增 23 个用例覆盖 `RuleAnalyzer.splitRule`；`ContentHelp.reSegment` 未测（见下） | 段 [1][3][6] |
| 甄别过程 | 扫描零 Android import 且行数 > 50 的类，再查调用点 | 通过：排除了 `innerRule`（零调用点），锁定 `splitRule`（5 处调用） | 段 [1][2] |
| 顺带发现缺陷 | — | **发现并修复 1 个真实缺陷**：`RuleAnalyzer.trim()` 对空串/纯空白越界崩溃 | 段 [4] |

## Red（失败先行）

本任务是「为已实现逻辑补测试」，Red 有两层：

**第一层：选错目标后的批量失败**

为 `RuleAnalyzer.innerRule` 写的 18 个用例中有 5 个失败：4 个 `StringIndexOutOfBoundsException`、
1 个 `ComparisonFailure`。

排查后发现关键事实：**`innerRule` 在全仓没有任何调用点**，是未使用的 API。
为它写测试即使全绿也不保护任何真实路径。→ 删除该测试，改测 `splitRule`。

**第二层：真实缺陷暴露（这是有价值的 Red）**

改测 `splitRule` 后，3 个边界用例失败：

```
空串输入       → java.lang.StringIndexOutOfBoundsException: String index out of range: 0
纯空白输入     → java.lang.StringIndexOutOfBoundsException: String index out of range: 3
单个分隔符 "@" → java.lang.StringIndexOutOfBoundsException: String index out of range: 1
```

定位到 `RuleAnalyzer.trim()` 两处越界：空串时直接取 `queue[pos]`；整串都是分隔符/空白时
`while` 循环会把 `pos` 推到 `length` 之外再取。

**这是真实缺陷，不是测试问题**：任何把空串或纯空白传给 `RuleAnalyzer` 的调用方都会崩溃，
而书源规则里空规则并不罕见（用户没填某一级、模板渲染出空值）。

## Green（通过）

- 缺陷已修复（`trim()` 加两处长度检查），修复后边界用例通过
- 修复过程中出现一次**断言写错**（以为是空列表，实际返回 `[""]`），用临时探针跑了 9 组
  输入确认实际行为后修正断言——代码自洽，不是缺陷
- 全量测试：`gradlew :app:testAppDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 1m 9s`
  **143 个用例，0 失败，0 跳过**
  - 本次新增 23 个（`RuleAnalyzerSplitRuleTest`）
  - 既有 120 个完整保留
- 探针测试用完即删，未留在仓库

## 变更记录（缺陷与行为变更）

**缺陷 1（已修，行为变更）**：`RuleAnalyzer.trim()` 空输入越界崩溃

- 现象：空串、纯空白、纯分隔符输入抛 `StringIndexOutOfBoundsException`
- 原因：`if (queue[pos] == ...)` 未检查 `pos` 是否已等于 `length`；
       `while (queue[pos] == ...) pos++` 会越过后继续取值
- 修正：进入前 `if (pos >= queue.length) return`；`while` 条件加 `pos < queue.length`
- 影响面：原行为是崩溃，新行为是安全返回空结果。调用方若依赖「空输入必崩溃」需要复查
  （实际不存在这样的调用方）
- 授权依据：用户在本轮任务开头明确允许「顺带修 bug」

## 未达成 / 主动放弃

- **`ContentHelp.reSegment` 未测**（630 行正文重排）：它是启发式算法，没有明确的对错标准。
  写测试容易变成「把现有实现原样固化成期望值」，抓不到真问题，反而会在将来改进算法时
  变成无意义的阻力。若要测，需要先定义「什么样的分段算正确」，这需要用户参与。
- **`innerRule` 未测**：零调用点，是死 API。
- **其它 private 方法未测**：`consumeToAny`、`chompRuleBalanced`、`findToAny` 等为 private，
  只能通过公开方法间接覆盖，本次未穷举。

## 残余风险与后续

- **`trim()` 的修复是行为变更**：虽然原行为是崩溃、新行为更安全，但严格说不是「纯重构」。
  提交信息里已标注，回滚只需还原那两行。
- **括号保护未穷举**：`splitRule` 对 `[]` 与 `()` 内的分隔符不切分，测试覆盖了常见形态与
  一层嵌套，但真实书源规则里可能出现多层混合嵌套，未验证。
- **测试对 `[""]` 这类边界行为的固化**：钉住既有行为是对的，但也意味着将来若有人想改成
  「空输入返回空列表」，会先被测试拦住——这是预期效果，需要时改测试即可。
- **T-3 仍是长期项**：仓库 842 个主源文件，本次只覆盖了 1 个类。`ReadBook.kt`（1083 行）
  等大文件里混着大量逻辑，但依赖链复杂，需要逐个甄别。
