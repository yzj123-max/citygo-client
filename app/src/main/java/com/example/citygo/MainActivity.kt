package com.example.citygo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citygo.skeleton.SkeletonApp
import com.example.citygo.skeleton.data.AccountModelStore
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.AppViewModel as GlobalAppViewModel
import com.example.citygo.skeleton.profile.AppViewModel
import com.example.citygo.ui.theme.CityGoTheme

/** App 唯一 Activity 和正式入口；挂载登录流程、五个主 Tab 及其二级页面。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开启 EdgeToEdge：内容延伸到状态栏 / 导航栏后方，由各组件自行处理安全区
        enableEdgeToEdge()
        setContent {
            val identity by AccountSession.identity.collectAsStateWithLifecycle()
            val accountKey = identity.storageKey
            val accountModels: AccountModelStore = viewModel()
            val accountOwner = accountModels.forAccount("$accountKey:${identity.generation}")
            // 账号业务 VM 绑定独立存储；登录与导航 VM 保持原生命周期，避免中断登录分流。
            val appViewModel: AppViewModel = viewModel(viewModelStoreOwner = accountOwner)
            val globalAppViewModel: GlobalAppViewModel =
                viewModel(viewModelStoreOwner = accountOwner, factory = GlobalAppViewModel.factory(accountKey))
            // 主题开关读 DataStore 持久化的深色模式，App 重启后自动恢复
            val isDarkMode by globalAppViewModel.isDarkMode.collectAsStateWithLifecycle()
            CityGoTheme(darkTheme = isDarkMode, dynamicColor = false) {
                // 强制主题时系统仍可能处于浅色模式，需手动同步状态栏 / 导航栏图标明暗，
                // 避免深色主题下图标也是深色而看不清
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val controller = WindowCompat.getInsetsController(
                            (view.context as android.app.Activity).window,
                            view,
                        )
                        // 浅色主题用深色图标，深色主题用浅色图标
                        controller.isAppearanceLightStatusBars = !isDarkMode
                        controller.isAppearanceLightNavigationBars = !isDarkMode
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // 全局一次性通知（如后台轮询的 AI 攻略完成自动同步后提示「已成功生成攻略」）：
                    // 在 Activity 根组件收集，用户停留在任意 Tab / 页面都能收到，不依赖计划页存活
                    val appContext = LocalContext.current
                    LaunchedEffect(globalAppViewModel) {
                        globalAppViewModel.taskNoticeEvents.collect { message ->
                            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                        }
                    }
                    SkeletonApp(
                        appViewModel = appViewModel,
                        globalAppViewModel = globalAppViewModel,
                    )
                }
            }
        }
    }
}
