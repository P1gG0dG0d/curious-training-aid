package com.haoqi.training

import android.app.Application
import com.haoqi.training.di.AppContainer
import com.haoqi.training.timer.RestTimerService

class App : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        RestTimerService.createChannel(this)
    }
}
