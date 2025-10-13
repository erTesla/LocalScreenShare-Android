package com.helium4.localscreenshare

import android.content.Intent

// 🔒 Holds MediaProjection permission data safely between SenderActivity and Service
object ProjectionHolder {
    var resultCode: Int = -1
    var data: Intent? = null
}
