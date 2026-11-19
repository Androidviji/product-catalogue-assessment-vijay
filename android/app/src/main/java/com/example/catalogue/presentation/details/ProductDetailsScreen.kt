package com.example.catalogue.presentation.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.catalogue.domain.model.Product
import com.example.catalogue.presentation.common.CatalogueScaffold
import com.example.catalogue.presentation.common.ErrorState
import com.example.catalogue.presentation.common.LoadState
import com.example.catalogue.presentation.common.LoadingState
import com.example.catalogue.presentation.common.ProductImage
import com.example.catalogue.presentation.common.RatingText
import com.example.catalogue.presentation.common.displayCategory
import com.example.catalogue.presentation.common.formatPrice
import com.example.catalogue.presentation.common.userMessage

@Composable
fun ProductDetailsScreen(
    viewModel: ProductDetailsViewModel,
    onEnquire: (Product) -> Unit,
    onCategoryClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CatalogueScaffold(title = "Product details", onBack = onBack) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                LoadState.Loading -> LoadingState("Loading product")
                is LoadState.Failed -> ErrorState(s.error.userMessage(), onRetry = viewModel::load)
                is LoadState.Loaded -> ProductDetails(s.data, onEnquire, onCategoryClick)
            }
        }
    }
}

@Composable
private fun ProductDetails(
    product: Product,
    onEnquire: (Product) -> Unit,
    onCategoryClick: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        ProductImage(
            product,
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(12.dp))
                .padding(8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(product.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(
            formatPrice(product.price),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        RatingText(product)
        Spacer(Modifier.height(8.dp))
        AssistChip(
            onClick = { onCategoryClick(product.category) },
            label = { Text("More in ${displayCategory(product.category)}") },
        )
        Spacer(Modifier.height(16.dp))
        Text(product.description, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onEnquire(product) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text("Enquire about this product")
        }
    }
}
