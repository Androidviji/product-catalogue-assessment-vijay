@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.catalogue.presentation

import com.example.catalogue.domain.model.AppError
import com.example.catalogue.domain.model.CategoryProducts
import com.example.catalogue.domain.model.DataResult
import com.example.catalogue.domain.model.FeaturedProducts
import com.example.catalogue.domain.usecase.GetCategoriesUseCase
import com.example.catalogue.domain.usecase.GetCategoryProductsUseCase
import com.example.catalogue.domain.usecase.GetFeaturedProductsUseCase
import com.example.catalogue.domain.usecase.SearchProductsUseCase
import com.example.catalogue.presentation.category.CategoryViewModel
import com.example.catalogue.presentation.common.LoadState
import com.example.catalogue.presentation.home.HomeViewModel
import com.example.catalogue.testing.FakeCatalogueRepository
import com.example.catalogue.testing.MainDispatcherRule
import com.example.catalogue.testing.product
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeCatalogueRepository()

    private fun viewModel() = HomeViewModel(
        GetFeaturedProductsUseCase(repository),
        GetCategoriesUseCase(repository),
        SearchProductsUseCase(repository),
    )

    @Test
    fun `starts loading then shows featured products and categories`() = runTest(main.dispatcher) {
        repository.featured = DataResult.Success(FeaturedProducts((1..5).map { product(it) }, 5, 0))
        repository.categories = DataResult.Success(listOf("electronics", "men's clothing"))

        val vm = viewModel()
        assertEquals(LoadState.Loading, vm.state.value.featured)
        advanceUntilIdle()

        val featured = (vm.state.value.featured as LoadState.Loaded).data
        assertEquals(5, featured.products.size)
        assertEquals(false, featured.isIncomplete)
        assertEquals(LoadState.Loaded(listOf("electronics", "men's clothing")), vm.state.value.categories)
    }

    @Test
    fun `missing featured products are reported, not hidden`() = runTest(main.dispatcher) {
        repository.featured = DataResult.Success(FeaturedProducts(listOf(product(1), product(5)), 5, 3))

        val vm = viewModel()
        advanceUntilIdle()

        val featured = (vm.state.value.featured as LoadState.Loaded).data
        assertTrue(featured.isIncomplete)
        assertEquals(2, featured.products.size)
        assertEquals(5, featured.configuredCount)
    }

    @Test
    fun `featured failure shows error and retry loads again`() = runTest(main.dispatcher) {
        repository.featured = DataResult.Failure(AppError.Timeout)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(LoadState.Failed(AppError.Timeout), vm.state.value.featured)

        repository.featured = DataResult.Success(FeaturedProducts(listOf(product(1)), 1, 0))
        vm.loadFeatured()
        assertEquals(LoadState.Loading, vm.state.value.featured)
        advanceUntilIdle()

        assertTrue(vm.state.value.featured is LoadState.Loaded)
        assertEquals(2, repository.featuredCalls)
    }

    @Test
    fun `category failure does not hide featured products`() = runTest(main.dispatcher) {
        repository.categories = DataResult.Failure(AppError.NoConnection)
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.featured is LoadState.Loaded)
        assertEquals(LoadState.Failed(AppError.NoConnection), vm.state.value.categories)
    }

    @Test
    fun `search button with blank text shows a message and does not navigate`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onSearchQueryChange("   ")

        assertNull(vm.onSearchClicked())
        assertNotNull(vm.state.value.searchError)

        vm.onSearchQueryChange(" backpack ")
        assertNull("typing clears the message", vm.state.value.searchError)
        assertEquals("backpack", vm.onSearchClicked())
        assertTrue("home never searches itself", repository.searchedQueries.isEmpty())
    }
}

class CategoryViewModelTest {

    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeCatalogueRepository()

    @Test
    fun `requests the exact category name and shows both sections`() = runTest(main.dispatcher) {
        repository.categoryProducts = DataResult.Success(
            CategoryProducts("men's clothing", listOf(product(3), product(2), product(1)), listOf(product(4))),
        )
        val vm = CategoryViewModel("men's clothing", GetCategoryProductsUseCase(repository))
        advanceUntilIdle()

        assertEquals(listOf("men's clothing"), repository.categoryRequests)
        val data = (vm.state.value as LoadState.Loaded).data
        assertEquals(listOf(3, 2, 1), data.topRated.map { it.id })
        assertEquals(listOf(4), data.otherProducts.map { it.id })
    }

    @Test
    fun `not found then retry`() = runTest(main.dispatcher) {
        repository.categoryProducts = DataResult.Failure(AppError.NotFound("CATEGORY_NOT_FOUND"))
        val vm = CategoryViewModel("toys", GetCategoryProductsUseCase(repository))
        advanceUntilIdle()
        assertEquals(LoadState.Failed(AppError.NotFound("CATEGORY_NOT_FOUND")), vm.state.value)

        repository.categoryProducts = DataResult.Success(CategoryProducts("toys", listOf(product(1)), emptyList()))
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.state.value is LoadState.Loaded)
    }
}
