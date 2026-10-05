package com.logiclinear.reading

import android.app.Application

/** 앱 프로세스마다 하나. [AppContainer]를 소유한다. */
class ReadingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
