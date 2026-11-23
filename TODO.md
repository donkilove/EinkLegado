# TODO

只装**未闭环**事项。完成即时移出（规格完成则迁入 `docs/specs/done/`）。

| 编号 | 事项 | 背景 | 下一步 |
|---|---|---|---|
| T-1 | `gradle.properties` 中 `kotlin.build.report.dir=D:/EinkLegado/build/reports/kotlin` 写死绝对路径 | 换机器或换目录后该配置失效，Kotlin 构建报告会写不出去，影响增量编译诊断 | 改为相对路径或由 Gradle 属性注入 |
| T-2 | 构建环境两个前置条件未固化进仓库 | 本机实测必须同时满足：①JDK 17（`jvmToolchain(17)`，本机原有 21/11 均不满足）②`GRADLE_USER_HOME` 与项目同盘符（否则 Glide 的 KSP 处理器崩溃）。两者靠环境变量临时设置，换机器必重现 | 用 `gradle.property` 或文档固化；或升级 Glide/KSP 版本消除跨盘符缺陷 |
| T-3 | 单元测试覆盖面仍然很窄 | 已有 `PaginatorTest`（22 个）、`BookProgressBundleTest`（16 个），但仓库 845 个主源文件里绝大多数纯逻辑类仍无测试 | 按 `docs/specs/done/engineering-debt.md` 的模式逐步补齐其余可测的纯逻辑类 |
| T-4 | `UserDataSync` 无测试且依赖 Room | 合并语义为「最后修改者胜」，逻辑复杂（书源空列表保护、指纹比对），但 `appDb` 需 Robolectric 或改生产代码才能测。同理 `PendingProgressQueue`、`SyncState` 依赖 `appCtx.filesDir` | 评估引入 Robolectric，或把纯决策逻辑抽成不依赖 `appDb` 的函数后再测 |
| T-5 | `UserDataSync.fingerprint` 用 `String.hashCode()` 作数据指纹 | 32 位有符号哈希，理论上存在碰撞，碰撞会导致「本地未改动」被误判，进而用云端覆盖本地 | 评估换成 SHA-256 等更宽的摘要；改动前需确认对 `sync_state.json` 的兼容处理 |
| T-6 | 网格书架分页未真机验证 | `bookshelfLayout >= 2` 的路径按代码逻辑处理（按列数取整行），但阶段 5 只测了列表模式 | 在真机上切到网格模式核对每页是否整行、末行是否溢出 |
| T-7 | 文档门禁未在 CI 中运行 | `tools/check-docs.ps1` 目前只在本地提交钩子里跑，`.github/workflows/` 中没有对应步骤。**本地钩子已于 2026-11-23 启用**（`core.hooksPath = .githooks`），但钩子不进版本库，新克隆的仓库默认不生效 | 在 CI 增加门禁步骤；或在 README 中把「克隆后先跑 install-hooks.ps1」写成第一步 |

## 已完成但曾登记于此的事项

无。编号取「已用最大号 + 1」，扫描来源：本文件、`docs/specs/done/README.md`。
