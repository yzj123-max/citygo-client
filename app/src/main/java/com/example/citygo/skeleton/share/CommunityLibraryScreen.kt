package com.example.citygo.skeleton.share

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.AppViewModel
import com.example.citygo.skeleton.data.ShareViewModel
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.screens.ShareScreen

@Composable
fun CommunityLibraryScreen(shareViewModel: ShareViewModel, appViewModel: AppViewModel,
    onBack: () -> Unit, onOpen: (String) -> Unit, onPublish: () -> Unit,
    source: String = "collections") {
    val account by AccountSession.currentAccount.collectAsStateWithLifecycle()
    val returnSource = rememberSaveable(account?.phone) { shareViewModel.uiState.value.source }
    val returnKeyword = rememberSaveable(account?.phone) { shareViewModel.communityKeyword.value.orEmpty() }
    val leaveLibrary: () -> Unit = {
        shareViewModel.setCommunityKeyword(returnKeyword)
        shareViewModel.selectSource(returnSource)
        onBack()
    }
    BackHandler(onBack = leaveLibrary)
    LaunchedEffect(account?.token) {
        shareViewModel.selectSource(source)
        shareViewModel.clearCommunityKeyword()
        shareViewModel.refresh()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TextButton(onClick = leaveLibrary) { Text(if (source == "mine") "返回 · 我的发布" else "返回 · 我的收藏") }
        ShareScreen(onArticleClick = onOpen, shareViewModel = shareViewModel,
            appViewModel = appViewModel, onPublish = onPublish, modifier = Modifier.weight(1f), showSourceControls = true)
    }
}
