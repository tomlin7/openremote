package com.tom7.openremote.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tom7.openremote.data.FormCondition
import com.tom7.openremote.data.FormField
import com.tom7.openremote.data.FormInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormSheet(
    form: FormInfo,
    onDismiss: () -> Unit,
    onSubmit: (Map<String, JsonElement>) -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val answers = remember(form.id) {
        mutableStateMapOf<String, JsonElement>().apply {
            form.fields.forEach { field ->
                field.default?.takeIf { it !is JsonNull }?.let { put(field.key, it) }
            }
        }
    }
    val custom = remember(form.id) { mutableStateMapOf<String, String>() }
    var error by remember(form.id) { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                form.title.ifBlank { "Input requested" },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "OpenCode needs an answer before it can continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            form.fields.forEach { field ->
                if (!fieldActive(field, answers) || field.hidden == true) return@forEach
                FieldInput(
                    field = field,
                    answer = answers[field.key],
                    customValue = custom[field.key].orEmpty(),
                    onAnswer = { value ->
                        if (value == null) answers.remove(field.key) else answers[field.key] = value
                        error = null
                    },
                    onCustomChange = { custom[field.key] = it },
                )
            }

            form.fields
                .firstOrNull { it.type == "external" }
                ?.let { external ->
                    val uriHandler = LocalUriHandler.current
                    OutlinedButton(
                        onClick = { runCatching { uriHandler.openUri(external.url.orEmpty()) } },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open authorisation page")
                    }
                }

            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val submitted = collectAnswers(form, answers, custom)
                        val missing = missingRequired(form, answers, custom)
                        if (missing.isNotEmpty()) {
                            error = "Answer: ${missing.joinToString(", ")}"
                            return@Button
                        }
                        onSubmit(submitted)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Submit")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldInput(
    field: FormField,
    answer: JsonElement?,
    customValue: String,
    onAnswer: (JsonElement?) -> Unit,
    onCustomChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.label + if (field.required == true) " *" else "",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        field.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))

        when (field.type) {
            "string" -> {
                val options = field.options
                if (options.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            val selected = (answer as? JsonPrimitive)?.contentOrNull == option.value
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) onAnswer(null) else onAnswer(JsonPrimitive(option.value))
                                },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
                if (options.isEmpty() || field.custom == true) {
                    OutlinedTextField(
                        value = if (options.isEmpty()) {
                            (answer as? JsonPrimitive)?.contentOrNull.orEmpty()
                        } else {
                            customValue
                        },
                        onValueChange = { value ->
                            if (options.isEmpty()) {
                                onAnswer(value.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) })
                            } else {
                                onCustomChange(value)
                                onAnswer(value.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) })
                            }
                        },
                        placeholder = { field.placeholder?.let { Text(it) } },
                        singleLine = field.format != "date-time",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (field.format) {
                                "email" -> KeyboardType.Email
                                "uri" -> KeyboardType.Uri
                                else -> KeyboardType.Text
                            },
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            "number", "integer" -> {
                OutlinedTextField(
                    value = (answer as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    onValueChange = { value ->
                        val cleaned = if (field.type == "integer") {
                            value.filter { it.isDigit() || it == '-' }
                        } else {
                            value.filter { it.isDigit() || it == '-' || it == '.' }
                        }
                        onAnswer(cleaned.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) })
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.type == "integer") KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                val range = listOfNotNull(
                    field.minimum?.let { "min ${trimNumber(it)}" },
                    field.maximum?.let { "max ${trimNumber(it)}" },
                )
                if (range.isNotEmpty()) {
                    Text(
                        range.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            "boolean" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if ((answer as? JsonPrimitive)?.booleanOrNull == true) "Yes" else "No",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = (answer as? JsonPrimitive)?.booleanOrNull == true,
                        onCheckedChange = { onAnswer(JsonPrimitive(it)) },
                    )
                }
            }

            "multiselect" -> {
                val selected = (answer as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    field.options.forEach { option ->
                        val isOn = option.value in selected
                        FilterChip(
                            selected = isOn,
                            onClick = {
                                val next = if (isOn) selected - option.value else selected + option.value
                                onAnswer(if (next.isEmpty()) null else JsonArray(next.map { JsonPrimitive(it) }))
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                if (field.custom == true) {
                    OutlinedTextField(
                        value = customValue,
                        onValueChange = onCustomChange,
                        placeholder = { Text("Other") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            }
        }
        if (field.type != "external") {
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun fieldActive(field: FormField, answers: Map<String, JsonElement>): Boolean =
    field.conditions.orEmpty().all { conditionHolds(it, answers) }

private fun conditionHolds(condition: FormCondition, answers: Map<String, JsonElement>): Boolean {
    val answer = answers[condition.key] ?: return false
    val expected = condition.value
    val matched = when {
        answer is JsonArray && expected is JsonPrimitive -> {
            val target = expected.contentOrNull ?: return false
            answer.any { (it as? JsonPrimitive)?.contentOrNull == target }
        }

        answer is JsonPrimitive && expected is JsonPrimitive -> {
            when {
                answer.isString || expected.isString -> answer.contentOrNull == expected.contentOrNull
                answer.booleanOrNull != null || expected.booleanOrNull != null ->
                    answer.booleanOrNull == expected.booleanOrNull

                else -> answer.contentOrNull?.toDoubleOrNull() == expected.contentOrNull?.toDoubleOrNull()
            }
        }

        else -> false
    }
    return if (condition.op == "eq") matched else !matched
}

private fun missingRequired(
    form: FormInfo,
    answers: Map<String, JsonElement>,
    custom: Map<String, String>,
): List<String> {
    return form.fields.mapNotNull { field ->
        if (field.type == "external" || field.hidden == true) return@mapNotNull null
        if (!fieldActive(field, answers)) return@mapNotNull null
        val value = answers[field.key]
        val hasValue = when {
            value == null || value is JsonNull -> false
            value is JsonArray -> value.isNotEmpty() || !custom[field.key].isNullOrBlank()
            value is JsonPrimitive -> value.contentOrNull?.isNotBlank() == true
            else -> true
        }
        if (field.required == true && !hasValue) field.label else null
    }
}

private fun collectAnswers(
    form: FormInfo,
    answers: Map<String, JsonElement>,
    custom: Map<String, String>,
): Map<String, JsonElement> {
    val result = linkedMapOf<String, JsonElement>()
    form.fields.forEach { field ->
        if (field.type == "external" || field.hidden == true) return@forEach
        if (!fieldActive(field, answers)) return@forEach
        val value = answers[field.key]
        if (value == null || value is JsonNull) return@forEach

        if (field.type == "multiselect") {
            val values = (value as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .orEmpty()
                .toMutableList()
            custom[field.key]?.takeIf { it.isNotBlank() }?.let { values.add(it.trim()) }
            if (values.isNotEmpty()) {
                result[field.key] = JsonArray(values.distinct().map { JsonPrimitive(it) })
            }
            return@forEach
        }

        if (value is JsonPrimitive && value.contentOrNull.isNullOrBlank()) return@forEach
        result[field.key] = value
    }
    return result
}

private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
