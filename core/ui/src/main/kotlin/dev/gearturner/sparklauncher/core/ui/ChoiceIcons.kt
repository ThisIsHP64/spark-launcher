package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.IconSource
import dev.gearturner.sparklauncher.core.model.ShortcutKey

private val ChoiceIconSize = 36.dp

/** the leading icon of an app row in a [ChoiceDialog]; an empty box keeps rows aligned while it loads */
@Composable
fun AppChoiceIcon(key: AppKey) {
    val icon = rememberAppIcon(key, ChoiceIconSize)
    if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(ChoiceIconSize))
    else Box(Modifier.size(ChoiceIconSize))
}

/** the leading icon of a shortcut row, drawn from the shortcut itself with its app as the badge owner */
@Composable
fun ShortcutChoiceIcon(key: ShortcutKey, owner: AppKey?) {
    val icon = rememberIcon(IconSource.Shortcut(key, owner), ChoiceIconSize)
    if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(ChoiceIconSize))
    else Box(Modifier.size(ChoiceIconSize))
}
