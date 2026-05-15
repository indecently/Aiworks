package com.birkneo.Aiworks.ui.settings.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun InferenceParameterRow(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    isInteger: Boolean = false,
    onValueChange: (Float) -> Unit
) {
    val view = LocalView.current
    var textValue by remember(value) { 
        mutableStateOf(if (isInteger) value.toInt().toString() else String.format(Locale.US, "%.2f", value)) 
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(text = description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            
            OutlinedTextField(
                value = textValue,
                onValueChange = { 
                    textValue = it
                    it.toFloatOrNull()?.let { newValue ->
                        if (newValue in valueRange) {
                            onValueChange(newValue)
                        }
                    }
                },
                modifier = Modifier.width(80.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            )
        }
        
        Slider(
            value = value,
            onValueChange = {
                if (it != value) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onValueChange(it)
                }
            },
            valueRange = valueRange,
            modifier = Modifier.height(24.dp)
        )
    }
}
