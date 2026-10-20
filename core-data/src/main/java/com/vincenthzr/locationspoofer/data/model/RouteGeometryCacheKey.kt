package com.vincenthzr.locationspoofer.data.model

import java.security.MessageDigest
import java.util.Locale

/**
 * 真实道路匹配结果的缓存键：只由"用户点选的途经点 + 路网类型"决定。
 *
 * 坐标保留 6 位小数（约 0.1 米），避免同一条路线因为浮点尾数不同而反复请求 API。
 * 版本号参与哈希，将来匹配算法或数据格式变化时可以整体失效旧缓存。
 */
object RouteGeometryCacheKey {
    private const val VERSION = "route-cache-v1"

    fun of(waypoints: List<RoutePoint>, profile: String): String {
        val canonical = buildString {
            append(VERSION).append('|').append(profile).append('|')
            waypoints.forEach { point ->
                append(String.format(Locale.US, "%.6f,%.6f;", point.lat, point.lng))
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
