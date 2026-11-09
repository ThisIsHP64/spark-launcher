package dev.gearturner.sparklauncher.feature.settings

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/** how a request to unlock the hidden apps page ended */
internal enum class LockResult {
    Granted,

    /** the user backed out, or the prompt was closed because spark left the screen */
    Cancelled,

    /** no screen lock or biometric is set up, so there is nothing to check the user against */
    Unavailable,

    /** the system gave up, for example after too many wrong attempts */
    Failed,
}

/**
 * asks the system to confirm the user with a fingerprint or face, falling back to the screen lock
 * pin, pattern, or password in the same prompt so a broken sensor never locks anyone out. the
 * framework prompt is used because it needs no extra library and no fragment activity.
 */
internal class HiddenAppsLock(private val context: Context) {

    private var signal: CancellationSignal? = null

    /** false when the device has no screen lock, in which case the page must stay shut */
    fun isAvailable(): Boolean =
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** [onResult] runs once on the main thread; a second request while one is open is ignored */
    fun authenticate(title: String, subtitle: String, onResult: (LockResult) -> Unit) {
        if (signal != null) return
        if (!isAvailable()) {
            onResult(LockResult.Unavailable)
            return
        }
        val cancel = CancellationSignal().also { signal = it }
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(
            cancel,
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    signal = null
                    onResult(LockResult.Granted)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    signal = null
                    onResult(lockResultOfError(errorCode))
                }
            },
        )
    }

    /** closes an open prompt, for when settings leaves the screen */
    fun cancel() {
        signal?.cancel()
        signal = null
    }

    private companion object {
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

/** a wrong finger is retried inside the prompt, so only terminal errors reach here */
internal fun lockResultOfError(code: Int): LockResult = when (code) {
    BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
    BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
    -> LockResult.Cancelled
    BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
    BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT,
    BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE,
    BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL,
    -> LockResult.Unavailable
    else -> LockResult.Failed
}
