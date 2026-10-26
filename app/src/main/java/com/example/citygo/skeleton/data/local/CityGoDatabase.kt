package com.example.citygo.skeleton.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/* =====================================================================================
 * App 本地数据库（Room）
 *
 * 当前仅承载景点 POI 缓存（spot_cache 表）；后续其他模块需要本地持久化时，
 * 在 entities 中追加 Entity、在抽象方法中追加 DAO，并升级 version 即可。
 * ===================================================================================== */

@Database(
    entities = [SpotCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CityGoDatabase : RoomDatabase() {

    /** 景点缓存 DAO */
    abstract fun spotCacheDao(): SpotCacheDao

    companion object {
        @Volatile
        private var instance: CityGoDatabase? = null

        /** 单例获取（首次访问时建库，城市景点缓存量小，无需复杂迁移策略） */
        fun get(context: Context): CityGoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CityGoDatabase::class.java,
                    "citygo.db",
                ).build().also { instance = it }
            }
    }
}
