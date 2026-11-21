@file:Suppress("unused")

package io.legado.app.utils

import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import io.legado.app.help.config.AppConfig

/**
 * 墨水屏：Snackbar 默认从底部滑入，在电纸上会拖出一片残影。Material 没有「无动画」模式，改用淡入（只引起一次刷新，不产生位移拖影）。
 */
private fun Snackbar.einkNoAnim(): Snackbar {
    if (AppConfig.isEInkMode) {
        animationMode = com.google.android.material.snackbar.BaseTransientBottomBar.ANIMATION_MODE_FADE
    }
    return this
}

/**
 * Display the Snackbar with the [Snackbar.LENGTH_SHORT] duration.
 *
 * @param message the message text resource.
 */
@JvmName("snackbar2")
fun View.snackbar(
    @StringRes message: Int
) = Snackbar
    .make(this, message, Snackbar.LENGTH_SHORT)
    .apply { einkNoAnim(); show() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_LONG] duration.
 *
 * @param message the message text resource.
 */
@JvmName("longSnackbar2")
fun View.longSnackbar(
    @StringRes message: Int
) = Snackbar
    .make(this, message, Snackbar.LENGTH_LONG)
    .apply { einkNoAnim(); show() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_INDEFINITE] duration.
 *
 * @param message the message text resource.
 */
@JvmName("indefiniteSnackbar2")
fun View.indefiniteSnackbar(
    @StringRes message: Int
) = Snackbar
    .make(this, message, Snackbar.LENGTH_INDEFINITE)
    .apply { einkNoAnim(); show() }

/**
 * Display the Snackbar with the [Snackbar.LENGTH_SHORT] duration.
 *
 * @param message the message text.
 */
@JvmName("snackbar2")
fun View.snackbar(
    message: CharSequence
) = Snackbar
    .make(this, message, Snackbar.LENGTH_SHORT)
    .apply { einkNoAnim(); show() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_LONG] duration.
 *
 * @param message the message text.
 */
@JvmName("longSnackbar2")
fun View.longSnackbar(
    message: CharSequence
) = Snackbar
    .make(this, message, Snackbar.LENGTH_LONG)
    .apply { einkNoAnim(); show() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_INDEFINITE] duration.
 *
 * @param message the message text.
 */
@JvmName("indefiniteSnackbar2")
fun View.indefiniteSnackbar(
    message: CharSequence
) = Snackbar
    .make(this, message, Snackbar.LENGTH_INDEFINITE)
    .apply { einkNoAnim(); show() }

/**
 * Display the Snackbar with the [Snackbar.LENGTH_SHORT] duration.
 *
 * @param message the message text resource.
 */
@JvmName("snackbar2")
fun View.snackbar(
    message: Int,
    @StringRes actionText:
    Int, action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_SHORT)
    .setAction(actionText, action)
    .apply { einkNoAnim(); show() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_LONG] duration.
 *
 * @param message the message text resource.
 */
@JvmName("longSnackbar2")
fun View.longSnackbar(
    @StringRes message: Int,
    @StringRes actionText: Int,
    action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_LONG)
    .setAction(actionText, action)
    .apply { einkNoAnim(); show() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_INDEFINITE] duration.
 *
 * @param message the message text resource.
 */
@JvmName("indefiniteSnackbar2")
fun View.indefiniteSnackbar(
    @StringRes message: Int,
    @StringRes actionText: Int,
    action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_INDEFINITE)
    .setAction(actionText, action)
    .apply { einkNoAnim(); show() }

/**
 * Display the Snackbar with the [Snackbar.LENGTH_SHORT] duration.
 *
 * @param message the message text.
 */
@JvmName("snackbar2")
fun View.snackbar(
    message: CharSequence,
    actionText: CharSequence,
    action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_SHORT)
    .setAction(actionText, action)
    .apply { einkNoAnim(); show() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_LONG] duration.
 *
 * @param message the message text.
 */
@JvmName("longSnackbar2")
fun View.longSnackbar(
    message: CharSequence,
    actionText: CharSequence,
    action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_LONG)
    .setAction(actionText, action)
    .apply { einkNoAnim(); show() }

/**
 * Display Snackbar with the [Snackbar.LENGTH_INDEFINITE] duration.
 *
 * @param message the message text.
 */
@JvmName("indefiniteSnackbar2")
fun View.indefiniteSnackbar(
    message: CharSequence,
    actionText: CharSequence,
    action: (View) -> Unit
) = Snackbar
    .make(this, message, Snackbar.LENGTH_INDEFINITE)
    .setAction(actionText, action)
    .apply { einkNoAnim(); show() }