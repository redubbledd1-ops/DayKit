package com.dd.daykit.sound

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.UUID

/**
 * Manager for HTTP server settings and lifecycle
 * Handles opt-in, API key generation, and server control
 */
object HttpServerManager {
    private const val TAG = "HttpServerManager"
    private const val PREFS_NAME = "http_server_settings"
    
    // Settings keys
    private const val KEY_ENABLED = "http_server_enabled"
    private const val KEY_API_KEY = "http_server_api_key"
    private const val KEY_PORT = "http_server_port"
    private const val KEY_REMOTE_ACCESS_ENABLED = "http_server_remote_access_enabled"
    
    // Default values
    private const val DEFAULT_PORT = 8765
    
    /**
     * Get SharedPreferences
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Check if HTTP server is enabled (opt-in)
     */
    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, false)
    }
    
    /**
     * Check if remote access (Tailscale) is enabled
     */
    fun isRemoteAccessEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REMOTE_ACCESS_ENABLED, false)
    }
    
    /**
     * Enable or disable remote access (Tailscale)
     */
    fun setRemoteAccessEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REMOTE_ACCESS_ENABLED, enabled).apply()
        Log.i(TAG, "Remote access (Tailscale) ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Enable or disable HTTP server
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        
        if (enabled) {
            // Generate API key if not exists
            if (getApiKey(context).isEmpty()) {
                regenerateApiKey(context)
            }
            startServer(context)
        } else {
            stopServer()
        }
        
        Log.i(TAG, "HTTP server ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Get API key (generates if not exists)
     */
    fun getApiKey(context: Context): String {
        val prefs = getPrefs(context)
        var apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        
        if (apiKey.isEmpty()) {
            apiKey = generateApiKey()
            prefs.edit().putString(KEY_API_KEY, apiKey).apply()
            Log.i(TAG, "Generated new API key")
        }
        
        return apiKey
    }
    
    /**
     * Regenerate API key
     */
    fun regenerateApiKey(context: Context): String {
        val apiKey = generateApiKey()
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
        
        // Restart server if running
        if (isEnabled(context)) {
            stopServer()
            startServer(context)
        }
        
        Log.i(TAG, "Regenerated API key")
        return apiKey
    }
    
    /**
     * Get server port
     */
    fun getPort(context: Context): Int {
        return getPrefs(context).getInt(KEY_PORT, DEFAULT_PORT)
    }
    
    /**
     * Set server port
     */
    fun setPort(context: Context, port: Int) {
        getPrefs(context).edit().putInt(KEY_PORT, port).apply()
        
        // Restart server if running
        if (isEnabled(context)) {
            stopServer()
            startServer(context)
        }
        
        Log.i(TAG, "Server port changed to $port")
    }
    
    /**
     * Start HTTP server
     */
    fun startServer(context: Context) {
        if (!isEnabled(context)) {
            Log.w(TAG, "Cannot start server: not enabled")
            return
        }
        
        val apiKey = getApiKey(context)
        val port = getPort(context)
        
        val server = SoundHttpServer.start(context, apiKey, port)
        if (server != null) {
            Log.i(TAG, "HTTP server started successfully on port $port")
        } else {
            Log.e(TAG, "Failed to start HTTP server")
        }
    }
    
    /**
     * Stop HTTP server
     */
    fun stopServer() {
        SoundHttpServer.stop()
        Log.i(TAG, "HTTP server stopped")
    }
    
    /**
     * Check if server is running
     */
    fun isRunning(): Boolean {
        return SoundHttpServer.isRunning()
    }
    
    /**
     * Get server URL for local network
     */
    fun getServerUrl(context: Context): String {
        val port = getPort(context)
        return "http://<device-ip>:$port"
    }
    
    /**
     * Get API key header for requests
     */
    fun getApiKeyHeader(context: Context): Pair<String, String> {
        return "X-API-Key" to getApiKey(context)
    }
    
    /**
     * Generate a secure API key
     */
    private fun generateApiKey(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }
    
    /**
     * Get device IP address (for display purposes)
     * Returns local WiFi IP if available
     */
    fun getDeviceIpAddress(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) 
                as? android.net.wifi.WifiManager
            
            wifiManager?.connectionInfo?.let { info ->
                val ipAddress = info.ipAddress
                if (ipAddress != 0) {
                    String.format(
                        "%d.%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                        ipAddress shr 24 and 0xff
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
            null
        }
    }
    
    /**
     * Get all available IP addresses (including Tailscale)
     * Returns map of interface name to IP address
     */
    fun getAllIpAddresses(): Map<String, String> {
        val addresses = mutableMapOf<String, String>()
        
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }
                
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    
                    // Only IPv4
                    if (inetAddress is java.net.Inet4Address) {
                        val ip = inetAddress.hostAddress ?: continue
                        
                        // Categorize by IP range
                        val label = when {
                            ip.startsWith("100.") -> "Tailscale" // Tailscale CGNAT range
                            ip.startsWith("192.168.") -> "WiFi (Local)"
                            ip.startsWith("10.") -> "Private Network"
                            ip.startsWith("172.") -> "Private Network"
                            else -> networkInterface.displayName ?: "Other"
                        }
                        
                        addresses["$label - $ip"] = ip
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all IP addresses", e)
        }
        
        return addresses
    }
    
    /**
     * Detect if Tailscale is active
     * Checks for IP in 100.x.x.x range (CGNAT)
     */
    fun getTailscaleIp(): String? {
        return try {
            val allIps = getAllIpAddresses()
            allIps.entries.find { it.key.contains("Tailscale") || it.value.startsWith("100.") }?.value
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting Tailscale IP", e)
            null
        }
    }
    
    /**
     * Get full server URL with IP
     */
    fun getFullServerUrl(context: Context): String {
        val ip = getDeviceIpAddress(context) ?: "<device-ip>"
        val port = getPort(context)
        return "http://$ip:$port"
    }
    
    /**
     * Check if WiFi is connected
     */
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as? android.net.ConnectivityManager
            
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            
            val isConnected = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            
            Log.d(TAG, "WiFi connected: $isConnected (network: $network, capabilities: ${capabilities?.toString()})")
            isConnected
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi connection", e)
            false
        }
    }
    
    /**
     * Get WiFi network name (SSID)
     * Requires ACCESS_FINE_LOCATION permission on Android 8.1+
     */
    fun getWifiNetworkName(context: Context): String? {
        return try {
            // Check location permission (required for SSID on Android 8.1+)
            val hasLocationPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            
            if (!hasLocationPermission) {
                Log.w(TAG, "WiFi SSID unavailable: Location permission not granted")
                return "<location permission required>"
            }
            
            // Check if location services are enabled
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            val isLocationEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                                   locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
            
            if (!isLocationEnabled) {
                Log.w(TAG, "WiFi SSID unavailable: Location services disabled")
                return "<location services disabled>"
            }
            
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) 
                as? android.net.wifi.WifiManager
            
            val connectionInfo = wifiManager?.connectionInfo
            val ssid = connectionInfo?.ssid?.replace("\"", "")
            
            Log.d(TAG, "WiFi SSID: $ssid (permission: $hasLocationPermission, location enabled: $isLocationEnabled)")
            
            if (ssid == "<unknown ssid>" || ssid.isNullOrBlank()) {
                Log.w(TAG, "WiFi SSID is unknown - check location permission and services")
                return null
            }
            
            ssid
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting WiFi SSID - location permission missing", e)
            "<location permission required>"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi SSID", e)
            null
        }
    }
    
    /**
     * Get server status for debugging (includes Tailscale info)
     */
    fun getServerStatus(context: Context): ServerStatus {
        val enabled = isEnabled(context)
        val running = isRunning()
        val wifiConnected = isWifiConnected(context)
        val deviceIp = getDeviceIpAddress(context)
        val port = getPort(context)
        val apiKey = getApiKey(context)
        val remoteAccessEnabled = isRemoteAccessEnabled(context)
        val tailscaleIp = getTailscaleIp()
        val allIps = getAllIpAddresses()
        
        val status = ServerStatus(
            enabled = enabled,
            running = running,
            wifiConnected = wifiConnected,
            deviceIp = deviceIp,
            port = port,
            hasApiKey = apiKey.isNotEmpty(),
            remoteAccessEnabled = remoteAccessEnabled,
            tailscaleIp = tailscaleIp,
            allAvailableIps = allIps
        )
        
        Log.i(TAG, "Server status: $status")
        return status
    }
    
    data class ServerStatus(
        val enabled: Boolean,
        val running: Boolean,
        val wifiConnected: Boolean,
        val deviceIp: String?,
        val port: Int,
        val hasApiKey: Boolean,
        val remoteAccessEnabled: Boolean = false,
        val tailscaleIp: String? = null,
        val allAvailableIps: Map<String, String> = emptyMap()
    ) {
        fun isFullyOperational(): Boolean {
            return enabled && running && deviceIp != null && hasApiKey
        }
        
        fun isRemoteAccessReady(): Boolean {
            return isFullyOperational() && remoteAccessEnabled && tailscaleIp != null
        }
        
        fun getIssues(): List<String> {
            val issues = mutableListOf<String>()
            if (!enabled) issues.add("Server niet ingeschakeld")
            if (!running) issues.add("Server draait niet")
            if (!wifiConnected && tailscaleIp == null) issues.add("Geen netwerk verbinding")
            if (deviceIp == null && tailscaleIp == null) issues.add("Geen IP adres")
            if (!hasApiKey) issues.add("Geen API key")
            if (remoteAccessEnabled && tailscaleIp == null) issues.add("Tailscale niet actief")
            return issues
        }
        
        fun getPrimaryIp(): String? {
            return tailscaleIp ?: deviceIp
        }
    }
}
