package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * 不画动画的下拉刷新容器（墨水屏专用）。
 *
 * SwipeRefreshLayout 内置的圆形箭头会持续旋转，电子纸上表现为一直闪的一圈残影。
 * AndroidX 没有公开接口关掉它，这里把内置指示器整体推到可视区之上：
 * 下拉手势与刷新回调照常工作，只是看不到旋转箭头。
 */
class StaticRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    init {
        // 起始/结束位置都推到屏幕外，指示器不再可见
        setProgressViewOffset(false, -dp(400), -dp(200))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
