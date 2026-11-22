package io.legado.app.ui.config

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.getPrefString
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toEditable
import splitties.init.appCtx

/**
 * WebDav 连接与同步设置。
 *
 * 从原「备份与恢复」页拆出：这里只维护连接信息与同步开关，
 * 备份/恢复操作在 [BackupConfigFragment]。
 */
class WebDavConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val viewModel by activityViewModels<ConfigViewModel>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_web_dav)
        findPreference<EditTextPreference>(PreferKey.webDavPassword)?.let {
            it.setOnBindEditTextListener { editText ->
                editText.inputType =
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
                editText.setSelection(editText.text.length)
            }
        }
        findPreference<EditTextPreference>(PreferKey.webDavDir)?.let {
            it.setOnBindEditTextListener { editText ->
                editText.text = AppConfig.webDavDir?.toEditable()
                editText.setSelection(editText.text.length)
            }
        }
        findPreference<EditTextPreference>(PreferKey.webDavDeviceName)?.let {
            it.setOnBindEditTextListener { editText ->
                editText.text = AppConfig.webDavDeviceName?.toEditable()
                editText.setSelection(editText.text.length)
            }
        }
        upPreferenceSummary(PreferKey.webDavUrl, getPrefString(PreferKey.webDavUrl))
        upPreferenceSummary(PreferKey.webDavAccount, getPrefString(PreferKey.webDavAccount))
        upPreferenceSummary(PreferKey.webDavPassword, getPrefString(PreferKey.webDavPassword))
        upPreferenceSummary(PreferKey.webDavDir, AppConfig.webDavDir)
        upPreferenceSummary(PreferKey.webDavDeviceName, AppConfig.webDavDeviceName)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.web_dav_set)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        listView.setEdgeEffectColor(primaryColor)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.webDavUrl,
            PreferKey.webDavAccount,
            PreferKey.webDavPassword,
            PreferKey.webDavDir -> view?.post {
                upPreferenceSummary(key, appCtx.getPrefString(key))
                viewModel.upWebDavConfig()
            }

            PreferKey.webDavDeviceName -> upPreferenceSummary(key, getPrefString(key))
        }
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String?) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.webDavUrl ->
                preference.summary =
                    if (value.isNullOrBlank()) getString(R.string.web_dav_url_s) else value

            PreferKey.webDavAccount ->
                preference.summary =
                    if (value.isNullOrBlank()) getString(R.string.web_dav_account_s) else value

            PreferKey.webDavPassword ->
                preference.summary =
                    if (value.isNullOrEmpty()) getString(R.string.web_dav_pw_s)
                    else "*".repeat(value.length)

            PreferKey.webDavDir -> preference.summary = value ?: "legado"

            else -> preference.summary = value
        }
    }
}
