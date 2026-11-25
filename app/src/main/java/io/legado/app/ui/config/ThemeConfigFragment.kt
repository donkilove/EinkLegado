package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import io.legado.app.R
import io.legado.app.base.AppContextWrapper
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.http.addHeaders
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.FileUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.FileOutputStream
import io.legado.app.lib.prefs.SwitchPreference
import io.legado.app.utils.getPrefBoolean
import io.legado.app.ui.widget.setupPreferencePagingWithSwipe


@Suppress("SameParameterValue")
class ThemeConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    MenuProvider {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // 墨水屏：本页只剩「夜间模式」开关与「软件设置」子页。
        // 进入子页时只加载该子屏幕下的项，因此必须带 rootKey。
        setPreferencesFromResource(R.xml.pref_config_theme, rootKey)
        if (Build.VERSION.SDK_INT < 26) {
            findPreference<Preference>(PreferKey.launcherIcon)?.let {
                preferenceScreen.removePreference(it)
            }
        }
        upPreferenceSummary(PreferKey.barElevation, AppConfig.elevation.toString())
        upPreferenceSummary(PreferKey.fontScale)
        // 用计算值初始化开关，避免存下来的偏好和实际生效的昼夜状态不一致
        findPreference<SwitchPreference>(KEY_NIGHT_MODE)?.isChecked = AppConfig.isNightTheme
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.setEdgeEffectColor(primaryColor)
        // 墨水屏：设置项用翻页代替滚动
        setupPreferencePagingWithSwipe(
            (activity as? ConfigActivity)?.pageBar,
            activity as? ConfigActivity
        )
        activity?.addMenuProvider(this, viewLifecycleOwner)
        upTitleBar()
    }

    override fun onResume() {
        super.onResume()
        upTitleBar()
    }

    /**
     * 墨水屏：进入二级设置页。
     *
     * 不能依赖 AndroidX 的 onPreferenceStartScreen —— 那个默认实现会在这个宿主
     * （Activity 直接持有 Fragment + 自建返回键回调）下替换错容器，
     * 返回时还会触发 Fragment already added。这里显式 replace 并压栈，与
     * OtherConfigFragment 的做法保持一致。
     */
    private fun openSubScreen(screen: PreferenceScreen) {
        val fragment = ThemeConfigFragment().apply {
            arguments = Bundle().apply {
                putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, screen.key)
                putString(ARG_SUB_TITLE, screen.title?.toString())
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.configFrameLayout, fragment, ConfigTag.THEME_CONFIG)
            .addToBackStack(null)
            .commit()
    }

    private fun upTitleBar() {
        val act = activity as? ConfigActivity ?: return
        val subTitle = arguments?.getString(ARG_SUB_TITLE)
        act.upTitleBar(
            title = subTitle ?: getString(R.string.theme_setting),
            showBack = true
        ) {
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                act.finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.theme_config, menu)
        // 墨水屏专属：主题固定为 EInk，昼夜切换无效
        menu.findItem(R.id.menu_theme_mode)?.isVisible = false
        menu.applyTint(requireContext())
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.menu_theme_mode -> {
                AppConfig.isNightTheme = !AppConfig.isNightTheme
                ThemeConfig.applyDayNight(requireContext())
                return true
            }
        }
        return false
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        sharedPreferences ?: return
        when (key) {
            PreferKey.launcherIcon -> LauncherIconHelp.changeIcon(getPrefString(key))
            PreferKey.transparentStatusBar -> recreateActivities()
            PreferKey.immNavigationBar -> recreateActivities()
            KEY_NIGHT_MODE -> {
                // 配色固定，只切换昼夜：日间白底黑字、夜间黑底白字
                AppConfig.isNightTheme = getPrefBoolean(KEY_NIGHT_MODE, false)
                ThemeConfig.applyDayNight(requireContext())
            }
        }

    }

    @SuppressLint("PrivateResource")
    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        // 墨水屏：二级页导航自己接管，见 openSubScreen 的说明
        if (preference is PreferenceScreen && preference.preferenceCount > 0) {
            openSubScreen(preference)
            return true
        }
        when (val key = preference.key) {
            PreferKey.barElevation -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.bar_elevation))
                .setMaxValue(32)
                .setMinValue(0)
                .setValue(AppConfig.elevation)
                .setCustomButton((R.string.btn_default_s)) {
                    AppConfig.elevation = AppConst.sysElevation
                    recreateActivities()
                }
                .show {
                    AppConfig.elevation = it
                    recreateActivities()
                }

            PreferKey.fontScale -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.font_scale))
                .setMaxValue(16)
                .setMinValue(8)
                .setValue(10)
                .setCustomButton((R.string.btn_default_s)) {
                    putPrefInt(PreferKey.fontScale, 0)
                    recreateActivities()
                }
                .show {
                    putPrefInt(PreferKey.fontScale, it)
                    recreateActivities()
                }

            "coverConfig" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.COVER_CONFIG)
            }

            "welcomeStyle" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.WELCOME_CONFIG)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }



    private fun recreateActivities() {
        postEvent(EventBus.RECREATE, "")
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String? = null) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.barElevation -> preference.summary =
                getString(R.string.bar_elevation_s, value)

            PreferKey.fontScale -> {
                val fontScale = AppContextWrapper.getFontScale(requireContext())
                preference.summary = getString(R.string.font_scale_summary, fontScale)
            }

            else -> preference.summary = value
        }
    }

    companion object {
        /** 二级设置页的标题，用于替换标题栏文字 */
        private const val ARG_SUB_TITLE = "subScreenTitle"

        /** 夜间模式开关的偏好键（配色固定，只切昼夜） */
        private const val KEY_NIGHT_MODE = "themeNightMode"
    }

}
