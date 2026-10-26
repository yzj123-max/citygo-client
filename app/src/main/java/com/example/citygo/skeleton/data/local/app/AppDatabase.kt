package com.example.citygo.skeleton.data.local.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.citygo.skeleton.data.local.ai.AiSpotDao
import com.example.citygo.skeleton.data.local.ai.AiSpotEntity
import com.example.citygo.skeleton.data.local.ai.AiTravelPlanDao
import com.example.citygo.skeleton.data.local.ai.AiTravelPlanEntity
import com.example.citygo.skeleton.data.local.chat.ChatHistoryDao
import com.example.citygo.skeleton.data.local.chat.ChatHistoryEntity
import com.example.citygo.skeleton.data.datastore.AccountStorage

/* =====================================================================================
 * App 业务库（Room）—— citygo_app.db
 *
 * 承载跨底部 Tab 共享的用户业务数据 10 张表：
 * 宠物 / 行程主表 / 行程子项 / 收藏景点 / 收藏帖子 / 浏览记录 / 日记 / 社区帖子
 * / AI 攻略主表（v2 新增）/ AI 攻略地点子表（v2 新增）。
 *
 * 注意：与只缓存高德 POI 的 citygo.db（CityGoDatabase）是两个独立库文件，
 * 互不影响——高德导航 / 天气预报相关代码完全不会被本库改动触及。
 *
 * 架构分层：页面 / ViewModel 只依赖 Repository 接口，不直接拿数据库实例。
 * ===================================================================================== */

@Database(
    entities = [
        PetEntity::class,
        TravelPlanEntity::class,
        PlanItemEntity::class,
        SpotCollectEntity::class,
        PostCollectEntity::class,
        BrowseHistoryEntity::class,
        DiaryEntity::class,
        PostEntity::class,
        // v2 新增：AI 大模型生成攻略的本地缓存（同城市命中缓存则不再请求大模型）
        AiTravelPlanEntity::class,
        AiSpotEntity::class,
        // v3 新增：AI 助手聊天记录（逻辑绑定行程 id，只新增表，不改任何旧表）
        ChatHistoryEntity::class,
        TripSyncEntity::class,
        ActiveTripEntity::class,
    ],
    version = AppDatabase.DB_VERSION,
    exportSchema = false,
)
@TypeConverters(AppConverters::class)
abstract class AppDatabase : RoomDatabase() {

    /** 宠物档案 */
    abstract fun petDao(): PetDao

    /** 行程主表 */
    abstract fun travelPlanDao(): TravelPlanDao

    /** 行程子项 */
    abstract fun planItemDao(): PlanItemDao

    /** 收藏景点 */
    abstract fun spotCollectDao(): SpotCollectDao

    /** 收藏帖子 */
    abstract fun postCollectDao(): PostCollectDao

    /** 浏览记录 */
    abstract fun browseHistoryDao(): BrowseHistoryDao

    /** 旅行日记 */
    abstract fun diaryDao(): DiaryDao

    /** 社区帖子 */
    abstract fun postDao(): PostDao

    /** AI 攻略主表缓存 */
    abstract fun aiTravelPlanDao(): AiTravelPlanDao

    /** AI 攻略地点子表缓存 */
    abstract fun aiSpotDao(): AiSpotDao

    /** AI 助手聊天记录 */
    abstract fun chatHistoryDao(): ChatHistoryDao
    abstract fun tripSyncDao(): TripSyncDao

