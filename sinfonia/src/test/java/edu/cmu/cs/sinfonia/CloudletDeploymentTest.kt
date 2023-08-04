package edu.cmu.cs.sinfonia

import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import com.wireguard.crypto.Key
import org.junit.Assert.*
import org.junit.Test
import java.util.Objects

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class CloudletDeploymentTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    private fun validConfig(): Config {
        var config: Config? = null
        try {
            Objects.requireNonNull(javaClass.classLoader).getResourceAsStream("working-helloworld.conf").use { `is` -> config = Config.parse(`is`) }
        } catch (e: BadConfigException) {
            fail("'working-helloworld.conf' should never fail to parse")
        }
        assertNotNull("config cannot be null after parsing", config)
        return config!!
    }

    companion object {
        private const val validUUID = "00000000-0000-0000-0000-000000000000"
        private val validApplicationKey = Key.fromBase64("HUN1dBaBCwl27MO4QayVEJrjBC1zh1thTTYpti7FWSY=")
        private const val validStatus = "deployed"
        private const val validDeploymentName = "hello-world"
        private const val validCreated = "2023-07-10T14:53:48+00:00"
    }
}