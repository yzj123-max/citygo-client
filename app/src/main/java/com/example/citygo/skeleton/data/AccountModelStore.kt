package com.example.citygo.skeleton.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Activity 保留此容器；身份切换同步销毁旧业务 VM，取消其任务，避免缓存或回复跨账号。 */
class AccountModelStore : ViewModel() {
    private var accountKey: String? = null
    private var owner: ViewModelStoreOwner? = null

    fun forAccount(key: String): ViewModelStoreOwner {
        if (key != accountKey) {
            owner?.viewModelStore?.clear()
            owner = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
            accountKey = key
        }
        return checkNotNull(owner)
    }

    override fun onCleared() {
        owner?.viewModelStore?.clear()
    }
}
