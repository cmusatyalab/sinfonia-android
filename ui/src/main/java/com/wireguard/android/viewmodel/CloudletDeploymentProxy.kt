package com.wireguard.android.viewmodel

import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.wireguard.android.BR
import com.wireguard.android.model.CloudletDeployment
import com.wireguard.config.BadConfigException
import com.wireguard.crypto.Key
import java.util.UUID

class CloudletDeploymentProxy : BaseObservable, Parcelable {
    var tunnelConfig: ConfigProxy
    private var owner: SinfoniaProxy? = null

    @get:Bindable
    var uuid: String = if (owner != null) owner?.uuid!! else "00000000-0000-0000-0000-000000000000"
        set(value) {
            field = value
            notifyPropertyChanged(BR.uuid)
        }

    @get:Bindable
    var applicationKey: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.applicationKey)
        }

    @get:Bindable
    var deploymentStatus: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.deploymentStatus)
        }

    @get:Bindable
    var deploymentName: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.deploymentName)
        }

    @get:Bindable
    var created: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.created)
        }

    private constructor(parcel: Parcel) : this() {
        tunnelConfig = ParcelCompat.readParcelable(parcel, ConfigProxy::class.java.classLoader, ConfigProxy::class.java) ?: ConfigProxy()
        uuid = parcel.readString() ?: ""
        applicationKey = parcel.readString() ?: ""
        deploymentStatus = parcel.readString() ?: ""
        deploymentName = parcel.readString() ?: ""
        created = parcel.readString() ?: ""
    }

    constructor(other: CloudletDeployment) : this() {
        tunnelConfig = ConfigProxy(other.tunnelConfig)
        uuid = other.uuid.toString()
        applicationKey = other.applicationKey.toBase64()
        deploymentStatus = other.status
        deploymentName = other.deploymentName.toString()
        created = other.created.toString()

    }

    constructor() {
        tunnelConfig = ConfigProxy()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(tunnelConfig, flags)
        dest.writeString(uuid)
        dest.writeString(applicationKey)
        dest.writeString(deploymentStatus)
        dest.writeString(deploymentName)
        dest.writeString(created)
    }

    override fun describeContents() = 0

    fun bind(sinfoniaProxy: SinfoniaProxy) {

    }

    @Throws(BadConfigException::class)
    fun resolve(): CloudletDeployment {
        return CloudletDeployment(
                UUID.fromString(uuid),
                Key.fromBase64(applicationKey),
                deploymentStatus,
                tunnelConfig.resolve(),
                deploymentName,
                created
        )
    }

    private class CloudletDeploymentCreator : Parcelable.Creator<CloudletDeploymentProxy> {
        override fun createFromParcel(parcel: Parcel): CloudletDeploymentProxy {
            return CloudletDeploymentProxy(parcel)
        }

        override fun newArray(size: Int): Array<CloudletDeploymentProxy?> {
            return arrayOfNulls(size)
        }
    }

    companion object CREATOR : Parcelable.Creator<CloudletDeploymentProxy> {
        @JvmField
        val CREATOR: Parcelable.Creator<CloudletDeploymentProxy> = CloudletDeploymentCreator()
        override fun createFromParcel(parcel: Parcel): CloudletDeploymentProxy {
            return CloudletDeploymentProxy(parcel)
        }

        override fun newArray(size: Int): Array<CloudletDeploymentProxy?> {
            return arrayOfNulls(size)
        }
    }
}