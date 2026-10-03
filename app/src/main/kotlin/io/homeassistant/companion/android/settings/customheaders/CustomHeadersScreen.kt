package io.homeassistant.companion.android.settings.customheaders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Delete
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.customheaders.CustomHeader.Validation

@Composable
internal fun CustomHeadersScreen(
    viewModel: CustomHeadersViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CustomHeadersContent(
        uiState = state,
        onBackClick = onBackClick,
        onAddHeader = viewModel::onAddHeader,
        onRemoveHeader = viewModel::onRemoveHeader,
        onNameChanged = viewModel::onNameChanged,
        onValueChanged = viewModel::onValueChanged,
        onSave = viewModel::onSave,
        modifier = modifier,
    )
}

@Composable
internal fun CustomHeadersContent(
    uiState: CustomHeadersUiState,
    onBackClick: () -> Unit,
    onAddHeader: () -> Unit,
    onRemoveHeader: (Int) -> Unit,
    onNameChanged: (Int, String) -> Unit,
    onValueChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The fragment is stacked on top of the server settings: be opaque and swallow touches so they don't fall through.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalHAColorScheme.current.colorSurfaceDefault)
            .pointerInput(Unit) {},
    ) {
        HATopBar(
            title = { Text(stringResource(commonR.string.custom_headers_title)) },
            onBackClick = onBackClick,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = HADimens.SPACE4),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        ) {
            item {
                Text(
                    text = stringResource(commonR.string.custom_headers_description),
                    style = HATextStyle.Body,
                )
            }
            items(uiState.rows, key = { it.id }) { row ->
                HeaderRowEditor(
                    row = row,
                    onNameChanged = { onNameChanged(row.id, it) },
                    onValueChanged = { onValueChanged(row.id, it) },
                    onRemove = { onRemoveHeader(row.id) },
                )
            }
            item {
                if (uiState.hasDuplicatedNames) {
                    Text(text = stringResource(commonR.string.custom_headers_error_duplicate), style = HATextStyle.Body)
                }
                HAPlainButton(
                    text = stringResource(commonR.string.custom_headers_add),
                    onClick = onAddHeader,
                )
            }
        }
        HAAccentButton(
            text = stringResource(commonR.string.save),
            onClick = onSave,
            enabled = uiState.canSave,
            modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE4),
        )
    }
}

@Composable
private fun HeaderRowEditor(
    row: HeaderRow,
    onNameChanged: (String) -> Unit,
    onValueChanged: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
            HATextField(
                value = row.name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(commonR.string.custom_headers_name)) },
                isError = row.validation == Validation.InvalidName || row.validation == Validation.ReservedName,
                supportingText = nameError(row.validation)?.let { { Text(stringResource(it)) } },
                singleLine = true,
            )
            // The value is a credential: it is always masked and has no reveal action
            HATextField(
                value = row.value,
                onValueChange = onValueChanged,
                label = { Text(stringResource(commonR.string.custom_headers_value)) },
                isError = row.validation == Validation.InvalidValue,
                supportingText = if (row.validation == Validation.InvalidValue) {
                    { Text(stringResource(commonR.string.custom_headers_error_value)) }
                } else {
                    null
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                singleLine = true,
            )
        }
        HAIconButton(
            icon = Mdi.Delete.rememberImageVector(),
            onClick = onRemove,
            contentDescription = stringResource(commonR.string.custom_headers_remove),
        )
    }
}

private fun nameError(validation: Validation): Int? = when (validation) {
    Validation.InvalidName -> commonR.string.custom_headers_error_name
    Validation.ReservedName -> commonR.string.custom_headers_error_reserved
    else -> null
}

@Preview
@Composable
private fun CustomHeadersContentPreview() {
    HAThemeForPreview {
        CustomHeadersContent(
            uiState = CustomHeadersUiState(
                isLoading = false,
                rows = listOf(
                    HeaderRow(0, "CF-Access-Client-Id", "id"),
                    HeaderRow(1, "Authorization", "x", Validation.ReservedName),
                ),
            ),
            onBackClick = {},
            onAddHeader = {},
            onRemoveHeader = {},
            onNameChanged = { _, _ -> },
            onValueChanged = { _, _ -> },
            onSave = {},
        )
    }
}
