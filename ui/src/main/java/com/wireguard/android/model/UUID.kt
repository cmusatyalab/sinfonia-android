package com.wireguard.android.model

import java.util.UUID

class ApplicationUUID {
    companion object {
        val ALIASES = mapOf(
                "helloworld" to UUID.fromString("00000000-0000-0000-0000-000000000000")
        )
    }
}