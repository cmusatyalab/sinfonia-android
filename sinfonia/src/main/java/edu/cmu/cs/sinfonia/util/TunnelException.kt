package edu.cmu.cs.sinfonia.util

import android.os.Parcel
import android.os.Parcelable


class TunnelException(private val reason: Reason, vararg format: Any?) : Exception(), Parcelable {
    private val formatArray: Array<out Any?> = format

    constructor(parcel: Parcel) : this(
        Reason.valueOf(parcel.readString() ?: Reason.UNKNOWN.name),
        *parcel.readArray(ClassLoader.getSystemClassLoader()) as Array<out Any?>
    )

    fun getFormat(): Array<out Any?> {
        return formatArray
    }

    fun getReason(): Reason {
        return reason
    }
    enum class Reason {
        UNKNOWN,
        ALREADY_EXIST,
        INVALID_NAME,
        NOT_FOUND
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(reason.name)
        parcel.writeArray(formatArray)
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