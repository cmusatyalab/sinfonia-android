package edu.cmu.cs.sinfonia.model

import android.content.Intent

interface SinfoniaMethods {
    fun deploy(intent: Intent)

    fun cleanup(): Boolean
}