package com.example.citygo.skeleton.data

import android.content.Context
import com.example.citygo.skeleton.data.datastore.SettingsDataStore
import com.example.citygo.skeleton.data.datastore.AccountStorage
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.local.app.AppDatabase
import com.example.citygo.skeleton.data.repository.LocalRepository
import com.example.citygo.skeleton.data.repository.Repository
import com.example.citygo.skeleton.data.repository.agent.AgentRepository
import com.example.citygo.skeleton.data.repository.agent.DoubaoAgentRepository
import com.example.citygo.skeleton.data.repository.chat.ChatHistoryRepository
import com.example.citygo.skeleton.data.repository.chat.RoomChatHistoryRepository
import com.example.citygo.skeleton.data.repository.hotel.AmapHotelPoiRepository
import com.example.citygo.skeleton.data.repository.hotel.HotelRepository
import com.example.citygo.skeleton.data.repository.image.AmapPoiImageRepository
import com.example.citygo.skeleton.data.repository.image.ImageRepository

/* =====================================================================================
 * App 全局业务数据 ServiceLocator（手工依赖注入，项目无 Hilt / Koin）
 *
 * 按账号提供固定分区的业务与聊天仓库，公共网络能力保持单例，
 * 与高德导航 / 天气预报使用的 RepositoryContainer 完全独立，互不影响。
 *
 * 在 Application.onCreate 中调用一次 [init]；ViewModel 通过 [requireRepository]
 * 拿到接口实例，只依赖 [Repository] 抽象，不直接接触 Room。
 * ===================================================================================== */

object AppServiceLocator {

    @Volatile
    private var imageRepositoryInstance: ImageRepository? = null

    @Volatile
    private var agentRepositoryInstance: AgentRepository? = null

    @Volatile
    private var hotelRepositoryInstance: HotelRepository? = null

    /** Application Context（供需要原生 SDK 上下文的能力使用，如高德地理编码 GeocodeSearch） */
    @Volatile
    private var appContextInstance: Context? = null

    /** 初始化业务 Repository 与配置 DataStore（幂等，重复调用安全） */
    fun init(context: Context) {
        val appContext = context.applicationContext
        appContextInstance = appContext
        // 地点图片仓库：复用高德 Web 服务 Key 做 POI 实拍图搜索（全新独立网络调用，
        // 不触碰任何既有高德导航 / 天气代码）
        if (imageRepositoryInstance == null) {
            synchronized(this) {
                if (imageRepositoryInstance == null) {
                    imageRepositoryInstance = AmapPoiImageRepository()
                }
            }
        }
        // 豆包对话 Agent：独立的大模型对话通道（火山方舟），与 AI 攻略生成、高德 / 天气互不影响
        if (agentRepositoryInstance == null) {
            synchronized(this) {
                if (agentRepositoryInstance == null) {
                    agentRepositoryInstance = DoubaoAgentRepository()
                }
            }
        }
        // 酒店 POI 搜索仓库：高德 Web 服务住宿类 POI（全新独立网络调用，不触碰导航 / 天气代码）
        if (hotelRepositoryInstance == null) {
            synchronized(this) {
                if (hotelRepositoryInstance == null) {
                    hotelRepositoryInstance = AmapHotelPoiRepository()
                }
            }
        }
    }

    /** 仓库在创建时固定账号，不在异步写入时读取当前身份。 */
    fun requireRepository(accountKey: String = currentAccountKey()): Repository =
        LocalRepository.create(AppDatabase.get(requireAppContext(), accountKey))

    fun requireSettingsDataStore(accountKey: String = currentAccountKey()): SettingsDataStore =
        SettingsDataStore(requireAppContext(), accountKey)

    private fun currentAccountKey(): String = AccountStorage.keyFor(
        AccountSession.currentAccount.value?.takeIf { it.token.isNotBlank() }?.phone,
    )

    /** 获取全局唯一地点图片 Repository；未初始化时直接抛错 */
    fun requireImageRepository(): ImageRepository =
        imageRepositoryInstance
            ?: error("AppServiceLocator 未初始化，请先在 Application.onCreate 调用 init(context)")

    /** 获取全局唯一豆包对话 Agent Repository；未初始化时直接抛错 */
    fun requireAgentRepository(): AgentRepository =
        agentRepositoryInstance
            ?: error("AppServiceLocator 未初始化，请先在 Application.onCreate 调用 init(context)")

    /** 聊天和行程使用同一个账号库。 */
    fun requireChatHistoryRepository(accountKey: String = currentAccountKey()): ChatHistoryRepository =
        RoomChatHistoryRepository.create(AppDatabase.get(requireAppContext(), accountKey))

    /** 获取全局唯一酒店 POI 搜索 Repository；未初始化时直接抛错 */
    fun requireHotelRepository(): HotelRepository =
        hotelRepositoryInstance
            ?: error("AppServiceLocator 未初始化，请先在 Application.onCreate 调用 init(context)")

    /** 获取全局 Application Context；未初始化时直接抛错（高德地理编码等原生 SDK 使用） */
    fun requireAppContext(): Context =
        appContextInstance
            ?: error("AppServiceLocator 未初始化，请先在 Application.onCreate 调用 init(context)")
}