    companion object {
        /**
         * 当前数据库版本号：表结构变更必须 +1 并提供迁移。
         * v1：初始 8 张业务表；
         * v2：新增 ai_travel_plan / ai_spot 两张 AI 攻略缓存表（仅新增，不改旧表）；
         * v3：新增 chat_history 聊天记录表（仅新增，不改旧表）。
         * v4：plan_item 增加 coverUrl 列（点位真实实拍封面，可空；旧行自动为 NULL）。
         * v5：chat_history 增加 planJson / proposalStatus 两列（AI 助手行程提议的
         *      方案原文与确认状态，均可空；旧消息两列 NULL 等价于「无提议」）。
         * v6：plan_item 增加 ticket / avgPrice / signatureDish / source 四列
         *      （后端数据库预置点位扩展属性：门票 / 人均 / 特色菜 + 来源标记，均可空；
         *      旧行程前三者 NULL 等价于「无该字段」，source NULL 等价于 AI 拓展点）。
         * v7：plan_item 增加 photoScene / travelTip / coordMocked 三列
         *      （拍照场景 / 出行要点原文 + 高德未命中时的兜底坐标标记，前两者可空）。
         * v8：plan_item 增加 timeSlot 列（时间段原文，如「上午9:00-12:00」「午餐12:30」，可空）。
         * v9：新增 trip_sync_state / trip_sync_active，持久化多行程、离线修订及待重试请求。
         */
        const val DB_VERSION = 9

        /** 当前行程的展示缓存槽位；历史行程及其对话按 tripId 保存在 trip_sync_state。 */
        const val CURRENT_PLAN_ID = 1L

        /**
         * v1 → v2 迁移：新建 AI 攻略缓存主子表与索引。
         * 只做 CREATE TABLE / INDEX，旧 8 张表数据原样保留（宠物 / 行程 / 收藏 / 日记等不丢）。
         * SQL 与实体声明严格对应：ai_spot.planId 外键级联删除，desc 为 SQLite 关键字需加反引号。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_travel_plan` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `city` TEXT NOT NULL,
                        `generateTime` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_travel_plan_city` " +
                        "ON `ai_travel_plan` (`city`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_spot` (
                        `planId` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `desc` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `isPetFriendly` INTEGER NOT NULL,
                        `imageUrlsJson` TEXT NOT NULL,
                        `tip` TEXT NOT NULL,
                        PRIMARY KEY(`planId`, `sortOrder`),
                        FOREIGN KEY(`planId`) REFERENCES `ai_travel_plan`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_spot_planId` " +
                        "ON `ai_spot` (`planId`)",
                )
            }
        }

        /**
         * v2 → v3 迁移：新建 AI 助手聊天记录表与索引。
         * 只做 CREATE TABLE / INDEX，旧 10 张表数据原样保留（行程 / 收藏 / AI 缓存等不丢）；
         * SQL 与 ChatHistoryEntity 声明严格对应（Boolean 列 isUser 落 INTEGER，
         * planId 仅做逻辑绑定并加索引，不设外键以免受行程写入顺序影响）。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_history` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `planId` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `isUser` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_history_planId` " +
                        "ON `chat_history` (`planId`)",
                )
            }
        }

        /**
         * v3 → v4 迁移：plan_item 新增可空封面列 coverUrl。
         * 仅 ALTER TABLE ADD COLUMN（SQLite 默认填 NULL），旧行程 / 收藏 / AI 缓存 / 聊天记录全部保留；
         * Room schema 与 PlanItemEntity 新增的 `coverUrl: String?` 严格对应。
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plan_item` ADD COLUMN `coverUrl` TEXT")
            }
        }

        /**
         * v4 → v5 迁移：chat_history 新增行程提议两列（方案 JSON 原文 + 确认状态）。
         * 仅 ALTER TABLE ADD COLUMN（SQLite 默认填 NULL），旧聊天记录 / 行程 / 封面 /
         * 收藏 / AI 缓存全部保留；Room schema 与 ChatHistoryEntity 新增字段严格对应。
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `planJson` TEXT")
                db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `proposalStatus` TEXT")
            }
        }

        /**
         * v5 → v6 迁移：plan_item 新增数据库预置点位扩展属性 4 列。
         * 仅 ALTER TABLE ADD COLUMN（SQLite 默认填 NULL），旧行程 / 收藏 / AI 缓存 / 聊天记录
         * 全部保留；ticket / avgPrice / signatureDish 为 NULL 表示无该字段（UI 不渲染），
         * source 为 NULL 等价于 AI 拓展点。Room schema 与 PlanItemEntity 新增字段严格对应。
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plan_item` ADD COLUMN `ticket` TEXT")
                db.execSQL("ALTER TABLE `plan_item` ADD COLUMN `avgPrice` TEXT")
                db.execSQL("ALTER TABLE `plan_item` ADD COLUMN `signatureDish` TEXT")
                db.execSQL("ALTER TABLE `plan_item` ADD COLUMN `source` TEXT")
            }
        }

        /**
         * v6 → v7 迁移：plan_item 新增拍照场景 / 出行要点 / 兜底坐标标记 3 列。
         * 仅 ALTER TABLE ADD COLUMN（SQLite 前两列默认 NULL、coordMocked 默认 0），
         * 旧行程 / 收藏 / AI 缓存 / 聊天记录全部保留。
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plan_item` ADD COLUMN `photoScene` TEXT")
                db.execSQL("ALTER TABLE `plan_item` ADD COLUMN `travelTip` TEXT")
                db.execSQL("ALTER TABLE `plan_item` ADD COLUMN `coordMocked` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v7 → v8 迁移：plan_item 新增时间段列（可空，旧行 NULL 等价于「无时间信息」）。
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plan_item` ADD COLUMN `timeSlot` TEXT")
            }
        }

        /** 只新增同步日志及当前行程指针；旧行程、聊天及其他业务表不变。 */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `trip_sync_state` (
                    `tripId` TEXT NOT NULL PRIMARY KEY, `title` TEXT NOT NULL,
                    `documentJson` TEXT NOT NULL, `remoteVersion` INTEGER NOT NULL,
                    `localRevision` INTEGER NOT NULL, `dirty` INTEGER NOT NULL,
                    `pendingRequestJson` TEXT, `pendingRevision` INTEGER,
                    `conflictJson` TEXT, `updatedAt` INTEGER NOT NULL)""".trimIndent())
                db.execSQL("CREATE TABLE IF NOT EXISTS `trip_sync_active` (`id` INTEGER NOT NULL PRIMARY KEY, `tripId` TEXT NOT NULL, `needsActivation` INTEGER NOT NULL)")
            }
        }

        private val instances = mutableMapOf<String, AppDatabase>()

        /** 每个账号使用独立文件。旧 citygo_app.db 无归属信息，原样保留且不自动认领。 */
        fun get(context: Context, accountKey: String): AppDatabase =
            synchronized(instances) {
                instances.getOrPut(accountKey) { Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    AccountStorage.databaseName(accountKey),
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    )
                    .build()
                }
            }
    }
}
