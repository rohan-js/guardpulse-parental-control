package com.guardpulse.parentcontrol.parent

import android.app.Application
import com.guardpulse.parentcontrol.shared.FirebaseRuntime

class ParentControlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseRuntime.initialize(this)
    }
}
