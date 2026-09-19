package com.example.logkeeper

import android.app.Application

class PwaLoaderApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    LogKeeperCatcher.install(this)
  }
}
