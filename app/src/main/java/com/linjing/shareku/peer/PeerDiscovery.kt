package com.linjing.shareku.peer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress

data class PeerDevice(
    val name: String,
    val host: String,
    val port: Int,
    val serviceName: String
) {
    val displayName: String get() = name.ifBlank { host }
}

/**
 * NSD-based peer discovery: register own service + scan for other ShareKu devices.
 * Scanning auto-stops after [SCAN_TIMEOUT_MS] to save battery.
 */
class PeerDiscovery(private val context: Context) {

    companion object {
        const val SERVICE_TYPE = "_shareku._tcp."
        /** How long a single discovery session lasts before auto-stopping (battery). */
        const val SCAN_TIMEOUT_MS = 12_000L
    }

    private val nsdManager: NsdManager? by lazy {
        try { context.getSystemService(Context.NSD_SERVICE) as? NsdManager } catch (_: Exception) { null }
    }

    /** Currently discovered peers. */
    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val discovered = mutableMapOf<String, PeerDevice>() // serviceName → PeerDevice
    private var scanJob: Job? = null
    private var registeredService: NsdServiceInfo? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /** Register ShareKu service so other devices can discover us. */
    fun registerService(port: Int) {
        val nsd = nsdManager ?: return
        unregisterService()
        val deviceModel = android.os.Build.MODEL ?: "ShareKu"
        val info = NsdServiceInfo().apply {
            serviceName = "ShareKu-$deviceModel"
            serviceType = SERVICE_TYPE
            this.port = port
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                registeredService = serviceInfo
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) { registeredService = null }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
        }
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (_: Exception) {}
    }

    /** Stop advertising ourselves. */
    fun unregisterService() {
        registrationListener?.let { nsdManager?.unregisterService(it) }
        registrationListener = null
        registeredService = null
    }

    /** Start scanning for other ShareKu devices. Stops automatically after [SCAN_TIMEOUT_MS]. */
    fun startScan(onFound: (PeerDevice) -> Unit = {}) {
        val nsd = nsdManager ?: return
        stopScan()
        discovered.clear()
        _peers.value = emptyList()
        _isScanning.value = true

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _isScanning.value = false
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String?) {}
            override fun onDiscoveryStopped(serviceType: String?) {
                _isScanning.value = false
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                serviceInfo ?: return
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                // Resolve to get the actual host/port
                try {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                        override fun onServiceResolved(resolved: NsdServiceInfo?) {
                            resolved ?: return
                            val host = resolved.host?.hostAddress ?: return
                            val port = resolved.port
                            if (port <= 0) return
                            val peer = PeerDevice(
                                name = resolved.serviceName ?: "Unknown",
                                host = host,
                                port = port,
                                serviceName = resolved.serviceName
                            )
                            // Skip self
                            val localIps = getLocalIps()
                            if (host in localIps) return
                            discovered[resolved.serviceName] = peer
                            _peers.value = discovered.values.toList()
                            onFound(peer)
                        }
                    })
                } catch (_: Exception) {}
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.serviceName?.let { name ->
                    discovered.remove(name)
                    _peers.value = discovered.values.toList()
                }
            }
        }

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (_: Exception) {
            _isScanning.value = false
        }

        scanJob = CoroutineScope(Dispatchers.Default).launch {
            delay(SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    /** Stop the active discovery session. */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        discoveryListener = null
        _isScanning.value = false
    }

    /** Manually trigger a fresh scan. */
    fun rescan(onFound: (PeerDevice) -> Unit = {}) {
        startScan(onFound)
    }

    /** Get local IP addresses to filter out self from scan results. */
    private fun getLocalIps(): Set<String> {
        val ips = mutableSetOf<String>()
        try {
            InetAddress.getByName("127.0.0.1")
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                iface.inetAddresses.toList().forEach { addr ->
                    ips.add(addr.hostAddress ?: return@forEach)
                }
            }
        } catch (_: Exception) {}
        ips.add("127.0.0.1")
        return ips
    }
}