package app.cmsl.classical.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

@Serializable
data object LibraryRoute

@Serializable
data object InfoRoute

@Serializable
data object LicensesRoute

@Serializable
data object ImageCreditsRoute

@Serializable
data object PlayerRoute

@Serializable
data class GenreRoute(val genreId: String)

/** genreId を指定すると、そのジャンルの作品だけを出す。 */
@Serializable
data class ComposerRoute(val composerId: String, val genreId: String? = null)

@Serializable
data class WorkRoute(val workId: String)
