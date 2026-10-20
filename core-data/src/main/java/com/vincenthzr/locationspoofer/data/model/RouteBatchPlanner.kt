package com.vincenthzr.locationspoofer.data.model

/**
 * 把一条多点路线切成若干次高德驾车请求。
 *
 * 高德 [com.amap.api.services.route.RouteSearch.DriveRouteQuery] 的途经点上限是 6，
 * 因此每个区间最多包含 起点 + 6 个途经点 + 终点。相邻区间共享边界点，拼接时去重。
 */
object RouteBatchPlanner {

    const val MAX_DRIVE_VIA_POINTS = 6

    /** 返回闭区间下标，例如 20 个点 -> [0..7, 7..14, 14..19]。 */
    fun drivingBatches(waypointCount: Int, maxViaPoints: Int = MAX_DRIVE_VIA_POINTS): List<IntRange> {
        if (waypointCount < 2) return emptyList()
        val span = maxViaPoints.coerceAtLeast(1) + 1
        val result = ArrayList<IntRange>()
        var start = 0
        while (start < waypointCount - 1) {
            val end = minOf(start + span, waypointCount - 1)
            result += start..end
            start = end
        }
        return result
    }
}
