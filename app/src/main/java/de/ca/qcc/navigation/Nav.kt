package de.ca.qcc.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Dashboard : Screen("dashboard")
    data object Duplicates : Screen("duplicates")
    data object Reader : Screen("reader")
    data object Language : Screen("language")
    data object About : Screen("about")
}
