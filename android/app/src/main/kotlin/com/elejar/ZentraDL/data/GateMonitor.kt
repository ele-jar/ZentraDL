package com.elejar.ZentraDL.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.elejar.ZentraDL.domain.NetState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-lifetime device-state feed for the queue gate (P3d). */
@Singleton
class GateMonitor @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val _state = MutableStateFlow(read(ctx))
    val state: StateFlow<NetState> = _state.asStateFlow()

    init {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
        })
        ctx.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) = refresh()
            },
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
    }

    private fun refresh() {
        _state.value = read(ctx)
    }

    companion object {
        fun read(ctx: Context): NetState {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
            val bm = ctx.getSystemService(BatteryManager::class.java)
            return NetState(connected, unmetered, bm?.isCharging == true)
        }
    }
}
