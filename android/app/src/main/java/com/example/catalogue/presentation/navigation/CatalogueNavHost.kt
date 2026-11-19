package com.example.catalogue.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.catalogue.AppContainer
import com.example.catalogue.presentation.category.CategoryScreen
import com.example.catalogue.presentation.category.CategoryViewModel
import com.example.catalogue.presentation.details.ProductDetailsScreen
import com.example.catalogue.presentation.details.ProductDetailsViewModel
import com.example.catalogue.presentation.enquiry.EnquiryScreen
import com.example.catalogue.presentation.enquiry.EnquiryViewModel
import com.example.catalogue.presentation.home.HomeScreen
import com.example.catalogue.presentation.home.HomeViewModel
import com.example.catalogue.presentation.search.SearchScreen
import com.example.catalogue.presentation.search.SearchViewModel
import kotlinx.serialization.Serializable

/*
 * Type-safe routes. Navigation serialises the arguments and URL-encodes strings,
 * so category names like "men's clothing" survive the trip unchanged.
 */
@Serializable object HomeRoute
@Serializable data class CategoryRoute(val name: String)
@Serializable data class SearchRoute(val query: String)
@Serializable data class ProductRoute(val id: Int)
@Serializable data class EnquiryRoute(val productId: Int, val productTitle: String)

/**
 * The app's screens and how they connect. Each ViewModel is created by `viewModel { ... }`,
 * which scopes it to that back-stack entry: it survives rotation and is cleared when the
 * user navigates Back past the screen. Use cases come from the AppContainer (constructor injection).
 */
@Composable
fun CatalogueNavHost(container: AppContainer) {
    val navController = rememberNavController()
    // dropUnlessResumed ignores a second Back tap while the screen is already leaving,
    // so a double tap cannot pop two screens at once.
    @Composable fun back(): () -> Unit = dropUnlessResumed { navController.popBackStack() }
    val openProduct: (Int) -> Unit = { navController.navigate(ProductRoute(it)) }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            val vm = viewModel {
                HomeViewModel(container.getFeaturedProducts, container.getCategories, container.searchProducts)
            }
            HomeScreen(
                viewModel = vm,
                onSearch = { navController.navigate(SearchRoute(it)) },
                onCategoryClick = { navController.navigate(CategoryRoute(it)) },
                onProductClick = openProduct,
            )
        }
        composable<CategoryRoute> { entry ->
            val route = entry.toRoute<CategoryRoute>()
            val vm = viewModel { CategoryViewModel(route.name, container.getCategoryProducts) }
            CategoryScreen(vm, onProductClick = openProduct, onBack = back())
        }
        composable<SearchRoute> { entry ->
            val route = entry.toRoute<SearchRoute>()
            val vm = viewModel { SearchViewModel(route.query, container.searchProducts) }
            SearchScreen(vm, onProductClick = openProduct, onBack = back())
        }
        composable<ProductRoute> { entry ->
            val route = entry.toRoute<ProductRoute>()
            val vm = viewModel { ProductDetailsViewModel(route.id, container.getProductDetails) }
            ProductDetailsScreen(
                viewModel = vm,
                onEnquire = { navController.navigate(EnquiryRoute(it.id, it.title)) },
                onCategoryClick = { navController.navigate(CategoryRoute(it)) },
                onBack = back(),
            )
        }
        composable<EnquiryRoute> { entry ->
            val route = entry.toRoute<EnquiryRoute>()
            val vm = viewModel { EnquiryViewModel(route.productId, container.submitEnquiry) }
            val goBack = back()
            EnquiryScreen(vm, productTitle = route.productTitle, onDone = goBack, onBack = goBack)
        }
    }
}
