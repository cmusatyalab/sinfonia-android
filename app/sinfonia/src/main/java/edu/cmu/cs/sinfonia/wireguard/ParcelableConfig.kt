/*
 * Copyright 2023 Carnegie Mellon University
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package edu.cmu.cs.sinfonia.wireguard

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RequiresApi
import androidx.core.os.ParcelCompat
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import com.wireguard.config.Peer

/**
 * This class implements the parcelable version of class [Config] in the tunnel library.
 *
 * @property interface The interface of the tunnel
 * @property peers The array list of peers of the tunnel
 */
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
