package com.wireguard.android.activity

import android.os.Bundle
import androidx.fragment.app.commit
import com.wireguard.android.fragment.DeployConfigFragment
import com.wireguard.android.model.ObservableTunnel

class DeployActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (supportFragmentManager.findFragmentById(android.R.id.content) == null) {
            supportFragmentManager.commit {
                add(android.R.id.content, DeployConfigFragment())
            }
        }
    }

    override fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?): Boolean {
        finish()
        return true
    }
}