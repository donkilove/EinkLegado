# 验证证据

一切验证输出（构建日志、核查脚本输出、截图、基线数据）都放本目录，文件名以对应规格 slug 前缀命名，并在此登记。

| 证据文件 | 对应规格 | 内容 |
|---|---|---|
| [eink-only-check.txt](eink-only-check.txt) | eink-only | 构建门禁、残留引用核查与文档门禁的原始输出 |
| [eink-only.verify.md](eink-only.verify.md) | eink-only | 验收标准与验证结果对照（含阶段 4–7） |
| [engineering-debt-check.txt](engineering-debt-check.txt) | engineering-debt | 环境补齐、Red/Green 测试输出与文档门禁的原始输出 |
| [engineering-debt.verify.md](engineering-debt.verify.md) | engineering-debt | 验收标准与验证结果对照 |
| [backlog-cleanup-check.txt](backlog-cleanup-check.txt) | backlog-cleanup（依据 TODO.md T-1~T-7） | 各遗留项的调查、Red/Green 测试与门禁原始输出 |
| [backlog-cleanup-verify.md](backlog-cleanup-verify.md) | backlog-cleanup（依据 TODO.md T-1~T-7） | 事项与验证结果对照、未达成项与残余风险 |
| [bookshelf-deadcode-check.txt](bookshelf-deadcode-check.txt) | bookshelf-deadcode | 不可达路径调查、删除清单、编译与真机验证原始输出 |
| [bookshelf-deadcode-verify.md](bookshelf-deadcode-verify.md) | bookshelf-deadcode | 验收标准与验证结果对照 |
| [t6-shelf-page3.png](t6-shelf-page3.png) | bookshelf-deadcode | 真机第 3 页（11–12）与页码 |
| [t6-shelf-page2back.png](t6-shelf-page2back.png) | bookshelf-deadcode | 真机右滑返回第 2 页 |
| [t6-shelf-reuse.png](t6-shelf-reuse.png) | bookshelf-deadcode | 真机切回书架仍停在第 2 页（Fragment 复用） |
| [t6-my-page.png](t6-my-page.png) | bookshelf-deadcode | 真机「我的」页 5 项正常 |
| [backlog-cleanup-2-check.txt](backlog-cleanup-2-check.txt) | backlog-cleanup-2（依据 TODO.md T-9、T-10） | 删书页码收敛真机验证与 AppConfig getter 调查原始输出 |
| [backlog-cleanup-2-verify.md](backlog-cleanup-2-verify.md) | backlog-cleanup-2（依据 TODO.md T-9、T-10） | 事项与验证结果对照、T-9 不删的理由 |
| [t10-1-before-24books.png](t10-1-before-24books.png) | backlog-cleanup-2 | 删书前：24 本 5 页 |
| [t10-2-lastpage5of5.png](t10-2-lastpage5of5.png) | backlog-cleanup-2 | 删书前：第 5 页（末页）4 本 |
| [t10-5-delconfirm.png](t10-5-delconfirm.png) | backlog-cleanup-2 | 删书确认对话框 |
| [t10-3-afterdelete-converged.png](t10-3-afterdelete-converged.png) | backlog-cleanup-2 | 删书后：自动回到第 1 页，显示共 14 本 3 页 |
| [t10-4-lastpage-ok.png](t10-4-lastpage-ok.png) | backlog-cleanup-2 | 删书后末页 4 本完整可见 |
| [sync-testable-check.txt](sync-testable-check.txt) | sync-testable | 实体探针、Red/Green 测试、真机三场景同步与数据核对原始输出 |
| [sync-testable-verify.md](sync-testable-verify.md) | sync-testable | 验收标准与验证结果对照、缺陷记录 |
| [dao-testable-check.txt](dao-testable-check.txt) | dao-testable（依据 TODO.md T-4） | 模拟器搭建、appDb 可注入化、Robolectric 环境障碍与 120 用例输出 |
| [dao-testable-verify.md](dao-testable-verify.md) | dao-testable（依据 TODO.md T-4） | 事项与验证结果对照、未覆盖项与残余风险 |
| [p1-emu1.png](p1-emu1.png) | dao-testable | 模拟器冷启动：隐私协议页 |
| [p1-emu2.png](p1-emu2.png) | dao-testable | 模拟器冷启动：书架页（无崩溃） |
| [p2-unit-tests-check.txt](p2-unit-tests-check.txt) | p2-unit-tests（依据 TODO.md T-3） | 候选甄别、选错目标的经过、发现的越界缺陷与修复、143 用例输出 |
| [p2-unit-tests-verify.md](p2-unit-tests-verify.md) | p2-unit-tests（依据 TODO.md T-3） | 事项与验证结果对照、缺陷与行为变更记录 |
| [p3-deadcode-check.txt](p3-deadcode-check.txt) | p3-deadcode（TODO.md T-11） | isEInkMode 78 处引用的分类、isNightTheme 追踪、可清理范围 |
| [p3-deadcode-verify.md](p3-deadcode-verify.md) | p3-deadcode（TODO.md T-11） | 调查结论：不清理，及将来若清理的正确顺序 |
| [search-page-check.txt](search-page-check.txt) | search-page | 实现改动、两个隐患的修正、编译失败与模拟器验证原始输出 |
| [search-page-verify.md](search-page-verify.md) | search-page | 需求与 AC 对照、隐患说明、未覆盖项 |
| [search-page-1-bottom-entry.png](search-page-1-bottom-entry.png) | search-page | 底栏出现搜索入口，书架右上角为备份图标 |
| [search-page-2-opened.png](search-page-2-opened.png) | search-page | 点击底栏搜索后打开 SearchActivity |
| [search-page-3-back-keeps-tab.png](search-page-3-back-keeps-tab.png) | search-page | 返回后底栏仍停在书架（选中态未被劫持） |
| [search-page-4-disabled.png](search-page-4-disabled.png) | search-page | 关闭开关后底栏无搜索项，右上角仍为备份 |
| [search-page-5-my-ok.png](search-page-5-my-ok.png) | search-page | 关闭态下点「我的」正确进入，映射无误 |
| [search-page-6-real-device.png](search-page-6-real-device.png) | search-page | 底栏三图标（书架/搜索/我的），搜索入口正常显示 |
| [search-page-7-real-search.png](search-page-7-real-search.png) | search-page | 点底栏搜索进入搜索页，搜索历史与键盘正常 |
| [search-page-8-fixed-off.png](search-page-8-fixed-off.png) | search-page | 关闭搜索入口开关后，底栏只剩书架与我的 |
| [search-page-9-fixed-on.png](search-page-9-fixed-on.png) | search-page | 重新开启开关后，底栏恢复三个图标 |
