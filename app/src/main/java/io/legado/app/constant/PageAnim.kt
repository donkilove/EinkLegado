package io.legado.app.constant

import androidx.annotation.IntDef

@Suppress("ConstPropertyName")
object PageAnim {

    const val coverPageAnim = 0

    const val slidePageAnim = 1

    const val simulationPageAnim = 2

    /**
     * 无翻页动画（墨水屏唯一选项）。
     *
     * 保留数值 4 而不是重排：这个值会被写进书籍配置与阅读设置，
     * 重排会让老用户升级后读到自己没选过的动画。
     * 原 scrollPageAnim(3) 已移除——滚动阅读在电子纸上没有实际意义，
     * 会持续刷新、累积残影，且与「按页翻页」的整条交互链路冲突。
     */
    const val noAnim = 4

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(coverPageAnim, slidePageAnim, simulationPageAnim, noAnim)
    annotation class Anim

}