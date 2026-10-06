package com.example.habit.coach

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** A local precheck; transport must still handle loss during its call. No network request. */
class CoachConnection(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    fun available(): Boolean = connectivity?.let { manager ->
        manager.getNetworkCapabilities(manager.activeNetwork)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } ?: false
    } ?: false
}
