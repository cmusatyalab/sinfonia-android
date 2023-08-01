package edu.cmu.cs.sinfonia.util

import android.os.Parcel
import android.os.Parcelable


class TunnelException(private val reason: Reason, vararg format: Any?) : Exception(), Parcelable {
    private val formatArray: Array<out Any?> = format

    constructor(parcel: Parcel) : this(
        TODO("reason"),
        TODO("format")
    ) {
    }

    fun getFormat(): Array<out Any?> {
        return formatArray
    }

    fun getReason(): Reason {
        return reason
    }
    enum class Reason {
        ALREADY_EXIST
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {

    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TunnelException> {
        override fun createFromParcel(parcel: Parcel): TunnelException {
            return TunnelException(parcel)
        }

        override fun newArray(size: Int): Array<TunnelException?> {
            return arrayOfNulls(size)
        }
    }
}