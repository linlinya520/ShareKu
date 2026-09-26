package com.linjing.shareku.server

import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val ipAddress: String,
    val isWifi: Boolean,
    val isEthernet: Boolean
)

class NetworkUtils {

    fun getAllInterfaces(): List<NetworkInterfaceInfo> {
        val results = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (iface in interfaces.toList()) {
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses.toList()
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        results.add(
                            NetworkInterfaceInfo(
                                name = iface.name,
                                displayName = getDisplayName(iface.name),
                                ipAddress = addr.hostAddress ?: continue,
                                isWifi = iface.name.startsWith("wlan"),
                                isEthernet = iface.name.startsWith("eth") || iface.name.startsWith("en")
                            )
                        )
                        break // one IPv4 per interface
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun getDisplayName(name: String): String {
        return when {
            name.startsWith("wlan") -> "Wi-Fi ($name)"
            name.startsWith("eth") || name.startsWith("en") -> "Ethernet ($name)"
            name.startsWith("rndis") || name.contains("usb") -> "USB Tethering ($name)"
            name.startsWith("dummy") -> "Dummy ($name)"
            name.startsWith("p2p") -> "Wi-Fi Direct ($name)"
            else -> name
        }
    }

    fun getPreferredInterface(preferred: String): NetworkInterfaceInfo? {
        val all = getAllInterfaces()
        if (preferred == "auto") {
            // 优先局域网可用接口：WiFi → 有线/USB 共享 → 排除蜂窝/VPN 虚接口 → 兜底
            return all.firstOrNull { it.isWifi }
                ?: all.firstOrNull { it.isEthernet }
                ?: all.firstOrNull { it.name.startsWith("rndis") || it.name.contains("usb") }
                ?: all.firstOrNull {
                    !it.name.startsWith("rmnet") && !it.name.startsWith("tun") &&
                        !it.name.startsWith("ppp") && !it.name.startsWith("dummy")
                }
                ?: all.firstOrNull()
        }
        return all.find { it.name == preferred }
    }

    fun getPrimaryIpAddress(): String? {
        return getPreferredInterface("auto")?.ipAddress
    }
}