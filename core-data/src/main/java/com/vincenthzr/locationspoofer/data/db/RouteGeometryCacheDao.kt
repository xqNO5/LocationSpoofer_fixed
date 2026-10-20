package com.vincenthzr.locationspoofer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RouteGeometryCacheDao {

    @Query("SELECT * FROM route_geometry_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun get(cacheKey: String): RouteGeometryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RouteGeometryCacheEntity)

    @Query("UPDATE route_geometry_cache SET lastUsedAt = :usedAt WHERE cacheKey = :cacheKey")
    suspend fun touch(cacheKey: String, usedAt: Long)

    /** 只保留最近使用的 [keep] 条，避免缓存无限增长。 */
    @Query(
        """
        DELETE FROM route_geometry_cache
        WHERE cacheKey NOT IN (
            SELECT cacheKey FROM route_geometry_cache
            ORDER BY lastUsedAt DESC
            LIMIT :keep
        )
        """
    )
    suspend fun trim(keep: Int)
}
