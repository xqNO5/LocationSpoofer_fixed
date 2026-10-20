package com.vincenthzr.locationspoofer.viewmodel

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.DrivePath
import com.amap.api.services.route.RouteSearch
import com.vincenthzr.locationspoofer.data.model.RouteBatchPlanner
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import com.vincenthzr.locationspoofer.utils.GeoMath

/**
 * 高德真实道路匹配。路线必须经过用户点选的每一个途经点，因此统一使用驾车 API：
 *
 * - 驾车 [RouteSearch.DriveRouteQuery] 单次最多 6 个途经点；
 * - 超过 6 个时按 [RouteBatchPlanner] 分批请求，相邻批次共享边界点；
 * - 某个批次失败时退化为逐段请求，保证每个用户途经点仍然作为起终点参与算路；
 * - 不使用步行 API：它只有起终点、不支持途经点，无法满足"强依赖途经点"的要求。
 */
internal object AmapRoutePlanner {

    fun fetch(context: Context, waypoints: List<RoutePoint>): List<RoutePoint> {
        if (waypoints.size < 2) return waypoints

        com.amap.api.services.core.ServiceSettings.updatePrivacyShow(context, true, true)
        com.amap.api.services.core.ServiceSettings.updatePrivacyAgree(context, true)
        val routeSearch = RouteSearch(context)

        val result = ArrayList<RoutePoint>()
        for (batch in RouteBatchPlanner.drivingBatches(waypoints.size)) {
            val segment = fetchDriveChunkWithFallback(routeSearch, waypoints, batch.first, batch.last)
            appendSegment(result, segment)
        }
        return result
    }

    private fun fetchDriveChunkWithFallback(
        routeSearch: RouteSearch,
        points: List<RoutePoint>,
        startIndex: Int,
        endIndex: Int
    ): List<RoutePoint> {
        return try {
            fetchDriveChunk(routeSearch, points, startIndex, endIndex)
        } catch (first: Exception) {
            if (endIndex <= startIndex + 1) throw first
            // 批次太大或中间某个途经点不可达时，退回逐段请求，至少保留能算出来的部分。
            val fallback = ArrayList<RoutePoint>()
            for (i in startIndex until endIndex) {
                appendSegment(fallback, fetchDriveChunk(routeSearch, points, i, i + 1))
            }
            fallback
        }
    }

    private fun fetchDriveChunk(
        routeSearch: RouteSearch,
        points: List<RoutePoint>,
        startIndex: Int,
        endIndex: Int
    ): List<RoutePoint> {
        val fromAndTo = RouteSearch.FromAndTo(toLatLon(points[startIndex]), toLatLon(points[endIndex]))
        val via = (startIndex + 1 until endIndex).map { toLatLon(points[it]) }
        val query = RouteSearch.DriveRouteQuery(
            fromAndTo,
            RouteSearch.DrivingDefault,
            via.ifEmpty { null },
            null,
            ""
        )
        val result = routeSearch.calculateDriveRoute(query)
        val path = result?.paths?.firstOrNull()
            ?: throw IllegalStateException("AMap returned no driving path")
        return drivePathPoints(path)
    }

    private fun drivePathPoints(path: DrivePath): List<RoutePoint> {
        val points = ArrayList<RoutePoint>()
        val stepEndIndices = ArrayList<Int>()
        for (step in path.steps.orEmpty()) {
            for (polyline in step.polyline.orEmpty()) {
                points.add(RoutePoint(polyline.latitude, polyline.longitude))
            }
            if (points.isNotEmpty()) stepEndIndices.add(points.lastIndex)
        }

        // 高德只返回红绿灯数量，不返回精确坐标；沿用原有策略，把等待点随机放到各段末尾。
        val trafficLights = path.totalTrafficlights
        if (trafficLights > 0 && stepEndIndices.isNotEmpty()) {
            stepEndIndices.shuffled().take(trafficLights).forEach { index ->
                points[index] = points[index].copy(waitSec = 15.0)
            }
        }
        return points
    }

    private fun appendSegment(target: MutableList<RoutePoint>, segment: List<RoutePoint>) {
        if (segment.isEmpty()) return
        if (target.isEmpty()) {
            target.addAll(segment)
            return
        }
        val last = target.last()
        val first = segment.first()
        val samePoint = GeoMath.distance(LatLng(last.lat, last.lng), LatLng(first.lat, first.lng)) < 1.0
        target.addAll(if (samePoint) segment.drop(1) else segment)
    }

    private fun toLatLon(point: RoutePoint): LatLonPoint = LatLonPoint(point.lat, point.lng)
}
