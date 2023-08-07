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
package edu.cmu.cs.sinfonia.util

import android.os.Parcel
import android.os.Parcelable

/**
 * This class handles the tunnel exception that can be propagated through IPC
 *
 * @property reason
 * @property formatArray
 */
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
        NOT_FOUND,
        ALREADY_UP,
        ALREADY_DOWN,
        ALREADY_TOGGLE,
        UNAUTHORIZED_ACCESS
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