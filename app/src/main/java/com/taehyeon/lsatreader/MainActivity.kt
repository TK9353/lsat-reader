package com.taehyeon.lsatreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taehyeon.lsatreader.ui.AppViewModel
import com.taehyeon.lsatreader.ui.FeedScreen
import com.taehyeon.lsatreader.ui.LibraryScreen
import com.taehyeon.lsatreader.ui.LsatTheme
import com.taehyeon.lsatreader.ui.ReaderScreen
import com.taehyeon.lsatreader.ui.SearchScreen
import com.taehyeon.lsatreader.ui.SettingsScreen
import com.taehyeon.lsatreader.ui.WordSheet

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LsatTheme { App() } }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { vm.feed.size })
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val m = vm.message ?: return@LaunchedEffect
        vm.message = null
        snackbar.showSnackbar(m)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Triple("피드", Icons.Filled.Home, 0),
                    Triple("검색", Icons.Filled.Search, 1),
                    Triple("보관함", Icons.Filled.Star, 2),
                    Triple("설정", Icons.Filled.Settings, 3),
                )
                items.forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = { tab = idx; vm.closeReader() },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> FeedScreen(vm, pagerState)
                1 -> SearchScreen(vm)
                2 -> LibraryScreen(vm)
                else -> SettingsScreen(vm)
            }
            vm.reader?.let { ReaderScreen(vm, it) }
            vm.overlayLoading?.let { msg ->
                Column(
                    Modifier.fillMaxSize().background(Color(0x99000000)).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(msg, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
    }
    vm.wordPopup?.let { WordSheet(vm, it) }
}
