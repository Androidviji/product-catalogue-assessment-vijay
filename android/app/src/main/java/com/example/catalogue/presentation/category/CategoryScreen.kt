package com.example.catalogue.presentation.category

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.catalogue.presentation.common.CatalogueScaffold
import com.example.catalogue.presentation.common.EmptyState
import com.example.catalogue.presentation.common.ErrorState
import com.example.catalogue.presentation.common.LoadState
import com.example.catalogue.presentation.common.LoadingState
import com.example.catalogue.presentation.common.ProductCard
import com.example.catalogue.presentation.common.ProductGrid
import com.example.catalogue.presentation.common.SectionTitle
import com.example.catalogue.presentation.common.displayCategory
import com.example.catalogue.presentation.common.fullWidthItem
import com.example.catalogue.presentation.common.userMessage

/** Top-rated and other products in two sections, in the order the backend returned them. */
@Composable
fun CategoryScreen(
    viewModel: CategoryViewModel,
    onProductClick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CatalogueScaffold(title = displayCategory(viewModel.categoryName), onBack = onBack) { padding ->
        ProductGrid(padding) {
            when (val s = state) {
                LoadState.Loading -> fullWidthItem { LoadingState("Loading products") }
                is LoadState.Failed -> fullWidthItem { ErrorState(s.error.userMessage(), onRetry = viewModel::load) }
                is LoadState.Loaded -> {
                    val data = s.data
                    if (data.topRated.isEmpty() && data.otherProducts.isEmpty()) {
                        fullWidthItem { EmptyState("There are no products in this category.") }
                    } else {
                        fullWidthItem("top-title") { SectionTitle("Top rated") }
                        // Keys are prefixed per section; the backend guarantees no product is in both.
                        items(data.topRated, key = { "top-${it.id}" }) {
                            ProductCard(it, onClick = { onProductClick(it.id) })
                        }
                        if (data.otherProducts.isNotEmpty()) {
                            fullWidthItem("other-title") { SectionTitle("More in ${displayCategory(data.category)}") }
                            items(data.otherProducts, key = { "other-${it.id}" }) {
                                ProductCard(it, onClick = { onProductClick(it.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
