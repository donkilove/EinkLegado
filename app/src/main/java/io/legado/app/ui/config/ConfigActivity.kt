package io.legado.app.ui.config

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityConfigBinding
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.ui.widget.PageBar

class ConfigActivity : VMBaseActivity<ActivityConfigBinding, ConfigViewModel>() {

    override val binding by viewBinding(ActivityConfigBinding::inflate)
    override val viewModel by viewModels<ConfigViewModel>()

    /** 设置页分页条：由各设置 Fragment 在 onViewCreated 里接入 */
    val pageBar: PageBar get() = binding.pageBar

    /**
     * 当前设置页的分页器。
     *
     * 设置页用的是 [io.legado.app.ui.widget.PreferencePager]——它不能像列表页那样
     * 接管 adapter 数据流（PreferenceFragmentCompat 的 adapter 由框架维护），
     * 只能按页码切换 isVisible。该分页器此前只接了 PageBar 按钮，
     * 没有左右滑动；这里把 Activity 的滑动事件转发过去，与列表页手势对齐。
     */
    private var preferencePager: io.legado.app.ui.widget.PreferencePager? = null

    fun bindPreferencePager(pager: io.legado.app.ui.widget.PreferencePager?) {
        preferencePager = pager
    }

    /**
     * 解绑，但只在「当前绑定的就是它」时才清空。
     *
     * 二级设置页用 replace + addToBackStack 打开，旧 Fragment 的视图销毁
     * 与新 Fragment 的视图创建先后顺序不保证；无条件清空会把新页刚绑好的
     * 分页器一起抹掉，表现就是「滑动没反应」。
     */
    fun unbindPreferencePager(pager: io.legado.app.ui.widget.PreferencePager) {
        if (preferencePager === pager) {
            preferencePager = null
        }
    }

    override fun onHorizontalSwipe(direction: Int): Boolean {
        val pager = preferencePager ?: return false
        pager.moveBy(direction)
        return true
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        when (val configTag = intent.getStringExtra("configTag")) {
            ConfigTag.OTHER_CONFIG -> replaceFragment<OtherConfigFragment>(configTag)
            ConfigTag.THEME_CONFIG -> replaceFragment<ThemeConfigFragment>(configTag)
            ConfigTag.WEB_DAV_CONFIG -> replaceFragment<WebDavConfigFragment>(configTag)
            ConfigTag.WEB_DAV_SYNC_CONFIG -> replaceFragment<WebDavSyncConfigFragment>(configTag)
            ConfigTag.COVER_CONFIG -> replaceFragment<CoverConfigFragment>(configTag)
            ConfigTag.WELCOME_CONFIG -> replaceFragment<WelcomeConfigFragment>(configTag)
            else -> finish()
        }
        // 墨水屏专属：进入二级设置页后，返回键应先退回上级列表，而不是直接关闭本页
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    finish()
                }
            }
        })
    }

    override fun setTitle(resId: Int) {
        super.setTitle(resId)
        binding.titleBar.setTitle(resId)
    }

    /**
     * 墨水屏专属：二级页需要把标题栏换成分类名并显示返回箭头
     */
    fun upTitleBar(title: CharSequence?, showBack: Boolean, onBack: () -> Unit) {
        binding.titleBar.title = title
        binding.titleBar.toolbar.navigationIcon = if (showBack) {
            ContextCompat.getDrawable(this, R.drawable.ic_arrow_back)
        } else {
            null
        }
        binding.titleBar.setNavigationOnClickListener {
            if (showBack) onBack()
        }
    }

    inline fun <reified T : Fragment> replaceFragment(configTag: String) {
        intent.putExtra("configTag", configTag)
        @Suppress("DEPRECATION")
        val configFragment = supportFragmentManager.findFragmentByTag(configTag)
            ?: T::class.java.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.configFrameLayout, configFragment, configTag)
            .commit()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
    }

}
