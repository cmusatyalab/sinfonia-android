package edu.cmu.cs.sinfonia.util


class TunnelException(private val reason: Reason, vararg format: Any?) : Exception() {
    private val formatArray: Array<out Any?> = format

    fun getFormat(): Array<out Any?> {
        return formatArray
    }

    fun getReason(): Reason {
        return reason
    }
    enum class Reason {
        ALREADY_EXIST
    }
}