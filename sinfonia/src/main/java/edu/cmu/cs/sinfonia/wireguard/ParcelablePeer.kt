/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package edu.cmu.cs.sinfonia.wireguard

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RequiresApi
import com.wireguard.config.Attribute
import com.wireguard.config.BadConfigException
import com.wireguard.config.Peer

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

    var endpoint: String = ""

    private var persistentKeepalive: String = ""

    private var preSharedKey: String = ""

    var publicKey: String = ""

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

    constructor()

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

    // Replace the first instance of the wildcard with the public network list, or vice versa.
    // DNS servers only need to handled specially when we're excluding private IPs.
    fun setExcludingPrivateIps(excludingPrivateIps: Boolean) {
        if (!isAbleToExcludePrivateIps || isExcludingPrivateIps == excludingPrivateIps) return
        val oldNetworks = if (excludingPrivateIps) IPV4_WILDCARD else IPV4_PUBLIC_NETWORKS
        val newNetworks = if (excludingPrivateIps) IPV4_PUBLIC_NETWORKS else IPV4_WILDCARD
        val input: Collection<String> = getAllowedIpsSet()
        val outputSize = input.size - oldNetworks.size + newNetworks.size
        val output: MutableCollection<String?> = LinkedHashSet(outputSize)
        var replaced = false
        // Replace the first instance of the wildcard with the public network list, or vice versa.
        for (network in input) {
            if (oldNetworks.contains(network)) {
                if (!replaced) {
                    for (replacement in newNetworks) if (!output.contains(replacement)) output.add(replacement)
                    replaced = true
                }
            } else if (!output.contains(network)) {
                output.add(network)
            }
        }
        // DNS servers only need to handled specially when we're excluding private IPs.
        if (excludingPrivateIps) output.addAll(dnsRoutes) else output.removeAll(dnsRoutes.toSet())
        allowedIps = Attribute.join(output)
        allowedIpsState = if (excludingPrivateIps) AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS else AllowedIpsState.CONTAINS_IPV4_WILDCARD
    }

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

    fun unbind() {
        if (owner == null) return
        val peers = owner!!.peers
        peers.remove(this)
        setInterfaceDns("")
        setTotalPeers(0)
        owner = null
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
