package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/**
 * 禁用滑动的 ViewPager（墨水屏专用）。
 *
 * 电子纸上左右滑动的跟手位移会持续整屏刷新并留下残影，且嵌套时容易误切到相邻大页，
 * 因此不再支持手势滑动：页面切换只由 Tab / 底部导航触发，且瞬时完成（不做平滑滚动）。
 *
 * 水平手势会被主动吞掉，避免漏到子视图上被误判为点击（实测会导致书架误打开书籍）；
 * 垂直手势不拦截，列表上下滚动照常。
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
