package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/**
 * 无动画 ViewPager（墨水屏专用）。
 *
 * 电子纸上左右滑动的跟手动画会持续整屏刷新并留下残影，这里：
 * 1. 切换一律瞬时完成（忽略 smoothScroll）；
 * 2. 手势滑动被禁用；
 * 3. **水平滑动会被吞掉**，避免事件漏到子视图上误触（例如书架列表把横滑当成点击打开书）。
 * 垂直滑动不拦截，列表滚动照常。
 */
class NoAnimViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        super.setCurrentItem(item, false)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (dx > touchSlop && dx > dy * 1.5f) {
                    // 水平滑动：拦截下来自行消费，不切换页面也不下发给子视图
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean = true
}
