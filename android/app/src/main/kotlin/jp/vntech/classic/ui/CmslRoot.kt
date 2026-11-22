package jp.vntech.classic.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import jp.vntech.classic.R
import jp.vntech.classic.di.AppContainer
import jp.vntech.classic.ui.components.LocalArtwork
import jp.vntech.classic.ui.components.LocalOpenImageCredits
import jp.vntech.classic.ui.components.LocalPortraits
import jp.vntech.classic.ui.composer.ComposerScreen
import jp.vntech.classic.ui.genre.GenreScreen
import jp.vntech.classic.ui.home.HomeScreen
import jp.vntech.classic.ui.info.ImageCreditsScreen
import jp.vntech.classic.ui.info.InfoScreen
import jp.vntech.classic.ui.info.LicensesScreen
import jp.vntech.classic.ui.library.LibraryScreen
import jp.vntech.classic.ui.navigation.ComposerRoute
import jp.vntech.classic.ui.navigation.GenreRoute
import jp.vntech.classic.ui.navigation.HomeRoute
import jp.vntech.classic.ui.navigation.ImageCreditsRoute
import jp.vntech.classic.ui.navigation.InfoRoute
import jp.vntech.classic.ui.navigation.LibraryRoute
import jp.vntech.classic.ui.navigation.LicensesRoute
import jp.vntech.classic.ui.navigation.PlayerRoute
import jp.vntech.classic.ui.navigation.SearchRoute
import jp.vntech.classic.ui.navigation.WorkRoute
import jp.vntech.classic.ui.player.MiniPlayer
import jp.vntech.classic.ui.player.PlayerScreen
import jp.vntech.classic.ui.player.PlayerViewModel
import jp.vntech.classic.ui.search.SearchScreen
import jp.vntech.classic.ui.theme.CmslTheme
import jp.vntech.classic.ui.work.WorkScreen

private data class Tab(val route: Any, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(HomeRoute, R.string.tab_home, Icons.Filled.Home),
    Tab(SearchRoute, R.string.tab_search, Icons.Filled.Search),
    Tab(LibraryRoute, R.string.tab_library, Icons.Filled.LibraryMusic),
    Tab(InfoRoute, R.string.tab_info, Icons.Filled.Info),
)

/** 画面の根。タブ（ホーム・検索・ライブラリ・情報）と、その上のミニプレイヤー（SPEC §4.1）。 */
@Composable
fun CmslRoot(container: AppContainer) {
    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalArtwork provides container.artwork,
        LocalPortraits provides container.portraits,
    ) {
        CmslTheme {
            val nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val onPlayer = entry?.destination?.hasRoute<PlayerRoute>() == true
            val playerVm = containerViewModel { PlayerViewModel(it) }
            val playerState by playerVm.state.collectAsStateWithLifecycle()
            // 上のシステムバーは各画面の TopAppBar が空ける。ここで空けるのはタブバーとミニプレイヤーの分だけ
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!onPlayer) {
                        Column {
                            MiniPlayer(
                                state = playerState,
                                onOpen = { nav.navigate(PlayerRoute) { launchSingleTop = true } },
                                onPlayPause = playerVm.player::togglePlayPause,
                                onNext = playerVm.player::skipNext,
                            )
                            TabBar(nav)
                        }
                    }
                },
            ) { padding ->
                CmslNavHost(nav, Modifier.padding(padding).consumeWindowInsets(padding))
            }
        }
    }
}

@Composable
private fun TabBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    NavigationBar {
        TABS.forEach { tab ->
            val selected = entry?.destination?.hierarchy()?.any { d -> d.hasRoute(tab.route::class) } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

private fun androidx.navigation.NavDestination.hierarchy() = generateSequence(this) { it.parent }

@Composable
private fun CmslNavHost(nav: NavHostController, modifier: Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    val openImageCredits: () -> Unit = { nav.navigate(ImageCreditsRoute) { launchSingleTop = true } }
    CompositionLocalProvider(LocalOpenImageCredits provides openImageCredits) {
        CmslNavGraph(nav, modifier, back, openImageCredits)
    }
}

@Composable
private fun CmslNavGraph(nav: NavHostController, modifier: Modifier, back: () -> Unit, openImageCredits: () -> Unit) {
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(onGenre = { nav.navigate(GenreRoute(it)) }, onWork = { nav.navigate(WorkRoute(it)) })
        }
        composable<SearchRoute> {
            SearchScreen(
                onComposer = { nav.navigate(ComposerRoute(it)) },
                onWork = { nav.navigate(WorkRoute(it)) },
                onGenre = { nav.navigate(GenreRoute(it)) },
            )
        }
        composable<LibraryRoute> { LibraryScreen() }
        composable<InfoRoute> {
            InfoScreen(onLicenses = { nav.navigate(LicensesRoute) }, onImageCredits = openImageCredits)
        }
        composable<ImageCreditsRoute> { ImageCreditsScreen(onBack = back) }
        composable<LicensesRoute> { LicensesScreen(onBack = back) }
        composable<GenreRoute> { e ->
            val r = e.toRoute<GenreRoute>()
            GenreScreen(r.genreId, onComposer = { nav.navigate(ComposerRoute(it, r.genreId)) }, onBack = back)
        }
        composable<ComposerRoute> { e ->
            val r = e.toRoute<ComposerRoute>()
            ComposerScreen(r.composerId, r.genreId, onWork = { nav.navigate(WorkRoute(it)) }, onBack = back)
        }
        composable<WorkRoute> { e -> WorkScreen(e.toRoute<WorkRoute>().workId, onBack = back) }
        composable<PlayerRoute> { PlayerScreen(onClose = back) }
    }
}
