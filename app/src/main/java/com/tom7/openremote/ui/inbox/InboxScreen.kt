package com.tom7.openremote.ui.inbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tom7.openremote.AppViewModel
import com.tom7.openremote.RealtimeStatus
import com.tom7.openremote.data.OpenCodeApi
import com.tom7.openremote.data.SessionInfo
import com.tom7.openremote.ui.pickers.ModelPickerSheet

@Composable
fun InboxScreen(
    api: OpenCodeApi,
    app: AppViewModel,
    onOpenSession: (SessionInfo) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = viewModel(
        key = "inbox:${app.apiKey}",
        factory = viewModelFactory {
            initializer { InboxViewModel(api) }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val realtime by app.realtime.collectAsStateWithLifecycle()
    val delivery by app.defaultDelivery.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    var draft by rememberSaveable { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    val selected = state.selectedModel
    val model = state.models.firstOrNull {
        it.modelID == selected?.id && it.providerID == selected?.providerID
    }
    val providerName = state.providers.firstOrNull { it.id == selected?.providerID }?.name
    val modelLabel = listOfNotNull(
        providerName?.takeIf { it.isNotBlank() },
        model?.displayName ?: selected?.id,
    ).joinToString(" › ").ifBlank { "Select model" }
    val variantLabel = selected?.variant?.replaceFirstChar { it.uppercaseChar() }

    val sendEnabled = draft.isNotBlank() && !state.sending && state.directory.isNotBlank()
    val send: () -> Unit = {
        val text = draft.trim()
        if (text.isNotEmpty() && !state.sending) {
            viewModel.send(text, delivery) { session ->
                draft = ""
                onOpenSession(session)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "openremote",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        letterSpacing = 2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                    maxLines = 1,
                )

                Spacer(Modifier.height(26.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 62.dp)
                                .focusRequester(focusRequester)
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            decorationBox = { inner ->
                                if (draft.isEmpty()) {
                                    Text(
                                        text = "Ask anything, / for commands, @ for context…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            },
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { focusRequester.requestFocus() },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Add,
                                        contentDescription = "Composer actions",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showModelPicker = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = modelLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Choose model",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            if (variantLabel != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(9.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
                                    ),
                                    modifier = Modifier.clickable { showModelPicker = true },
                                ) {
                                    Text(
                                        text = variantLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }

                            Surface(
                                color = if (sendEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable(enabled = sendEnabled, onClick = send),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (state.sending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.ArrowUpward,
                                            contentDescription = "Send",
                                            tint = if (sendEnabled) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                ContextRow(
                    projectName = state.projects
                        .firstOrNull { it.canonical == state.directory }
                        ?.displayName
                        ?.takeIf { it.isNotBlank() }
                        ?: state.directory.trimEnd('\\', '/')
                            .substringAfterLast('\\')
                            .substringAfterLast('/')
                            .ifBlank { "openremote" },
                    live = realtime == RealtimeStatus.Live,
                    branch = state.branch,
                )
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = state.models,
            providers = state.providers,
            selected = state.selectedModel,
            onDismiss = { showModelPicker = false },
            onSelect = { model ->
                showModelPicker = false
                viewModel.selectModel(model)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextRow(projectName: String, live: Boolean, branch: String?) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ContextChip(
            icon = { tint ->
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
            },
            label = projectName,
        )
        ContextChip(
            icon = { tint ->
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
            },
            label = "Local",
            trailing = if (live) {
                {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.tertiary, shape = RoundedCornerShape(3.dp)),
                    )
                }
            } else null,
        )
        branch?.let {
            ContextChip(
                icon = { tint ->
                    Icon(
                        Icons.AutoMirrored.Outlined.CallSplit,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(13.dp),
                    )
                },
                label = it,
            )
        }
    }
}

@Composable
private fun ContextChip(
    icon: @Composable (Color) -> Unit,
    label: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon(MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }
    }
}
