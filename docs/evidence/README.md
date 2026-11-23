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
