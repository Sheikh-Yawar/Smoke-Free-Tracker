package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    val selectedIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex
        }
    }
    
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            onItemSelected(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .height(120.dp)
            .width(80.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 40.dp)
        ) {
            items(items.size) { index ->
                val isSelected = index == selectedIndex
                val scale = if (isSelected) 1.2f else 0.8f
                val alpha = if (isSelected) 1.0f else 0.4f
                val color = if (isSelected) VioletPrimary else TextSecondary
                val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                val fontSize = if (isSelected) 22.sp else 16.sp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        color = color,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        modifier = Modifier
                            .scale(scale)
                            .alpha(alpha)
                    )
                }
            }
        }

        // Thin VioletMuted border framing the center row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(1.dp, VioletMuted, RoundedCornerShape(8.dp))
        )
        
        // Faded top/bottom gradients mapping smoothly
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SurfaceElevated, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, SurfaceElevated)
                    )
                )
        )
    }
}
