package io.legado.app.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.ColorUtils

/**
 * 翻页栏：电子纸上用翻页代替滚动。
 *
 * 三个元素：上一页 / 页码信息 / 下一页。到达首尾时对应按钮置灰不可点。
 *
 * 颜色跟随主题（日间白底黑字、夜间黑底白字）。原先这里写死 Color.WHITE / Color.BLACK，
 * 夜间主题下就变成深色列表里嵌一条白色横条。
 */
@SuppressLint("ViewConstructor")
class PageBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val tvPrev: TextView
    private val tvInfo: TextView
    private val tvNext: TextView

    /** 参数为 -1（上一页）或 +1（下一页） */
    var onPageChange: ((Int) -> Unit)? = null

    /**
     * 是否显示左右翻页按钮。目录页用滑动翻页，可只保留中间的页码显示。
     */
    var showButtons: Boolean = true
        set(value) {
            field = value
            tvPrev.isVisible = value
            tvNext.isVisible = value
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(4))

        val bgColor = context.bottomBackground
        setBackgroundColor(bgColor)

        // 正文色按背景明暗取，保证任何一套主题下都有足够对比度
        val textColor = context.primaryTextColor
        val disabledColor = ColorUtils.withAlpha(textColor, 0.4f)

        tvPrev = createButton("上一页", textColor, disabledColor)
        tvInfo = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(textColor)
        }
        tvNext = createButton("下一页", textColor, disabledColor)

        // 按钮宽度随文字自适应，避免窄屏下挤压中间的页码信息
        addView(
            tvPrev,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            tvInfo,
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        )
        addView(
            tvNext,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        )

        tvPrev.setOnClickListener { if (tvPrev.isEnabled) onPageChange?.invoke(-1) }
        tvNext.setOnClickListener { if (tvNext.isEnabled) onPageChange?.invoke(1) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createButton(text: String, color: Int, disabledColor: Int): TextView =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(color)
            setTypeface(typeface, Typeface.NORMAL)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            isClickable = true
            isFocusable = true
            // 置灰色随主题走，避免深色主题下用固定的浅灰
            tag = disabledColor
        }

    /**
     * @param page 当前页，从 0 开始
     * @param pageCount 总页数（至少 1）
     * @param total 总条目数
     * @param itemName 条目单位，如「条」
     */
    fun update(page: Int, pageCount: Int, total: Int, itemName: String = "条") {
        this.visibility = if (pageCount <= 1) View.GONE else View.VISIBLE
        if (pageCount <= 1) return
        tvInfo.text = "第 ${page + 1} / $pageCount 页（共 $total $itemName）"
        setButtonEnabled(tvPrev, page > 0)
        setButtonEnabled(tvNext, page < pageCount - 1)
    }

    private fun setButtonEnabled(button: TextView, enabled: Boolean) {
        button.isEnabled = enabled
        val normalColor = context.primaryTextColor
        val disabledColor = button.tag as? Int ?: ColorUtils.withAlpha(normalColor, 0.4f)
        button.setTextColor(if (enabled) normalColor else disabledColor)
    }

    /** 用于隐藏整条翻页栏（例如空列表） */
    fun hide() {
        isVisible = false
    }
}
