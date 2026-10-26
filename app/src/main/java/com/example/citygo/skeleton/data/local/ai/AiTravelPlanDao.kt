package com.example.citygo.skeleton.data.local.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/* =====================================================================================
 * AI 攻略主表 DAO（ai_travel_plan）—— 全新增文件，不改动任何既有 DAO
 *
 * 缓存模型：一个城市只保留最新一份攻略；地点子表 ai_spot 通过外键
 * ON DELETE CASCADE 随主表记录一并删除。
 *
 * 历史缓存表 DAO，仅为兼容已有数据库保留，不参与正式攻略生成。
 * 方法均为 Room 阻塞式，
 * 禁止在主线程调用，调用方须处于 IO 线程 / 协程中。
 * ===================================================================================== */
@Dao
interface AiTravelPlanDao {

    /** 查某城市最新一份历史攻略主表（无缓存返回 null）。 */
    @Query("SELECT * FROM ai_travel_plan WHERE city = :city ORDER BY generateTime DESC LIMIT 1")
    fun getLatestByCity(city: String): AiTravelPlanEntity?

    /** 按主键查攻略主表 */
    @Query("SELECT * FROM ai_travel_plan WHERE id = :planId LIMIT 1")
    fun getById(planId: Long): AiTravelPlanEntity?

    /** 插入攻略主表，返回 Room 分配的自增 id（供地点子表关联） */
    @Insert
    fun insertPlan(entity: AiTravelPlanEntity): Long

    /** 删除某城市的全部攻略（ai_spot 通过外键 CASCADE 级联清除） */
    @Query("DELETE FROM ai_travel_plan WHERE city = :city")
    fun deleteByCity(city: String)

    /** 清空全部 AI 攻略主表（子表由外键级联清除） */
    @Query("DELETE FROM ai_travel_plan")
    fun deleteAll()

    /** 统计当前缓存的攻略份数（调试 / 缓存管理用） */
    @Query("SELECT COUNT(*) FROM ai_travel_plan")
    fun countAll(): Int
}
