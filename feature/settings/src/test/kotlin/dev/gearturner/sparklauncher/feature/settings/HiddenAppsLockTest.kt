package dev.gearturner.sparklauncher.feature.settings

import android.hardware.biometrics.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenAppsLockTest {

    @Test
    fun backingOutIsACancelNotAFailure() {
        assertEquals(LockResult.Cancelled, lockResultOfError(BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED))
        assertEquals(LockResult.Cancelled, lockResultOfError(BiometricPrompt.BIOMETRIC_ERROR_CANCELED))
    }

    @Test
    fun missingSetupIsUnavailable() {
        assertEquals(LockResult.Unavailable, lockResultOfError(BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL))
        assertEquals(LockResult.Unavailable, lockResultOfError(BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS))
        assertEquals(LockResult.Unavailable, lockResultOfError(BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT))
    }

    @Test
    fun lockoutAndUnknownErrorsDenyAccess() {
        assertEquals(LockResult.Failed, lockResultOfError(BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT))
        assertEquals(LockResult.Failed, lockResultOfError(9999))
    }
}
