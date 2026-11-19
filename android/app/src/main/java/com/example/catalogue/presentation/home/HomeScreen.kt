package com.example.catalogue.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.catalogue.domain.model.FeaturedProducts
import com.example.catalogue.presentation.common.CatalogueScaffold
import com.example.catalogue.presentation.common.EmptyState
import com.example.catalogue.presentation.common.ErrorState
import com.example.catalogue.presentation.common.LoadState
import com.example.catalogue.presentation.common.LoadingState
import com.example.catalogue.presentation.common.ProductCard
import com.example.catalogue.presentation.common.ProductGrid
import com.example.catalogue.presentation.common.SearchBox
import com.example.catalogue.presentation.common.SectionTitle
import com.example.catalogue.presentation.common.displayCategory
import com.example.catalogue.presentation.common.fullWidthItem
import com.example.catalogue.presentation.common.userMessage

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSearch: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onProductClick: (Int) -> Unit,
) {
    // Lifecycle-aware: stops collecting while the screen is not visible.
    val state by viewModel.state.collectAsStateWithLifecycle()

    CatalogueScaffold(title = "Product catalogue", onBack = null) { padding ->
        ProductGrid(padding) {
            fullWidthItem("search") {
                SearchBox(
                    query = state.searchQuery,
                    error = state.searchError,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onSearch = { viewModel.onSearchClicked()?.let(onSearch) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            fullWidthItem("categories") {
                Column {
                    SectionTitle("Shop by category")
                    Categories(state.categories, onCategoryClick, onRetry = viewModel::loadCategories)
                }
            }

            fullWidthItem("featured-title") { SectionTitle("Featured products") }

            when (val featured = state.featured) {
                LoadState.Loading -> fullWidthItem("featured-loading") { LoadingState("Loading featured products") }
                is LoadState.Failed -> fullWidthItem("featured-error") {
                    ErrorState(featured.error.userMessage(), onRetry = viewModel::loadFeatured)
                }
                is LoadState.Loaded -> {
                    val data = featured.data
                    if (data.isIncomplete) {
                        fullWidthItem("featured-notice") { IncompleteFeaturedNotice(data, viewModel::loadFeatured) }
                    }
                    if (data.products.isEmpty()) {
                        fullWidthItem("featured-empty") { EmptyState("No featured products are available right now.") }
                    } else {
                        items(data.products, key = { it.id }) { product ->
                            ProductCard(product, onClick = { onProductClick(product.id) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Categories(
    state: LoadState<List<String>>,
    onCategoryClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingState("Loading categories")
        is LoadState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Categories couldn't be loaded.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        is LoadState.Loaded ->
            if (state.data.isEmpty()) {
                Text("No categories available.", style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.data.forEach { category ->
                        SuggestionChip(
                            onClick = { onCategoryClick(category) },
                            label = { Text(displayCategory(category), style = MaterialTheme.typography.labelLarge) },
                        )
                    }
                }
            }
    }
}

/**
 * Shown when the backend reports configured featured products it could not find. The customer sees
 * honestly that fewer than the planned products are shown; product IDs mean nothing to them, so they
 * are not shown (the backend logs them for the business).
 */
@Composable
private fun IncompleteFeaturedNotice(data: FeaturedProducts, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Showing ${data.products.size} of ${data.configuredCount} featured products. " +
                    "Some are unavailable right now.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
