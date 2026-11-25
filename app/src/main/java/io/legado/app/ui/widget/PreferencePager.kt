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
    /** 所有分类容器：分页时按「分类内是否有可见项」决定分类本身显不显示 */
    private val groups = mutableListOf<androidx.preference.PreferenceGroup>()
    private var perPage = Int.MAX_VALUE
    private var page = 0
    /** 上次测量时的列表高度，用于判断是否需要重测 */
    private var measuredHeight = 0
    private var screen: PreferenceScreen? = null

    /** 测量完成后回调，便于调用方做「定位到某项」之类的后续动作 */
    var onCalibrated: (() -> Unit)? = null

    fun bind(screen: PreferenceScreen) {
        this.screen = screen
        all.clear()
        collectLeaves(screen, all)
        page = 0
        // 先全部可见，测量一页能放几项
        all.forEach { it.isVisible = true }
        groups.forEach { it.isVisible = true }
        // 高度变化后要重新测量：首次测量时 PageBar 可能还没完成布局，
        // recyclerView 比最终高度高，算出的 perPage 偏大，
        // 这一页的最后一项就会被切在屏幕外（墨水屏不能滚动，切掉就点不到）。
        recyclerView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val h = bottom - top
            if (h > 0 && h != oldBottom - oldTop && h != measuredHeight) {
                recyclerView.post { measure() }
            }
        }
        recyclerView.post { measure() }
    }

    /**
     * 收集所有**叶子**设置项，并记住它们所属的分组。
     *
     * 只取 `screen.preferenceCount` 是不够的：封面设置、其它设置等页面把项放在
     * [androidx.preference.PreferenceCategory] 里，直接子项只有几个分类，
     * 叶子项全被漏掉。结果 all.size 远小于真实行数，perPage 反而大于 size，
     * pageCount 恒为 1，翻页永远无效。
     */
    private fun collectLeaves(group: androidx.preference.PreferenceGroup, out: MutableList<Preference>) {
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            when {
                // 子页面（PreferenceScreen）是「点进去才显示」的独立页面，不是本页的行，
                // 绝不能展开——否则「我的」页会把各子页的设置项全算进来，变成十几页。
                pref is PreferenceScreen -> Unit
                // 分类（PreferenceCategory）是本页内的分组标题，要展开取其中的设置项
                pref is androidx.preference.PreferenceGroup -> {
                    groups.add(pref)
                    collectLeaves(pref, out)
                }

                else -> out.add(pref)
            }
        }
    }

    private fun measure() {
        // 可用高度要扣掉内边距：列表设了 clipToPadding=false 并加了导航栏内边距，
        // 直接用 height 会高估一截，这一页最后一行就被切在屏幕外。
        val height = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
        if (height <= 0 || all.isEmpty()) {
            // 还没有布局完成，稍后再试
            recyclerView.postDelayed({ measure() }, LAYOUT_RETRY_DELAY)
            return
        }
        // 按实际渲染出的子项高度累加，得出这一屏能放几项。
        //
        // 只数**叶子设置项**，不数分类标题：分类（PreferenceCategory）在列表里
        // 也是独立的一行，但分页是按叶子项切的。若把标题也算进 perPage，
        // 有分类的页面 perPage 会大于 all.size，pageCount 恒为 1，
        // 翻页按钮和滑动就全都失效，而内容其实已经超出一屏（墨水屏不能滚动，
        // 超出的项就再也点不到了）。封面设置、欢迎页、其它设置的二级页都属于这种。
        val adapter = recyclerView.adapter as? androidx.preference.PreferenceGroupAdapter
        var used = 0
        var fit = 0
        for (i in 0 until recyclerView.childCount) {
            val child: View = recyclerView.getChildAt(i) ?: continue
            val h = child.height
            if (h <= 0) continue
            if (used + h > height) break
            // 高度按「所有行」累加（含分类标题），否则会把标题占的位置算漏，
            // 导致这一页最后一项被切在屏幕外
            used += h
            val position = recyclerView.getChildAdapterPosition(child)
            val item = if (position >= 0) adapter?.getItem(position) else null
            // 但只把叶子设置项计入「一页几项」，标题不占分页名额
            if (item is Preference && all.contains(item)) fit++
        }
        if (fit <= 0) fit = 1
        measuredHeight = height
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
        // 分类本身：只要它下面还有可见项就显示，否则整个分类（含标题）隐藏
        groups.forEach { group ->
            var hasVisible = false
            for (i in 0 until group.preferenceCount) {
                if (group.getPreference(i).isVisible) {
                    hasVisible = true
                    break
                }
            }
            group.isVisible = hasVisible
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

    /**
     * 本分页器注册到 [pageBar] 的翻页回调。
     *
     * 单独存一份引用，是为了解绑时能判断「现在挂着的还是不是我」。
     * pageBar 属于 Activity，二级设置页用 replace 打开时新旧 Fragment 的
     * 视图 attach/detach 顺序不保证；无条件置空会把新页刚注册的回调一起抹掉，
     * 表现就是「翻页按钮点了没反应、左右滑动也没反应」。
     */
    private val pageChangeHandler: (Int) -> Unit = { delta -> moveBy(delta) }

    fun bindPageBar() {
        pageBar.onPageChange = pageChangeHandler
        // 视图销毁后把回调摘掉，避免 pageBar（常属于 Activity）持有已失效的分页器
        recyclerView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    if (pageBar.onPageChange === pageChangeHandler) {
                        pageBar.onPageChange = null
                        pageBar.hide()
                    }
                }
            }
        )
    }

    private companion object {
        const val LAYOUT_RETRY_DELAY = 80L
    }
}

/**
 * 给设置页接入分页。
 *
 * 宿主（Activity 或外层 Fragment）提供 [pageBar]，这里负责把它与列表绑定起来。
 * 页面项少到一页就能放下时，PageBar 会自行隐藏。
 */
fun androidx.preference.PreferenceFragmentCompat.setupPreferencePaging(
    pageBar: PageBar?
): PreferencePager? {
    if (pageBar == null) return null
    return PreferencePager(listView, pageBar).apply {
        bindPageBar()
        preferenceScreen?.let { bind(it) }
    }
}

/**
 * 接入分页，并把分页器交给宿主，让宿主能把左右滑动转发进来。
 *
 * [io.legado.app.ui.config.ConfigActivity] 通过它拿到当前页的分页器，
 * 在 onHorizontalSwipe 里调用 moveBy，使设置页与列表页的手势一致。
 * 离开页面时把宿主持有的引用清空，避免指向已销毁的分页器。
 */
fun androidx.preference.PreferenceFragmentCompat.setupPreferencePagingWithSwipe(
    pageBar: PageBar?,
    host: io.legado.app.ui.config.ConfigActivity?
): PreferencePager? {
    val pager = setupPreferencePaging(pageBar) ?: return null
    host?.bindPreferencePager(pager)
    listView.addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                // 只解绑自己：二级页替换时新旧视图的 attach/detach 顺序不保证，
                // 无条件清空会误伤新页刚绑好的分页器
                host?.unbindPreferencePager(pager)
            }
        }
    )
    return pager
}
