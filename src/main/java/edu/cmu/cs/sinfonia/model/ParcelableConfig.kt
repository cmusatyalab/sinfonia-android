/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package edu.cmu.cs.sinfonia.model

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RequiresApi
import androidx.core.os.ParcelCompat
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import com.wireguard.config.Peer

class ParcelableConfig : Parcelable {
    val `interface`: ParcelableInterface
    val peers: ArrayList<ParcelablePeer> = arrayListOf()

    private constructor(parcel: Parcel) {
        `interface` = ParcelCompat.readParcelable(parcel, ParcelableInterface::class.java.classLoader, ParcelableInterface::class.java) ?: ParcelableInterface()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ParcelCompat.readParcelableList(parcel, peers, ParcelablePeer::class.java.classLoader, ParcelablePeer::class.java)
        } else {
            parcel.readTypedList(peers, ParcelablePeer.CREATOR)
        }
        peers.forEach { it.bind(this) }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    constructor(other: Config) {
        `interface` = ParcelableInterface(other.getInterface())
        other.peers.forEach {
            val peer = ParcelablePeer(it)
            peers.add(peer)
            peer.bind(this)
        }
    }

    constructor() {
        `interface` = ParcelableInterface()
    }

    fun addPeer(): ParcelablePeer {
        val peer = ParcelablePeer()
        peers.add(peer)
        peer.bind(this)
        return peer
    }

    override fun describeContents() = 0

    @Throws(BadConfigException::class)
    fun resolve(): Config {
        val resolvedPeers: MutableCollection<Peer> = ArrayList()
        peers.forEach { resolvedPeers.add(it.resolve()) }
        return Config.Builder()
            .setInterface(`interface`.resolve())
            .addPeers(resolvedPeers)
            .build()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(`interface`, flags)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dest.writeParcelableList(peers, flags)
        } else {
            dest.writeTypedList(peers)
        }
    }

    private class ParcelableConfigCreator : Parcelable.Creator<ParcelableConfig> {
        override fun createFromParcel(parcel: Parcel): ParcelableConfig {
            return ParcelableConfig(parcel)
        }

        override fun newArray(size: Int): Array<ParcelableConfig?> {
            return arrayOfNulls(size)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ParcelableConfig> = ParcelableConfigCreator()
    }
}
