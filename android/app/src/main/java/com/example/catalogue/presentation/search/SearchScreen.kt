package com.example.catalogue.presentation.search

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.catalogue.presentation.common.CatalogueScaffold
import com.example.catalogue.presentation.common.EmptyState
import com.example.catalogue.presentation.common.ErrorState
import com.example.catalogue.presentation.common.LoadingState
import com.example.catalogue.presentation.common.ProductCard
import com.example.catalogue.presentation.common.ProductGrid
import com.example.catalogue.presentation.common.SearchBox
import com.example.catalogue.presentation.common.fullWidthItem
import com.example.catalogue.presentation.common.userMessage

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onProductClick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CatalogueScaffold(title = "Search", onBack = onBack) { padding ->
        ProductGrid(padding) {
            fullWidthItem("search") {
                SearchBox(
                    query = state.query,
                    error = state.queryError,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = viewModel::search,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            when (val results = state.results) {
                SearchResultsState.Idle -> Unit
                is SearchResultsState.Searching -> fullWidthItem("loading") { LoadingState("Searching for ${results.query}") }
                is SearchResultsState.Failed -> fullWidthItem("error") {
                    ErrorState(results.error.userMessage(), onRetry = viewModel::retry)
                }
                is SearchResultsState.NoMatches -> fullWidthItem("empty") {
                    EmptyState("No products match \"${results.query}\". Try a different word or a category name.")
                }
                is SearchResultsState.Found -> {
                    fullWidthItem("count") {
                        val count = results.products.size
                        Text(
                            "$count ${if (count == 1) "result" else "results"} for \"${results.query}\"",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    items(results.products, key = { it.id }) {
                        ProductCard(it, onClick = { onProductClick(it.id) })
                    }
                }
            }
        }
    }
}
