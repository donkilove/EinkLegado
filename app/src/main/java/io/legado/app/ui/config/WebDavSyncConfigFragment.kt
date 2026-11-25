package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.ui.widget.setupPreferencePagingWithSwipe

/**
 * WebDav 同步开关。
 *
 * 从 [WebDavConfigFragment] 拆出：连接信息（地址/账号/密码/子目录）是「配一次就不动」的，
 * 而同步开关是「会反复调」的。混在一页时，墨水屏下要翻两页才能碰到开关，
 * 且连接信息占掉了首屏。拆开后连接页只剩 5 项、开关页 5 项，各自一屏内可见。
 */
class WebDavSyncConfigFragment : PreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_web_dav_sync)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.web_dav_sync_setting)
        listView.setEdgeEffectColor(primaryColor)
        // 墨水屏：设置项用翻页代替滚动
                setupPreferencePagingWithSwipe((activity as? ConfigActivity)?.pageBar, activity as? ConfigActivity)
    }
}
