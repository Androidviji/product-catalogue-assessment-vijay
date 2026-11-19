package com.example.catalogue.presentation.enquiry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.catalogue.domain.model.EnquiryField
import com.example.catalogue.domain.usecase.EnquiryValidator
import com.example.catalogue.presentation.common.CatalogueScaffold
import com.example.catalogue.presentation.common.userMessage

@Composable
fun EnquiryScreen(
    viewModel: EnquiryViewModel,
    productTitle: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CatalogueScaffold(title = "Product enquiry", onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.sentEnquiryId != null) {
                ThankYou(state, productTitle, onDone)
            } else {
                EnquiryForm(state, productTitle, viewModel)
            }
        }
    }
}

@Composable
private fun EnquiryForm(state: EnquiryUiState, productTitle: String, viewModel: EnquiryViewModel) {
    val editable = !state.isSubmitting

    Text("Ask about", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(productTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(4.dp))
    Text(
        "Our sales team will reply by email. Fields marked * are required.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Your name *") },
        singleLine = true,
        enabled = editable,
        isError = EnquiryField.Name in state.fieldErrors,
        supportingText = state.fieldErrors[EnquiryField.Name]?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("Email address *") },
        singleLine = true,
        enabled = editable,
        isError = EnquiryField.Email in state.fieldErrors,
        supportingText = state.fieldErrors[EnquiryField.Email]?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.message,
        onValueChange = viewModel::onMessageChange,
        label = { Text("Message (optional)") },
        minLines = 4,
        enabled = editable,
        isError = EnquiryField.Message in state.fieldErrors,
        supportingText = {
            Text(state.fieldErrors[EnquiryField.Message] ?: "${state.message.length} / ${EnquiryValidator.MAX_MESSAGE}")
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )

    when (val problem = state.submitProblem) {
        null -> Unit
        // Certain failure: safe to send again.
        is SubmitProblem.NotSent -> ProblemCard(
            title = "Your enquiry was not sent",
            lines = listOf(problem.error.userMessage(), "Your details are still here. Tap Send enquiry to try again."),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        // Unknown outcome: the email may already be with the sales team, so do not imply it failed.
        SubmitProblem.Unconfirmed -> ProblemCard(
            title = "We couldn't confirm your enquiry",
            lines = listOf(
                "Something went wrong before we could confirm whether your enquiry reached our " +
                    "sales team. It may already have been sent.",
                "Your details are still here. If you send it again, the sales team may receive it twice.",
            ),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = viewModel::submit,
        enabled = editable,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        if (state.isSubmitting) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Sending…")
            }
        } else {
            // After an unconfirmed send, make it clear that tapping sends the enquiry again.
            Text(if (state.submitProblem == SubmitProblem.Unconfirmed) "Send again" else "Send enquiry")
        }
    }
}

@Composable
private fun ProblemCard(title: String, lines: List<String>, containerColor: Color, contentColor: Color) {
    Spacer(Modifier.height(8.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Shown only after the backend confirmed the email was accepted by the mail server. */
@Composable
private fun ThankYou(state: EnquiryUiState, productTitle: String, onDone: () -> Unit) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("Thank you, ${state.name.trim()}!", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(
            "Your enquiry about $productTitle has been sent to our sales team. " +
                "They will reply to ${state.email.trim()}.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Reference: ${state.sentEnquiryId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text("Back to product")
    }
}
