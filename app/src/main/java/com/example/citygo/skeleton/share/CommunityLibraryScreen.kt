package com.example.citygo.skeleton.share

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.AppViewModel
import com.example.citygo.skeleton.data.ShareViewModel
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.screens.ShareScreen

@Composable
fun CommunityLibraryScreen(shareViewModel: ShareViewModel, appViewModel: AppViewModel,
    onBack: () -> Unit, onOpen: (String) -> Unit, onPublish: () -> Unit) {
    val account by AccountSession.currentAccount.collectAsStateWithLifecycle()
    LaunchedEffect(account?.token) {
        shareViewModel.selectSource("collections")
        shareViewModel.refresh()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TextButton(onClick = onBack) { Text("返回 · 我的收藏") }
        ShareScreen(onArticleClick = onOpen, shareViewModel = shareViewModel,
            appViewModel = appViewModel, onPublish = onPublish, modifier = Modifier.weight(1f))
    }
}
