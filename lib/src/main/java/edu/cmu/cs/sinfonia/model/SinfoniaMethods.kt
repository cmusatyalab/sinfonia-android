package edu.cmu.cs.sinfonia.model

import android.content.Intent

interface SinfoniaMethods {
    fun fetch(intent: Intent)
    fun deploy(intent: Intent)
    fun cleanup(intent: Intent)
}