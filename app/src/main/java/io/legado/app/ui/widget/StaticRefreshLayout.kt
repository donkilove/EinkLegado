package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * 不显示刷新指示器的下拉刷新容器（墨水屏专用）。
 *
 * SwipeRefreshLayout 内置的圆形箭头会持续旋转，电子纸上表现为一直闪的一圈残影，下拉时还会
 * 在列表顶部弹出一个白色圆底图标。AndroidX 没有公开接口关掉它：
 * - setProgressViewOffset 用负偏移在部分设备上并不生效（实测 Android 17 上图标照常出现）
 * - 因此改为反射取出内部指示器 mCircleView，直接设为完全透明
 *
 * 下拉手势与刷新回调保持原样，只是用户看不到任何指示器；刷新状态由布局里的文字提示承担。
 */
class StaticRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private var circleView: View? = null

    init {
        // 双保险：先把指示器推到屏幕外
        setProgressViewOffset(false, -dp(400), -dp(200))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        hideIndicator()
    }

    private fun hideIndicator() {
        if (circleView == null) {
            circleView = runCatching {
                val field = SwipeRefreshLayout::class.java.getDeclaredField("mCircleView")
                field.isAccessible = true
                field.get(this) as? View
            }.getOrNull()
        }
        // 用 alpha 而不是 visibility：不参与布局计算，也不会触发再次测量
        circleView?.alpha = 0f
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
