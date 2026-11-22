package io.legado.app.ui.widget

import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView

/**
 * 设置页分页器。
 *
 * [EinkPager] 的做法是接管 adapter 的数据流（分页后把「当前页的数据」喂给 adapter），
 * 但 [androidx.preference.PreferenceFragmentCompat] 的 adapter 由框架自己维护，
 * 没法从外部替换数据。所以这里的思路不同：
 *
 * 保留全部 [Preference]，只按页码控制每一项的可见性。
 * 不可见的项不参与布局，列表自然就只有一页的高度，再用 [PageBar] 翻页。
 *
 * 电子纸上一屏能放的设置项数量取决于项高（有没有 summary），
 * 因此先让全部项可见，等布局完成后数一数实际能放下几项，再据此分页。
 */
class PreferencePager(
    private val recyclerView: RecyclerView,
    private val pageBar: PageBar,
    private val itemName: String = "项"
) {

    private val all = mutableListOf<Preference>()
    private var perPage = Int.MAX_VALUE
    private var page = 0
    private var screen: PreferenceScreen? = null

    /** 测量完成后回调，便于调用方做「定位到某项」之类的后续动作 */
    var onCalibrated: (() -> Unit)? = null

    fun bind(screen: PreferenceScreen) {
        this.screen = screen
        all.clear()
        for (i in 0 until screen.preferenceCount) {
            all.add(screen.getPreference(i))
        }
        page = 0
        // 先全部可见，测量一页能放几项
        all.forEach { it.isVisible = true }
        recyclerView.post { measure() }
    }

    private fun measure() {
        val height = recyclerView.height
        if (height <= 0 || all.isEmpty()) {
            // 还没有布局完成，稍后再试
            recyclerView.postDelayed({ measure() }, LAYOUT_RETRY_DELAY)
            return
        }
        // 按实际渲染出的子项高度累加，得出这一屏能放几项
        var used = 0
        var fit = 0
        for (i in 0 until recyclerView.childCount) {
            val child: View = recyclerView.getChildAt(i) ?: continue
            val h = child.height
            if (h <= 0) continue
            if (used + h > height) break
            used += h
            fit++
        }
        if (fit <= 0) fit = 1
        perPage = fit
        applyPage()
        onCalibrated?.invoke()
    }

    /** 重新测量（例如子页切换、字号变化后） */
    fun recalibrate() {
        page = 0
        all.forEach { it.isVisible = true }
        recyclerView.post { measure() }
    }

    private fun applyPage() {
        if (perPage == Int.MAX_VALUE) return
        val start = page * perPage
        val end = start + perPage
        all.forEachIndexed { index, preference ->
            preference.isVisible = index in start until end
        }
        pageBar.update(page, pageCount, all.size, itemName)
        // 翻页后回到列表顶部
        recyclerView.scrollToPosition(0)
    }

    private val pageCount: Int
        get() {
            if (all.isEmpty()) return 1
            if (perPage == Int.MAX_VALUE) return 1
            return (all.size + perPage - 1) / perPage
        }

    fun moveBy(delta: Int) {
        val target = (page + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (target == page) return
        page = target
        applyPage()
    }

    fun bindPageBar() {
        pageBar.onPageChange = { delta -> moveBy(delta) }
    }

    private companion object {
        const val LAYOUT_RETRY_DELAY = 80L
    }
}
