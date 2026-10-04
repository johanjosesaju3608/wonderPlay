package com.wonderplay.source
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
internal fun connected(context:Context):Boolean {
 val manager=context.getSystemService(ConnectivityManager::class.java)
 return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
}
