package dev.gearturner.sparklauncher.core.model

/**
 * identifies one launchable activity for one user profile.
 * the profile is stored as a serial number because user handles are not stable across reboots.
 */
data class AppKey(
    val packageName: String,
    val className: String,
    val userSerial: Long,
)

data class AppInfo(
    val key: AppKey,
    val label: String,
    /** true for work profile and private space apps, which get a badge and their own drawer tab later */
    val isManagedProfile: Boolean,
    val firstInstallTime: Long,
)
