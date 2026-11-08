package dev.gearturner.sparklauncher.core.data

import dev.gearturner.sparklauncher.core.model.HomeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** each home mode keeps its own layout, so switching modes never rearranges the other one */
enum class LayoutId(val key: String) {
    DRAWER("drawer"),
    HOME("home"),
    ;

    companion object {
        fun of(mode: HomeMode) = if (mode == HomeMode.HOME_ONLY) HOME else DRAWER
    }
}

/** which layout the home screen shows and edits right now */
interface ActiveLayout {
    val layout: Flow<LayoutId>
    suspend fun current(): LayoutId
}

class SettingsActiveLayout @Inject constructor(private val settings: SettingsRepository) : ActiveLayout {
    override val layout: Flow<LayoutId> = settings.settings.map { LayoutId.of(it.homeMode) }.distinctUntilChanged()

    override suspend fun current(): LayoutId = LayoutId.of(settings.current().homeMode)
}
