package com.grafitto.laudate

import android.app.Application
import com.grafitto.laudate.di.AppContainer

class CanticoraApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
