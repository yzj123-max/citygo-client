package com.example.citygo.skeleton.screens.stylist

import android.app.Application
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.citygo.skeleton.data.model.Place
import com.example.citygo.skeleton.data.model.TripPlan
import com.example.citygo.skeleton.data.remote.stylist.StylistDailyPlanDto
import com.example.citygo.skeleton.data.remote.stylist.StylistRemote
import com.example.citygo.skeleton.data.remote.stylist.StylistScheduleDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 评估流程阶段：与 UI 按钮 / 状态条一一对应，禁止隐式推断 */
enum class StylistPhase {
    /** 初始（可编辑图片 / 问题） */
    IDLE,

    /** 图片依次上传中 */
    UPLOADING,

    /** SSE 流式评估中（answerText 持续追加） */
    STREAMING,

    /** 流式正常结束（服务端关闭连接 = 正常现象） */
    DONE,

    /** 网络 / 接口异常：仅页面内友好提示，不崩溃 */
    FAILED,
}

/** 一张已选穿搭照片（uri 为系统相册临时授权的 content:// 地址） */
data class StylistImage(
    val id: Long,
    val uri: Uri,
)

/** 默认提问（后端样例原句，可在页面编辑） */
const val STYLIST_DEFAULT_QUERY = "帮我评估这套穿搭和景点氛围搭不搭"

data class StylistUiState(
    val images: List<StylistImage> = emptyList(),
    val phase: StylistPhase = StylistPhase.IDLE,
    /** UPLOADING 阶段：已完成上传张数 / 总张数 */
    val uploadedCount: Int = 0,
    val totalCount: Int = 0,
    /** SSE 已接收到的评估全文（逐段 append） */
    val answerText: String = "",
    /** 失败时的友好提示文案（仅 FAILED 阶段展示） */
    val errorMessage: String? = null,
    /** 用户提问，默认按后端样例预填，可编辑 */
    val userQuery: String = STYLIST_DEFAULT_QUERY,
) {
    val busy: Boolean get() = phase == StylistPhase.UPLOADING || phase == StylistPhase.STREAMING
}

/**
 * AI 穿搭评估页 ViewModel（仅本模块新增，不接入 / 不修改任何既有 ViewModel 与网络封装）。
 *
 * 流程严格按后端契约：
 * 1. 选择穿搭照片（系统相册，多图）；
 * 2. 点击评估 → 所有照片在一个 multipart 请求中提交，后端负责上传 TOS；
 * 3. POST /api/stylist/chat/multiImgChatSse，独立提交行程 JSON，接收综合评估；
 * 4. 连接正常关闭 → DONE；任何网络 / 接口异常 → FAILED + 友好文案，App 不崩溃。
 */
class StylistViewModel(app: Application) : AndroidViewModel(app) {

    private val remote = StylistRemote(app.applicationContext)

    private val _uiState = MutableStateFlow(StylistUiState())
    val uiState: StateFlow<StylistUiState> = _uiState.asStateFlow()

    private var evaluateJob: Job? = null
    private var imageSeq: Long = 0L

    /** 追加用户从相册选择的照片（去重同一 Uri；上限 9 张） */
    fun addImages(uris: List<Uri>) {
        if (_uiState.value.busy) return
        val exist = _uiState.value.images.mapTo(mutableSetOf()) { it.uri }
        val added = uris.distinct()
            .filter { it !in exist }
            .map { StylistImage(id = ++imageSeq, uri = it) }
        _uiState.update { state ->
            val merged = (state.images + added).take(MAX_IMAGE_COUNT)
            state.copy(images = merged)
        }
    }

