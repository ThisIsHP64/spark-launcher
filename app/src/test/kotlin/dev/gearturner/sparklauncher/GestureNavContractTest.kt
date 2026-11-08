package dev.gearturner.sparklauncher

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureNavContractTest {

    @Test
    fun swipeHomeIntentCarriesTheContract() {
        assertTrue(GestureNavContract.isHomeIntent(Intent.ACTION_MAIN, setOf(Intent.CATEGORY_HOME, Intent.CATEGORY_DEFAULT)))
    }

    @Test
    fun otherIntentsCannotSmuggleAContractIn() {
        assertFalse(GestureNavContract.isHomeIntent(Intent.ACTION_VIEW, setOf(Intent.CATEGORY_HOME)))
        assertFalse(GestureNavContract.isHomeIntent(Intent.ACTION_MAIN, setOf(Intent.CATEGORY_LAUNCHER)))
        assertFalse(GestureNavContract.isHomeIntent(Intent.ACTION_MAIN, null))
        assertFalse(GestureNavContract.isHomeIntent(null, setOf(Intent.CATEGORY_HOME)))
    }
}
