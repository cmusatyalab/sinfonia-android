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
import com.wireguard.config.Interface
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyFormatException
import com.wireguard.crypto.KeyPair

class ParcelableInterface : Parcelable {
    private val excludedApplications: ArrayList<String> = arrayListOf()

    private val includedApplications: ArrayList<String> = arrayListOf()

    private var addresses: String = ""

    var dnsServers: String = ""

    private var listenPort: String = ""

    private var mtu: String = ""

    private var privateKey: String = ""

    val publicKey: String
        get() = try {
            KeyPair(Key.fromBase64(privateKey)).publicKey.toBase64()
        } catch (ignored: KeyFormatException) {
            ""
        }

    private constructor(parcel: Parcel) {
        addresses = parcel.readString() ?: ""
        dnsServers = parcel.readString() ?: ""
        parcel.readStringList(excludedApplications)
        parcel.readStringList(includedApplications)
        listenPort = parcel.readString() ?: ""
        mtu = parcel.readString() ?: ""
        privateKey = parcel.readString() ?: ""
    }

    @RequiresApi(Build.VERSION_CODES.N)
    constructor(other: Interface) {
        addresses = Attribute.join(other.addresses)
        val dnsServerStrings = other.dnsServers.map { it.hostAddress }.plus(other.dnsSearchDomains)
        dnsServers = Attribute.join(dnsServerStrings)
        excludedApplications.addAll(other.excludedApplications)
        includedApplications.addAll(other.includedApplications)
        listenPort = other.listenPort.map { it.toString() }.orElse("")
        mtu = other.mtu.map { it.toString() }.orElse("")
        val keyPair = other.keyPair
        privateKey = keyPair.privateKey.toBase64()
    }

    constructor()

    override fun describeContents() = 0

    @Throws(BadConfigException::class)
    fun resolve(): Interface {
        val builder = Interface.Builder()
        if (addresses.isNotEmpty()) builder.parseAddresses(addresses)
        if (dnsServers.isNotEmpty()) builder.parseDnsServers(dnsServers)
        if (excludedApplications.isNotEmpty()) builder.excludeApplications(excludedApplications)
        if (includedApplications.isNotEmpty()) builder.includeApplications(includedApplications)
        if (listenPort.isNotEmpty()) builder.parseListenPort(listenPort)
        if (mtu.isNotEmpty()) builder.parseMtu(mtu)
        if (privateKey.isNotEmpty()) builder.parsePrivateKey(privateKey)
        return builder.build()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(addresses)
        dest.writeString(dnsServers)
        dest.writeStringList(excludedApplications)
        dest.writeStringList(includedApplications)
        dest.writeString(listenPort)
        dest.writeString(mtu)
        dest.writeString(privateKey)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun overwrite(`interface`: Interface) {
        excludedApplications.clear()
        includedApplications.clear()
        addresses = Attribute.join(`interface`.addresses)
        val dnsServerStrings = `interface`.dnsServers.map { it.hostAddress }.plus(`interface`.dnsSearchDomains)
        dnsServers = Attribute.join(dnsServerStrings)
        excludedApplications.addAll(`interface`.excludedApplications)
        includedApplications.addAll(`interface`.includedApplications)
        listenPort = `interface`.listenPort.map { it.toString() }.orElse("")
        mtu = `interface`.mtu.map { it.toString() }.orElse("")
        val keyPair = `interface`.keyPair
        privateKey = keyPair.privateKey.toBase64()
    }

    private class ParcelableInterfaceCreator : Parcelable.Creator<ParcelableInterface> {
        override fun createFromParcel(parcel: Parcel): ParcelableInterface {
            return ParcelableInterface(parcel)
        }

        override fun newArray(size: Int): Array<ParcelableInterface?> {
            return arrayOfNulls(size)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ParcelableInterface> = ParcelableInterfaceCreator()
    }
}
