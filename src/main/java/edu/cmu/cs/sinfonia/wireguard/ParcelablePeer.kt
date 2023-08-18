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
import com.wireguard.config.Attribute
import com.wireguard.config.BadConfigException
import com.wireguard.config.Peer

/**
 * This class implements the parcelable version of [Peer] in the tunnel library.
 *
 * @property dnsRoutes
 * @property allowedIpsState
 * @property owner
 * @property totalPeers
 * @property allowedIps
 * @property endpoint
 * @property persistentKeepalive
 * @property preSharedKey
 * @property publicKey
 * @property isAbleToExcludePrivateIps
 * @property isExcludingPrivateIps
 */
class ParcelablePeer : Parcelable {
    private val dnsRoutes: MutableList<String?> = ArrayList()
    private var allowedIpsState = AllowedIpsState.INVALID
    private var owner: ParcelableConfig? = null
    private var totalPeers = 0

    private var allowedIps: String = ""
        set(value) {
            field = value
            calculateAllowedIpsState()
        }

    private var endpoint: String = ""

    private var persistentKeepalive: String = ""

    private var preSharedKey: String = ""

    private var publicKey: String = ""

    private val isAbleToExcludePrivateIps: Boolean
        get() = allowedIpsState == AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS || allowedIpsState == AllowedIpsState.CONTAINS_IPV4_WILDCARD

    private val isExcludingPrivateIps: Boolean
        get() = allowedIpsState == AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS

    private constructor(parcel: Parcel) {
        allowedIps = parcel.readString() ?: ""
        endpoint = parcel.readString() ?: ""
        persistentKeepalive = parcel.readString() ?: ""
        preSharedKey = parcel.readString() ?: ""
        publicKey = parcel.readString() ?: ""
    }

    @RequiresApi(Build.VERSION_CODES.N)
    constructor(other: Peer) {
        allowedIps = Attribute.join(other.allowedIps)
        endpoint = other.endpoint.map { it.toString() }.orElse("")
        persistentKeepalive = other.persistentKeepalive.map { it.toString() }.orElse("")
        preSharedKey = other.preSharedKey.map { it.toBase64() }.orElse("")
        publicKey = other.publicKey.toBase64()
    }

    fun bind(owner: ParcelableConfig) {
        val interfaze: ParcelableInterface = owner.`interface`
        val peers = owner.peers
        setInterfaceDns(interfaze.dnsServers)
        setTotalPeers(peers.size)
        this.owner = owner
    }

    private fun calculateAllowedIpsState() {
        val newState: AllowedIpsState = if (totalPeers == 1) {
            // String comparison works because we only care if allowedIps is a superset of one of
            // the above sets of (valid) *networks*. We are not checking for a superset based on
            // the individual addresses in each set.
            val networkStrings: Collection<String> = getAllowedIpsSet()
            // If allowedIps contains both the wildcard and the public networks, then private
            // networks aren't excluded!
            if (networkStrings.containsAll(IPV4_WILDCARD))
                AllowedIpsState.CONTAINS_IPV4_WILDCARD
            else if (networkStrings.containsAll(IPV4_PUBLIC_NETWORKS))
                AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS
            else
                AllowedIpsState.OTHER
        } else {
            AllowedIpsState.INVALID
        }
        if (newState != allowedIpsState) {
            allowedIpsState = newState
        }
    }

    override fun describeContents() = 0

    private fun getAllowedIpsSet() = setOf(*Attribute.split(allowedIps))

    @Throws(BadConfigException::class)
    fun resolve(): Peer {
        val builder = Peer.Builder()
        if (allowedIps.isNotEmpty()) builder.parseAllowedIPs(allowedIps)
        if (endpoint.isNotEmpty()) builder.parseEndpoint(endpoint)
        if (persistentKeepalive.isNotEmpty()) builder.parsePersistentKeepalive(persistentKeepalive)
        if (preSharedKey.isNotEmpty()) builder.parsePreSharedKey(preSharedKey)
        if (publicKey.isNotEmpty()) builder.parsePublicKey(publicKey)
        return builder.build()
    }

    private fun setInterfaceDns(dnsServers: CharSequence) {
        val newDnsRoutes = Attribute.split(dnsServers).filter { !it.contains(":") }.map { "$it/32" }
        if (allowedIpsState == AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS) {
            val input = getAllowedIpsSet()
            // Yes, this is quadratic in the number of DNS servers, but most users have 1 or 2.
            val output = input.filter { !dnsRoutes.contains(it) || newDnsRoutes.contains(it) }.plus(newDnsRoutes).distinct()
            // None of the public networks are /32s, so this cannot change the AllowedIPs state.
            allowedIps = Attribute.join(output)
        }
        dnsRoutes.clear()
        dnsRoutes.addAll(newDnsRoutes)
    }

    private fun setTotalPeers(totalPeers: Int) {
        if (this.totalPeers == totalPeers) return
        this.totalPeers = totalPeers
        calculateAllowedIpsState()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(allowedIps)
        dest.writeString(endpoint)
        dest.writeString(persistentKeepalive)
        dest.writeString(preSharedKey)
        dest.writeString(publicKey)
    }

    private enum class AllowedIpsState {
        CONTAINS_IPV4_PUBLIC_NETWORKS, CONTAINS_IPV4_WILDCARD, INVALID, OTHER
    }

    private class PeerProxyCreator : Parcelable.Creator<ParcelablePeer> {
        override fun createFromParcel(parcel: Parcel): ParcelablePeer {
            return ParcelablePeer(parcel)
        }

        override fun newArray(size: Int): Array<ParcelablePeer?> {
            return arrayOfNulls(size)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ParcelablePeer> = PeerProxyCreator()
        private val IPV4_PUBLIC_NETWORKS = setOf(
            "0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4", "32.0.0.0/3",
            "64.0.0.0/2", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/6", "172.0.0.0/12",
            "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9", "173.0.0.0/8", "174.0.0.0/7",
            "176.0.0.0/4", "192.0.0.0/9", "192.128.0.0/11", "192.160.0.0/13", "192.169.0.0/16",
            "192.170.0.0/15", "192.172.0.0/14", "192.176.0.0/12", "192.192.0.0/10",
            "193.0.0.0/8", "194.0.0.0/7", "196.0.0.0/6", "200.0.0.0/5", "208.0.0.0/4"
        )
        private val IPV4_WILDCARD = setOf("0.0.0.0/0")
    }
}
