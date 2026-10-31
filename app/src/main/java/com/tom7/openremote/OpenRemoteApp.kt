package com.tom7.openremote

import android.app.Application
import com.tom7.openremote.data.AppJson
import com.tom7.openremote.data.ConnectionStore
import com.tom7.openremote.data.OpenCodeApi
import com.tom7.openremote.data.SavedConnection
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class OpenRemoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        OpenRemoteNotificationManager.createNotificationChannels(this)
    }
}

class AppContainer(application: Application) {
    val store = ConnectionStore(application)
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun api(connection: SavedConnection): OpenCodeApi =
        OpenCodeApi(connection.url, connection.password, client)
}
