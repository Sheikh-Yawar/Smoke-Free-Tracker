package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletMuted
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.DividerColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown

data class CurrencyInfo(val symbol: String, val code: String, val name: String) {
    val displayName: String get() = "$symbol $name"
}

val CurrenciesList = listOf(
    CurrencyInfo("₹", "INR", "Indian Rupee"),
    CurrencyInfo("$", "USD", "US Dollar"),
    CurrencyInfo("€", "EUR", "Euro"),
    CurrencyInfo("£", "GBP", "British Pound"),
    CurrencyInfo("¥", "JPY", "Japanese Yen"),
    CurrencyInfo("$", "AUD", "Australian Dollar")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(
    selectedSymbol: String,
    onCurrencySelected: (CurrencyInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSelected = remember(selectedSymbol) {
        CurrenciesList.find { it.symbol == selectedSymbol } ?: CurrenciesList[0]
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currentSelected.symbol,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .width(88.dp)
                .testTag("currency_dropdown_field"),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textAlign = TextAlign.Center,
                fontSize = 22.sp,             // larger font so symbol is clearly visible
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface  // ensure not transparent/invisible
            ),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = VioletPrimary,
                    modifier = Modifier.size(20.dp)
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VioletPrimary,
                unfocusedBorderColor = DividerColor
            ),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(220.dp)
                .background(SurfaceCard)
        ) {
            CurrenciesList.forEach { currency ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currency.symbol,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = VioletPrimary,
                                modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = currency.code,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = currency.name,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    onClick = {
                        onCurrencySelected(currency)
                        expanded = false
                    },
                    modifier = Modifier.testTag("currency_item_${currency.code}")
                )
            }
        }
    }
}
