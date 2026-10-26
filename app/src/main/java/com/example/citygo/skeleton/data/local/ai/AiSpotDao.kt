package com.example.citygo.skeleton.data.local.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/* =====================================================================================
 * AI 攻略地点子表 DAO（ai_spot）—— 全新增文件，不改动任何既有 DAO
 *
 * 保存一份攻略下的全部景点 / 美食 / 探店点位，按 sortOrder 维持大模型推荐顺序。
 * 与 [AiTravelPlanDao] 配合：先插主表拿 id，再批量插子表；事务由 Repository 层保证。
 * ===================================================================================== */
@Dao
interface AiSpotDao {

    /** 取某份攻略的全部地点，严格按推荐顺序（sortOrder 升序） */
    @Query("SELECT * FROM ai_spot WHERE planId = :planId ORDER BY sortOrder ASC")
    fun getByPlan(planId: Long): List<AiSpotEntity>

    /** 批量插入地点子表（同主键冲突时替换，保证重存幂等） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<AiSpotEntity>)

    /** 删除某份攻略下的全部地点（通常由主表外键级联完成，此处供显式清理使用） */
    @Query("DELETE FROM ai_spot WHERE planId = :planId")
    fun deleteByPlan(planId: Long)

    /** 清空地点子表（与主表清空在同一事务内调用，避免外键约束异常） */
    @Query("DELETE FROM ai_spot")
    fun deleteAll()
}
