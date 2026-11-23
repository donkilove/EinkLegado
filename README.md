<div align="center">
<img width="125" height="125" src="https://gitee.com/lyc486/legado/raw/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="legado"/>
<br>
阅读Sigma
<br>
阅读Sigma继承自<a href="https://github.com/gedoor/legado" target="_blank">Legado</a>，延续开源阅读项目，在其基础上新增更多功能。
</div>

## 版本说明
- 测试版(beta)：包名与Legado原版相同，可覆盖更新，版本更新频繁
- 正式版(plus)：新的共存包名，安装后是一个新软件，不会覆盖Legado原版，每到一个稳定阶段进行一次更新
#### 找不到下载地址可以去这里 [下载软件](https://gitee.com/lyc486/legado/releases)

[![](https://img.shields.io/badge/-Contents:-696969.svg)](#contents) [![](https://img.shields.io/badge/-Function-F5F5F5.svg)](#Function-主要功能-) [![](https://img.shields.io/badge/-Community-F5F5F5.svg)](#Community-交流社区-) [![](https://img.shields.io/badge/-API-F5F5F5.svg)](#API-) [![](https://img.shields.io/badge/-Other-F5F5F5.svg)](#Other-其他-) [![](https://img.shields.io/badge/-Grateful-F5F5F5.svg)](#Grateful-感谢-) [![](https://img.shields.io/badge/-Interface-F5F5F5.svg)](#Interface-界面-)

>新用户？
>
>软件不提供内容，需要您自己手动添加，例如导入书源等。  
>看看 [帮助文档](https://www.yuque.com/legado/wiki)，也许里面就有你要的答案。

# Function-主要功能 [![](https://img.shields.io/badge/-Function-F5F5F5.svg)](#Function-主要功能-)

<details><summary>原版</summary>
1.自定义书源，自己设置规则，抓取网页数据，规则简单易懂，软件内有规则说明。<br>
2.列表书架（本分支已移除网格书架与文件夹分组）。<br>
3.书源规则支持搜索及发现，所有找书看书功能全部自定义，找书更方便。<br>
4.订阅内容,可以订阅想看的任何内容,看你想看<br>
5.支持替换净化，去除广告替换内容很方便。<br>
6.支持本地TXT、EPUB阅读，手动浏览，智能扫描。<br>
7.支持高度自定义阅读界面，切换字体、颜色、背景、行距、段距、加粗、简繁转换等（本分支配色固定为纯黑白）。<br>
8.支持多种翻页模式（本分支收敛为「无动画」与「滚动」两种）。<br>
9.软件开源，持续优化，无广告。
</details>

<details><summary>本分支：墨水屏专属改造</summary>
面向电子纸设备，把「持续刷新」和「动画」这两类在电子纸上只有负面影响的东西逐步去掉：

1.长列表改为翻页，每页条数按实际项高自动决定，页内无需滚动。<br>
2.设置页改为二级分页，首屏零滚动。<br>
3.关闭水波纹、过渡动画、跟手滑动、循环旋转图标等视觉特效。<br>
4.主题固定为纯黑白 EInk，不提供浅色/深色切换。<br>
5.书架固定为列表视图 + 标签分组，主界面与书架分类靠点按切换而非滑动。<br>
6.移除视频、弹幕、本地音频播放与 Firebase 统计（书源 JS 中相关函数保留签名，调用后仅提示）。<br>
7.保留 RSS 订阅、发现页、字典与段评、朗读、Web 端编辑——这些在电纸书看小说的场景下仍有实际用途。<br>
详见 [docs/specs/done/](docs/specs/done/README.md) 的规格与决策记录。
</details>

<details><summary>延续（上游 Legado 的扩展，本分支已部分移除）</summary>
1.带歌词的音频播放功能 —— **本分支已移除**。<br>
2.带弹幕的视频播放功能 —— **本分支已移除**。<br>
3.更方便的书源编辑。<br>
4.更丰富的书源功能实现。<br>
5.更完善的订阅源功能。<br>
</details>

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Community-交流社区 [![](https://img.shields.io/badge/-Community-F5F5F5.svg)](#Community-交流社区-)

#### Telegram
[![Telegram-channel](https://img.shields.io/badge/Σ_Telegram-%E9%A2%91%E9%81%93-blue)](https://t.me/readsigma)

#### WeChat
[![WeChat-channel](https://img.shields.io/badge/Σ_%e5%be%ae%e4%bf%a1-%e5%85%ac%e4%bc%97%e5%8f%b7-green)](https://mp.weixin.qq.com/mp/appmsgalbum?action=getalbum&album_id=4345046964963590147)  
<img src="https://open.weixin.qq.com/qr/code?username=legado_plus" width="100">

#### Discord
[![Discord](https://img.shields.io/discord/560731361414086666?color=%235865f2&label=Discord)](https://discord.gg/VtUfRyzRXn)

#### Other
https://www.yuque.com/legado/wiki/community

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# API [![](https://img.shields.io/badge/-API-F5F5F5.svg)](#API-)
* 阅读3.0 提供了2种方式的API：`Web方式`和`Content Provider方式`。您可以在[这里](api.md)根据需要自行调用。 
* 可通过url唤起阅读进行一键导入,url格式: legado://import/{path}?src={url}
* path类型: bookSource,rssSource,replaceRule,textTocRule,httpTTS,theme,readConfig,dictRule,[addToBookshelf](/app/src/main/java/io/legado/app/ui/association/AddToBookshelfDialog.kt)
* path类型解释: 书源,订阅源,替换规则,本地txt小说目录规则,在线朗读引擎,主题,阅读排版,添加到书架

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Other-其他 [![](https://img.shields.io/badge/-Other-F5F5F5.svg)](#Other-其他-)  

<details><summary>免责声明</summary>
阅读依赖系统webview提供网页访问功能，通过用户自定义的第三方网页书源返回阅读内容，阅读对其返回内容概不负责，亦不承担任何法律责任。任何第三方网页书源均系他人制作或提供，非软件作者，阅读对其合法性概不负责，亦不承担任何法律责任。第三方网页书源提供的试读，不代表阅读赞成其内容或立场。您应该对用搜索到的结果自行承担风险。<br>
任何单位或个人认为第三方网页书源内容可能涉嫌侵犯其信息网络传播权，应该及时向阅读提出书权力通知，并提供身份证明、权属证明及详细侵权情况证明。阅读在收到上述法律文件后，将会依法尽快屏蔽相关内容。<br>
</details>

##### 阅读3.0
* [书源规则](https://mgz0227.github.io/The-tutorial-of-Legado/)
* [更新日志](/app/src/main/assets/updateLog.md)
* [帮助文档](/app/src/main/assets/web/help/md/appHelp.md)

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Grateful-感谢 [![](https://img.shields.io/badge/-Grateful-F5F5F5.svg)](#Grateful-感谢-)
> * org.jsoup:jsoup
> * cn.wanghaomiao:JsoupXpath
> * com.jayway.jsonpath:json-path
> * com.github.gedoor:rhino-android
> * com.squareup.okhttp3:okhttp
> * com.github.bumptech.glide:glide
> * org.nanohttpd:nanohttpd
> * org.nanohttpd:nanohttpd-websocket
> * cn.bingoogolapple:bga-qrcode-zxing
> * com.jaredrummler:colorpicker
> * org.apache.commons:commons-text
> * io.noties.markwon:core
> * io.noties.markwon:image-glide
> * com.hankcs:hanlp
> * com.positiondev.epublib:epublib-core
> * com.github.Moriafly:LyricViewX
> * io.github.rosemoe:editor
<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Interface-界面 [![](https://img.shields.io/badge/-Interface-F5F5F5.svg)](#Interface-界面-)
<img src="https://channel.qpic.cn/psc?/channel/NaDwC23LjvXqrn3RH.9z8bdVVU8X7gejuELokmv2DGfJwQpP2UFcwHqqFK4nzfgNUTL6Wij4QqsJySkoPfQuCB1l5MstXCXWjmH5Mm6PGkU!/b=&bo=UQRICVEESAkWADA!&ek=1&tl=1" width="270">  <img src="https://channel.qpic.cn/psc?/channel/NaDwC23LjvXqrn3RH.9z8epqbe*Bzro*ybEEVmlLpuD9E14zFMjtRx2JeZJdVzNYeSZqCm5Kv0MruAumEe0tJ77n*agIEzJIaSN9VHAen2I!/b=&bo=UQRICVEESAkWADA!&ek=1&tl=1" width="270">  
<img src="https://channel.qpic.cn/psc?/channel/NaDwC23LjvXqrn3RH.9z8SQggDiubhwWyktI5v48VluvK1vwdY2OV9RauO04TcXb4425kVcBiAz2DhpfnrDZKnSpwp8SLnDtKOxtLm1yP9I!/b=&bo=UQRICVEESAkWADA!&ek=1&tl=1" width="270">  <img src="https://channel.qpic.cn/psc?/channel/NaDwC23LjvXqrn3RH.9z8Tx.7oqZyCeAdzSVQa6ZgtXKE*EaUCdEJ3zm2ynIrgem10TqY8aKvSbG3v5FNNHtwSFyN5QfLW*Zd14gUetfsC8!/b=&bo=UgRKCVIESgkWADA!&ek=1&tl=1" width="270">

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Build-构建环境 [![](https://img.shields.io/badge/-Build-F5F5F5.svg)](#Build-构建环境-)

本地构建本仓库有两个前置条件，缺任意一个都会失败，且报错信息不会直指根因。

**1. JDK 17**

`app/build.gradle` 与 `modules/*/build.gradle` 均声明 `jvmToolchain(17)`，只装了 JDK 21/11 的机器会失败：

```
Cannot find a Java installation ... matching: {languageVersion=17}
```

安装（Windows）：

```powershell
winget install --id Microsoft.OpenJDK.17
```

指向方式二选一，**推荐环境变量**，不要写进仓库文件：

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot"
```

**2. `GRADLE_USER_HOME` 必须与项目同盘符**

Glide 的 KSP 处理器不支持跨盘符路径，Gradle 缓存在 C 盘而项目在 D 盘时会在 `kspAppDebugKotlin` 崩溃：

```
e: [ksp] java.lang.IllegalArgumentException: this and base files have different roots:
   C:\Users\...\.gradle\caches\...\glide-svg-4.0.2-api.jar!\... and D:\EinkLegado\app.
```

解决：把缓存迁到与项目相同的盘符。

```powershell
$env:GRADLE_USER_HOME = "D:\gradle-home"
```

首次会重新下载 Gradle 发行版与全部依赖，之后恢复正常。这是环境层面的规避，非项目缺陷；升级 Glide/KSP 后可能不再需要。

**日常命令**

```powershell
$env:JAVA_HOME       = "C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot"
$env:GRADLE_USER_HOME = "D:\gradle-home"
.\gradlew.bat :app:testAppDebugUnitTest   # 单元测试，约 1 分钟
.\gradlew.bat assembleAppDebug            # 构建 debug 包
```

**Kotlin 构建报告**固定输出到 `build/reports/kotlin-build/`。属性 `kotlin.build.report.dir` 已被 Kotlin 2.3.10 废弃，设置它只会产生「Deprecated Gradle Property」警告且不生效，不要加回 `gradle.properties`。

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Docs-文档约定 [![](https://img.shields.io/badge/-Docs-F5F5F5.svg)](#Docs-文档约定-)

本仓库只允许四类文档，其余 `.md` 只能作为上游既有文档或随产品发布的工件存在。新文档先归类，四类都装不下就不新增，改写进既有文档。

| 类型 | 落点 | 索引 | 说明 |
|---|---|---|---|
| TODO | `TODO.md` | — | 只装未闭环事项，完成即时移出 |
| SPEC | `docs/specs/` | [docs/specs/README.md](docs/specs/README.md) | 进行中的规格 |
| 证据 | `docs/evidence/` | [docs/evidence/README.md](docs/evidence/README.md) | 一切验证输出（构建日志、核查输出、截图、基线） |
| 已完成 SPEC | `docs/specs/done/` | [docs/specs/done/README.md](docs/specs/done/README.md) | 规格与决策记录归档 |

**例外**

以下为上游既有文档与随产品发布、被工具消费的工件，不属于上述四类：

- `README.md`
- `CHANGELOG.md`
- `api.md`
- `app/src/main/assets/**/*.md`
- `app/src/main/java/**/*.md`
- `modules/**/*.md`

**编号**

新增条目编号取「已用最大号 + 1」，扫描来源：`TODO.md`、`docs/specs/done/README.md`。

**门禁**

- 校验入口：`tools/check-docs.ps1`，校验三条不变式：归类完备、索引与实体双向一致、编号唯一。
- **克隆后第一步启用提交钩子**：`powershell -File tools/install-hooks.ps1`（幂等，可 `-Check` 只报告、可 `-Uninstall`）。`core.hooksPath` 属于仓库本地配置，不随仓库分发，**新克隆默认不生效**，必须手动跑一次。
- 逃生门：`git commit --no-verify` 可显式跳过，但会留痕于 `git log`。
