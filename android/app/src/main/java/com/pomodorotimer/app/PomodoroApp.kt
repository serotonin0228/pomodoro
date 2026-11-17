package com.pomodorotimer.app

import android.app.Application
import com.pomodorotimer.app.domain.Clock

class PomodoroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // A single clock shared by every screen and service; cloud sync may adjust the correction.
        Clock.setCorrection(0L)
    }
}