    /** 移除一张已选照片 */
    fun removeImage(id: Long) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(images = it.images.filterNot { img -> img.id == id }) }
    }

    fun updateQuery(text: String) {
        _uiState.update { it.copy(userQuery = text) }
    }

    /** 完成 / 失败后重新开始一轮（清空评估结果，保留已选照片与提问） */
    fun resetResult() {
        evaluateJob?.cancel()
        _uiState.update {
            it.copy(
                phase = StylistPhase.IDLE,
                uploadedCount = 0,
                totalCount = 0,
                answerText = "",
                errorMessage = null,
            )
        }
    }

    /**
     * 开始评估。
     *
     * @param trip 当前全局行程（按天景点）；空行程 / 无景点时拒绝并提示先生成攻略
     */
    fun evaluate(trip: TripPlan) {
        val state = _uiState.value
        if (state.busy) return
        if (state.images.isEmpty()) {
            _uiState.update { it.copy(phase = StylistPhase.FAILED, errorMessage = "请先选择至少一张穿搭照片") }
            return
        }
        val dailyPlans = trip.toStylistDailyPlans()
        if (dailyPlans.isEmpty()) {
            _uiState.update {
                it.copy(
                    phase = StylistPhase.FAILED,
                    errorMessage = "当前还没有行程景点，请先在「计划」页生成行程攻略后再来评估",
                )
            }
            return
        }
        val query = state.userQuery.ifBlank { STYLIST_DEFAULT_QUERY }

        evaluateJob?.cancel()
        evaluateJob = viewModelScope.launch {
            _uiState.value = StylistUiState(
                images = state.images,
                phase = StylistPhase.UPLOADING,
                totalCount = state.images.size,
                userQuery = query,
            )
            // Gson 生成有效 JSON；所有照片按选图顺序在同一个请求中提交。
            val schedule = Gson().toJson(StylistScheduleDto(dailyPlanList = dailyPlans))
            try {
                remote.streamChat(
                    imageUris = state.images.map { it.uri },
                    message = query,
                    travelSchedule = schedule,
                ).collect { delta ->
                    _uiState.update {
                        it.copy(phase = StylistPhase.STREAMING, answerText = it.answerText + delta)
                    }
                }
                // Flow 正常结束（含服务端主动关闭连接）→ 评估完成
                _uiState.update {
                    it.copy(
                        phase = StylistPhase.DONE,
                        errorMessage = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "chat sse failed", e)
                enterFailure(friendlyNetworkError(e, prefix = "评估失败"))
            }
        }
    }

    private fun enterFailure(message: String) {
        _uiState.update {
            it.copy(phase = StylistPhase.FAILED, errorMessage = message)
        }
    }

    /** 网络异常 → 友好文案（USB adb reverse 联调模式重点提示 127.0.0.1:8080 转发） */
    private fun friendlyNetworkError(e: Throwable?, prefix: String): String {
        val tail = when (e) {
            is UnknownHostException, is ConnectException ->
                "无法连接服务器，请确认手机已用 USB 连接电脑，且电脑已执行 adb reverse tcp:8080 tcp:8080"
            is SocketTimeoutException, is InterruptedIOException ->
                "服务响应超时，请检查网络或稍后重试"
            is IOException -> e.message?.takeIf { it.isNotBlank() } ?: "网络异常，请稍后重试"
            else -> e?.message?.takeIf { it.isNotBlank() } ?: "服务异常，请稍后重试"
        }
        return "$prefix：$tail"
    }

    companion object {
        private const val TAG = "StylistViewModel"
        private const val MAX_IMAGE_COUNT = 9

        /** 把全局 [TripPlan] 的按天景点映射为穿搭接口的 dailyPlanList（只读取，不改动原数据） */
        private fun TripPlan.toStylistDailyPlans(): List<StylistDailyPlanDto> =
            dailyPlans.flatMap { day ->
                day.places.map { place -> place.toDto(day.day) }
            }

        /**
         * 单个景点 → 接口 DTO：
         * spotStyle 取景点类型（酒店/探店/饭店/景点），spotTone 取出片场景，
         * spotDesc 拼接门票与简介；字段缺失就留空，不虚构内容。
         */
        private fun Place.toDto(dayIndex: Int): StylistDailyPlanDto = StylistDailyPlanDto(
            dayName = dayNameCn(dayIndex),
            spotName = name,
            spotStyle = placeType.label,
            spotTone = photoScene.orEmpty(),
            spotDesc = listOfNotNull(
                ticket?.takeIf { it.isNotBlank() },
                intro?.takeIf { it.isNotBlank() },
            ).joinToString("；"),
        )

        /** 第 1..15 天 → 第一天…第十五天（超出范围回退阿拉伯数字） */
        private fun dayNameCn(day: Int): String {
            val cn = listOf(
                "一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
                "十一", "十二", "十三", "十四", "十五",
            )
            return if (day in 1..cn.size) "第${cn[day - 1]}天" else "第$day 天"
        }
    }
}
