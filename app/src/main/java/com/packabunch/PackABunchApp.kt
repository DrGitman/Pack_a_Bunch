package com.packabunch

import android.app.Application

class PackABunchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // No-op without a RevenueCat key, so builds without billing stay honest.
        com.packabunch.billing.Billing.configure(this)
    }
}
