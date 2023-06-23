package com.wireguard.android.viewmodel

import android.os.Parcel
import android.os.Parcelable
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableList
import com.wireguard.android.BR
import com.wireguard.android.model.SinfoniaTier3
import com.wireguard.android.model.ApplicationUUID
import java.net.URL
import java.util.UUID

class SinfoniaProxy : BaseObservable, Parcelable {
    val deployments: ObservableList<CloudletDeploymentProxy> = ObservableArrayList()

    @get:Bindable
    var tier1Url: String = "https://cmu.findcloudlet.org"
        set(value) {
            field = value
            notifyPropertyChanged(BR.tier1Url)
        }

    @get:Bindable
    var applicationName: String = "helloworld"
        set(value) {
            field = value
            notifyPropertyChanged(BR.applicationName)
        }

    @get:Bindable
    var uuid: String = "00000000-0000-0000-0000-000000000000"
        set(value) {
            field = value
            notifyPropertyChanged(BR.uuid)
        }

    @get:Bindable
    var zeroconf: Boolean = false
        set(value) {
            field = value
            notifyPropertyChanged(BR.zeroconf)
        }

    @get:Bindable
    val application = mutableListOf<String>()

    constructor(parcel: Parcel) : this() {
    }

    constructor(other: SinfoniaTier3) {
        tier1Url = other.tier1Url.toString()
        applicationName = other._applicationName
        uuid = other.uuid.toString()
        zeroconf = other._zeroconf
        if (other.application != null) application.addAll(other.application!!)
        other.deployments.forEach {
            val proxy = CloudletDeploymentProxy(it)
            deployments.add(proxy)
            proxy.bind(this)
        }
    }

    constructor()

    fun addDeployment(): CloudletDeploymentProxy {
        val proxy = CloudletDeploymentProxy()
        deployments.add(proxy)
        proxy.bind(this)
        return proxy
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        TODO("Not yet implemented")
    }

    companion object CREATOR : Parcelable.Creator<SinfoniaProxy> {
        override fun createFromParcel(parcel: Parcel): SinfoniaProxy {
            return SinfoniaProxy(parcel)
        }

        override fun newArray(size: Int): Array<SinfoniaProxy?> {
            return arrayOfNulls(size)
        }
    }

}