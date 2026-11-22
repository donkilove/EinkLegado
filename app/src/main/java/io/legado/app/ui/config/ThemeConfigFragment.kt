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
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogImageBlurringBinding
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.ColorPreference
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.applyTint
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readUri
import io.legado.app.utils.removePref
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.FileOutputStream


@Suppress("SameParameterValue")
class ThemeConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    MenuProvider {

    private val requestCodeBgLight = 121
    private val requestCodeBgDark = 122
    private val selectImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                requestCodeBgLight -> setBgFromUri(uri, PreferKey.bgImage) {
                    upTheme(false)
                }

                requestCodeBgDark -> setBgFromUri(uri, PreferKey.bgImageN) {
                    upTheme(true)
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // 墨水屏：本页拆成「软件设置 / 白天主题 / 夜间主题」三个二级入口，
        // 进入子页时只加载该子屏幕下的项，因此必须带 rootKey
        setPreferencesFromResource(R.xml.pref_config_theme, rootKey)
        if (Build.VERSION.SDK_INT < 26) {
            findPreference<Preference>(PreferKey.launcherIcon)?.let {
                preferenceScreen.removePreference(it)
            }
        }
        upPreferenceSummary(PreferKey.bgImage, getPrefString(PreferKey.bgImage))
        upPreferenceSummary(PreferKey.bgImageN, getPrefString(PreferKey.bgImageN))
        upPreferenceSummary(PreferKey.barElevation, AppConfig.elevation.toString())
        upPreferenceSummary(PreferKey.fontScale)
        findPreference<ColorPreference>(PreferKey.cBackground)?.let {
            it.onSaveColor = { color ->
                if (!ColorUtils.isColorLight(color)) {
                    toastOnUi(R.string.day_background_too_dark)
                    true
                } else {
                    false
                }
            }
        }
        findPreference<ColorPreference>(PreferKey.cNBackground)?.let {
            it.onSaveColor = { color ->
                if (ColorUtils.isColorLight(color)) {
                    toastOnUi(R.string.night_background_too_light)
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.setEdgeEffectColor(primaryColor)
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
            PreferKey.cPrimary,
            PreferKey.cAccent,
            PreferKey.cBackground,
            PreferKey.cBBackground,
            PreferKey.tNavBar-> {
                upTheme(false)
            }

            PreferKey.cNPrimary,
            PreferKey.cNAccent,
            PreferKey.cNBackground,
            PreferKey.cNBBackground,
            PreferKey.tNavBarN -> {
                upTheme(true)
            }

            PreferKey.bgImage,
            PreferKey.bgImageN -> {
                upPreferenceSummary(key, getPrefString(key))
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

            PreferKey.bgImage -> selectBgAction(false)
            PreferKey.bgImageN -> selectBgAction(true)
            "themeList" -> ThemeListDialog().show(childFragmentManager, "themeList")
            "saveDayTheme",
            "saveNightTheme" -> alertSaveTheme(key)

            "coverConfig" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.COVER_CONFIG)
            }

            "welcomeStyle" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.WELCOME_CONFIG)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    @SuppressLint("InflateParams")
    private fun alertSaveTheme(key: String) {
        alert(R.string.theme_name) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "name"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { themeName ->
                    when (key) {
                        "saveDayTheme" -> {
                            ThemeConfig.saveDayTheme(requireContext(), themeName)
                        }

                        "saveNightTheme" -> {
                            ThemeConfig.saveNightTheme(requireContext(), themeName)
                        }
                    }
                }
            }
            cancelButton()
        }
    }

    private fun selectBgAction(isNight: Boolean) {
        val bgKey = if (isNight) PreferKey.bgImageN else PreferKey.bgImage
        val blurringKey = if (isNight) PreferKey.bgImageNBlurring else PreferKey.bgImageBlurring
        val actions = arrayListOf(
            getString(R.string.background_image_blurring),
            getString(R.string.select_image)
        )
        if (!getPrefString(bgKey).isNullOrEmpty()) {
            actions.add(getString(R.string.delete))
        }
        context?.selector(items = actions) { _, i ->
            when (i) {
                0 -> alertImageBlurring(blurringKey) {
                    upTheme(isNight)
                }

                1 -> {
                    if (isNight) {
                        selectImage.launch {
                            requestCode = requestCodeBgDark
                            mode = HandleFileContract.IMAGE
                        }
                    } else {
                        selectImage.launch {
                            requestCode = requestCodeBgLight
                            mode = HandleFileContract.IMAGE
                        }
                    }
                }

                2 -> {
                    removePref(bgKey)
                    upTheme(isNight)
                }
            }
        }
    }

    private fun alertImageBlurring(preferKey: String, success: () -> Unit) {
        alert(R.string.background_image_blurring) {
            val alertBinding = DialogImageBlurringBinding.inflate(layoutInflater).apply {
                getPrefInt(preferKey, 0).let {
                    seekBar.progress = it
                    textViewValue.text = it.toString()
                }
                seekBar.setOnSeekBarChangeListener(object : SeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        textViewValue.text = progress.toString()
                    }
                })
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.seekBar.progress.let {
                    putPrefInt(preferKey, it)
                    success.invoke()
                }
            }
            cancelButton()
        }
    }

    private fun upTheme(isNightTheme: Boolean) {
        if (AppConfig.isNightTheme == isNightTheme) {
            // 用 view?.post 而不是 listView.post：切换主题会重建 Activity，
            // Fragment 的 listView 可能已经随视图销毁变成 null，之前会抛
            // NullPointerException: RecyclerView.post on a null object reference
            view?.post {
                ThemeConfig.applyTheme(requireContext())
                recreateActivities()
            }
        }
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

            PreferKey.bgImage,
            PreferKey.bgImageN -> preference.summary = if (value.isNullOrBlank()) {
                getString(R.string.select_image)
            } else {
                value
            }

            else -> preference.summary = value
        }
    }

    private fun setBgFromUri(uri: Uri, preferenceKey: String, success: () -> Unit) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            lifecycleScope.launch {
                kotlin.runCatching {
                    appCtx.toastOnUi("下载背景图片中...")
                    val analyzeUrl = AnalyzeUrl(uri.toString())
                    val url = analyzeUrl.urlNoQuery
                    var file = requireContext().externalFiles
                    val res = okHttpClient.newCallResponse(0) {
                        addHeaders(analyzeUrl.headerMap)
                        url(url)
                    }
                    val contentType = res.header("Content-Type") ?: "image/jpeg"
                    val imageType = when {
                        contentType.contains("png", ignoreCase = true) -> "png"
                        contentType.contains("gif", ignoreCase = true) -> "gif"
                        contentType.contains("webp", ignoreCase = true) -> "webp"
                        else -> "jpg"
                    }
                    val suffix = if (url.contains(".9.png", true)) {
                        ".9.png"
                    } else {
                        ".$imageType"
                    }
                    val fileName = MD5Utils.md5Encode(url) + suffix
                    file = FileUtils.createFileIfNotExist(file, preferenceKey, fileName)
                    res.body.byteStream().use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    putPrefString(preferenceKey, file.absolutePath)
                    if (isAdded && context != null) {
                        success()
                    }
                }.onSuccess {
                    appCtx.toastOnUi("设定成功")
                }.onFailure {
                    appCtx.toastOnUi(it.localizedMessage)
                }
            }
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                file = FileUtils.createFileIfNotExist(file, preferenceKey, fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                putPrefString(preferenceKey, file.absolutePath)
                success()
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

    companion object {
        /** 二级设置页的标题，用于替换标题栏文字 */
        private const val ARG_SUB_TITLE = "subScreenTitle"
    }

}