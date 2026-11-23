package io.legado.app.testing

import android.app.Application

/**
 * 测试专用的 Application。
 *
 * 生产环境用的是 [io.legado.app.App]，它的 `onCreate` 会做大量初始化：
 * 崩溃上报、网络监听、通知渠道、协程调度、Cronet 预下载等。
 * 这些在 JVM 单测里既没必要，还会因为缺少 `appCtx` 等全局状态而抛异常
 * （实测表现为 `ExceptionInInitializerError at App.kt:296`，
 * 根因是 splitties 的 `AppCtx.kt:40` 抛 IllegalStateException）。
 *
 * 所以在测试里换成这个空壳，让 Robolectric 有一个可用的 Context，
 * 又不触发任何生产初始化。
 */
class TestApplication : Application()
