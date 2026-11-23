# dao-testable 验证证据

> 生成：2026-11-23 · 依据：[TODO.md](../../TODO.md) 的 T-4 · 状态：完成
> 原始输出：[dao-testable-check.txt](dao-testable-check.txt)

## 事项 ↔ 验证映射

| 事项 | 做法 | 结果 | 原始输出 |
|---|---|---|---|
| T-4 同步的 DAO 写入层无测试 | 引入 Robolectric + 内存库，测 `replaceLocal` 把 `SyncPlan` 落到数据库的行为 | 通过：14 个用例覆盖三类数据的增删、去重、书源保护、计数与无副作用 | 段 [5][6] |
| 阻塞项：`appDb` 是全局 lazy 单例 | 抽出 `DbProvider`，保留 `val appDb` 访问器 | 通过：827 处调用点零改动，diff 集中在 `AppDatabase.kt` | 段 [2][3] |
| 阻塞项：`onOpen` 预种脏数据 | 新增 `TestableCallback.skipSeedData` | 通过：测试可拿到干净的内存库 | 段 [3] |
| 生产路径未被打断 | 构建 APK + 模拟器冷启动 | 通过：`BUILD SUCCESSFUL`，31.51 MB；模拟器启动到书架页，`logcat -b crash` 无本应用输出 | 段 [7] |

## Red（失败先行）

本任务是「为已有实现补测试」，Red 表现为多轮**环境层失败**（不是断言失败）：

1. **`skipSeedData` 编译失败**：`Unresolved reference 'skipSeedData'`。
   原因是 Kotlin 把 `object : Callback() { var x }` 推断成匿名类型，成员外部不可见。
   → 抽出 `TestableCallback` 基类解决。

2. **运行时崩溃**：`ExceptionInInitializerError at App.kt:296`，
   根因 `IllegalStateException at AppCtx.kt:40`。
   Robolectric 默认加载生产 `App`，它的 `onCreate` 依赖 splitties 的 `appCtx`。
   → 用 `@Config(application = TestApplication::class)` 换成空壳 Application。

3. **Robolectric 运行时下载卡死**：首次运行超过 30 分钟无输出。
   定位为 `android-all-instrumented`（156 MB）从官方 Maven 下载过慢（约 30 KB/s）。
   → 手动经阿里云镜像预下载，23 秒完成。

这三轮失败都属于环境搭建，**没有一轮是测试断言失败**——因为被测代码本身是对的，
只是从未被验证过。

## Green（通过）

- 单测：`gradlew :app:testAppDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL`
  **120 个用例，0 失败，0 跳过**
  - 本次新增 14 个（`UserDataSyncDaoTest`）
  - 既有 106 个完整保留
- `UserDataSyncDaoTest` 单独耗时 4.784s
- 构建：`BUILD SUCCESSFUL in 1m 10s`，APK 31.51 MB（与改造前一致）
- 模拟器 `emulator-5554`：安装成功、冷启动到书架页、无崩溃

## 未达成 / 主动放弃

- **真机未验证**：用户真机已取走，本次用无头模拟器 `android-35` 代替。
  模拟器未配置 WebDAV 账号，因此没有触发真实的 `syncUserData` 网络路径；
  该路径的决策与差异计算已由纯函数单测覆盖。
- **89 版本迁移未测**：内存库直接建最新表结构，不走迁移。
- **`onOpen` 种数据逻辑未测**：测试刻意关掉它（`skipSeedData = true`）。
  生产默认 `false`，行为未变，但没有测试覆盖。

## 残余风险与后续

- **`DbProvider.override` 是全局可变状态**：测试若忘记 `reset()`，会影响同进程其他测试。
  当前靠 `@After` 保证，但如果将来测试并行执行（Gradle 默认不并行同模块的单测）需要重新评估。
- **`skipSeedData` 是生产代码里的测试开关**：默认 `false`，理论上生产无影响，
  但它确实是一条「测试能改变生产行为」的通道。已用 `@VisibleForTesting` 标注，
  并用 `@Volatile` 保证可见性。
- **Robolectric 运行时缓存**：`android-all-instrumented` 存在 `~/.m2`，
  换机器或清缓存后需重新下载 156 MB。README 未记录这一点，属环境约定，
  建议后续补进「Build-构建环境」一节（未做，避免本次改动扩散到不相关文档）。
- **测试只覆盖 `replaceLocal`**：`export()` 的全表读取、`syncUserData` 的分支执行
  仍未覆盖（后者部分由纯函数覆盖）。
