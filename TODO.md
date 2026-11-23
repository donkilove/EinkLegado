# TODO

只装**未闭环**事项。完成即时移出（规格完成则迁入 `docs/specs/done/`）。

| 编号 | 事项 | 背景 | 下一步 |
|---|---|---|---|
| T-3 | 单元测试覆盖面仍然很窄 | 已有 `PaginatorTest`(22)、`BookProgressBundleTest`(16)、`SyncFingerprintTest`(9)、`AlphanumComparatorTest`(17)、`SyncDecisionTest`(17)、`SyncPlanTest`(17)、`UserDataSyncDaoTest`(14)，共 112 个新用例；仓库 842 个主源文件里多数纯逻辑类仍无测试 | 按 `docs/specs/done/engineering-debt.md` 的模式继续补齐零 Android 依赖的类 |
| T-8 | 旧版同步指纹升级后无自动清理 | 旧版 `sync_state.json` 存 32 位 hashCode 指纹，新版为 `v2:<sha256>`。升级当次走「本地为准上传」的保守分支（已记日志），但旧值不会被主动清除，需等本次同步成功后由 `SyncState.save` 覆盖 | 观察一个版本；若日志频繁出现「检测到旧版指纹」，再考虑显式迁移 |

## 已知约定（不是待办，改动这些代码前先读）

| 编号 | 约定 | 现状与理由 |
|---|---|---|
| T-9 | `AppConfig.bookGroupStyle` / `bookshelfLayout` 两个 getter 恒返回 `0` | 2026-11-23 调查确认：两者**无任何读取方**，其 setter 也无人调用，属事实上的只读约定。`BackupConfig` 引用的是 `PreferKey` 常量，与这两个属性无关，因此删除**不会**影响旧备份恢复——此前 TODO 中的该说法有误，已纠正。**仍建议保留**：getter 固定返回 0 是「墨水屏专属，书架布局固定」这一设计决策在代码中的显式痕迹，删除会让后续改书架的人无从得知该约定。若将来真要清理，正确做法是补足注释或改写为常量，而非直接删除。详见 `docs/evidence/backlog-cleanup-2-verify.md` |

## 已完成但曾登记于此的事项

| 编号 | 事项 | 完成日期 | 处理 |
|---|---|---|---|
| T-1 | `kotlin.build.report.dir` 写死绝对路径 | 2026-11-23 | 调查发现该属性已被 Kotlin 2.3.10 **废弃**，设置它只产生警告且不生效（报告固定落 `build/reports/kotlin-build`）。已删除该行，警告消失 |
| T-2 | 构建环境前置条件未固化 | 2026-11-23 | 写入 `README.md` 新增的「Build-构建环境」一节：JDK 17、`GRADLE_USER_HOME` 同盘符，含报错原文与命令 |
| T-5 | `fingerprint` 用 32 位 `hashCode()` | 2026-11-23 | 新增 `SyncFingerprint`，改用 SHA-256 并加 `v2:` 前缀；旧值可被 `isLegacy` 识别，升级当次走「本地为准」保守分支 |
| T-6 | 书架网格视图与 style2 为死代码 | 2026-11-23 | 已清理。删除 13 个文件（6 源码 + 7 布局），详见 `docs/specs/done/bookshelf-deadcode.md`；真机验证通过 |
| T-7 | 文档门禁未在 CI 中运行 | 2026-11-23 | 用户决定不使用 CI。本地钩子已启用，并在 README 门禁一节补充「新克隆必须手动跑 install-hooks.ps1」 |
| T-10 | 书架删书后页码收敛未真机验证 | 2026-11-23 | 真机验证通过：24 本 5 页 → 在第 5 页用应用自带功能删书 → 自动跳回第 1 页并显示「第 1 / 3 页（共 14 本）」，无越界空页；末页 4 本完整可见。详见 `docs/evidence/backlog-cleanup-2-verify.md` |
| T-4 | 同步的 DAO 写入层无测试 | 2026-11-23 | 已闭环。抽出 `DbProvider` 让 `appDb` 可注入（827 处调用点零改动），给 `dbCallback` 加 `skipSeedData` 开关，引入 Robolectric 4.14.1 与内存库，新增 14 个 DAO 用例覆盖增删、去重、书源保护、计数与无副作用。全量 120 用例通过。详见 `docs/evidence/dao-testable-verify.md` |

编号取「已用最大号 + 1」，扫描来源：本文件、`docs/specs/done/README.md`。
