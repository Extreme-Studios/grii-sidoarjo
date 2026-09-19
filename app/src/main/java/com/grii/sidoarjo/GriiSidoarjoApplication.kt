package com.grii.sidoarjo

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.grii.sidoarjo.updates.ContentUpdateScheduler

class GriiSidoarjoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Only banner ads are enabled. App Open and rewarded ads are disabled.
        MobileAds.initialize(this) {}
        ContentUpdateScheduler.ensureScheduled(this)
    }
}
