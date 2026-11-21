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
 *
 * 若调用方设置了 [onHorizontalSwipe]，则水平滑动不再被默默丢弃，
 * 而是在手势结束时回调方向（-1 向左滑，1 向右滑），供列表翻页使用。
 */
class NoAnimViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    /** 水平滑动回调：-1 左滑，1 右滑。为 null 时保持「吞掉手势」的原行为 */
    var onHorizontalSwipe: ((direction: Int) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var swiping = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** 识别为一次翻页手势所需的最小水平位移，取屏宽的 15% 左右 */
    private val swipeThreshold: Int by lazy {
        (resources.displayMetrics.widthPixels * 0.15f).toInt()
    }

    override fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        super.setCurrentItem(item, false)
    }

    /**
     * TabLayout.setupWithViewPager 内部调用的是这个单参数版本，
     * 它不会经过上面的双参数重载，而是自行以 smoothScroll=true 滚动，
     * 导致点分组标签时整页横向滑动。这里一并覆盖为瞬时切换。
     */
    override fun setCurrentItem(item: Int) {
        super.setCurrentItem(item, false)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                lastY = ev.y
                swiping = false
            }

            MotionEvent.ACTION_MOVE -> {
                lastX = ev.x
                lastY = ev.y
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (dx > touchSlop && dx > dy * 1.5f) {
                    // 水平滑动：拦截下来自行消费，不切换页面也不下发给子视图
                    swiping = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                lastX = ev.x
                lastY = ev.y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swiping) {
                    val dx = lastX - downX
                    val dy = lastY - downY
                    if (abs(dx) >= swipeThreshold && abs(dx) > abs(dy) * 1.5f) {
                        onHorizontalSwipe?.invoke(if (dx > 0) -1 else 1)
                    }
                }
                swiping = false
            }
        }
        return true
    }
}
