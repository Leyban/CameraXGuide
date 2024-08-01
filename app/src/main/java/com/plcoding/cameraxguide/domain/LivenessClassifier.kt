package com.plcoding.cameraxguide.domain

import android.graphics.Bitmap

interface LivenessClassifier {
    fun classify(bitmap: Bitmap, rotation: Int): List<Classification>
}