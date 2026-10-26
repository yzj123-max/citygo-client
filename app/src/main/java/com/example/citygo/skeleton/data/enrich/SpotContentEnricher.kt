package com.example.citygo.skeleton.data.enrich

import com.example.citygo.skeleton.data.model.ScenicSpot

/* =====================================================================================
 * 景点文案补充器（RAG 预留点）
 *
 * 高德 POI 只解决「点位」问题（名称 / 坐标 / 地址 / 类型 / 评分），
 * 不提供门票政策、出行攻略、避坑提示等文案。本接口负责在 POI 映射完成后补齐：
 *
 * - [LocalRuleSpotEnricher]：按景点名称 / 类型关键词套本地规则模板，
 *   确定性生成，零网络依赖、即时返回，当前默认使用；
 * - [RemoteLlmSpotEnricher]：RAG 大模型补充的预留骨架（TODO），
 *   未来以「城市 + 景点名 + 类型」为上下文调大模型生成高质量文案，
 *   失败 / 超时时降级回本地规则，保证详情页永远有内容可展示。
 *
 * 约定：Enricher 只填充空字段 / 空列表，已有真实内容（POI 返回或未来后端下发）保持不动。
 * ===================================================================================== */

/**
 * 景点文案补充器：对单个 POI 映射后的景点补齐文案字段。
 * 接口设为 suspend，便于未来直接切换为网络大模型实现。
 */
interface SpotContentEnricher {
    /** 返回补充文案后的新景点对象（不可变拷贝） */
    suspend fun enrich(spot: ScenicSpot): ScenicSpot
}
