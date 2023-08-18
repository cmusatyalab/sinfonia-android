package edu.cmu.cs.sinfonia

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class SinfoniaTest {
    // Context of the app under test.
    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun useAppContext() {
        assertEquals("edu.cmu.cs.sinfonia.test", appContext.packageName)
    }
}