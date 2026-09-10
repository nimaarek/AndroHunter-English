package com.androhunter.app.ui.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androhunter.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var logoAlpha by remember { mutableStateOf(0f) }

    val animAlpha by animateFloatAsState(
        targetValue = logoAlpha,
        animationSpec = tween(1200),
        label = "alpha"
    )

    // Pulse animation for crosshair
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    LaunchedEffect(Unit) {
        logoAlpha = 1f
        delay(2200)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(HunterBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // ASCII Logo + Crosshair
            Box(
                Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .alpha(animAlpha)
                    .border(2.dp, HunterGreen, RoundedCornerShape(60.dp))
                    .background(HunterCard, RoundedCornerShape(60.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⊕", color = HunterGreen, fontSize = 48.sp)
                }
            }

            Text(
                "ANDROHUNTER",
                color      = HunterGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize   = 28.sp,
                modifier   = Modifier.alpha(animAlpha)
            )
            Text(
                "Android Security Toolkit v2.0",
                color      = HunterTextDim,
                fontFamily = FontFamily.Monospace,
                fontSize   = 13.sp,
                modifier   = Modifier.alpha(animAlpha)
            )
        }

        // Scanline effect
        repeat(20) { i ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .offset(y = (i * 40).dp)
                    .alpha(0.03f)
                    .background(HunterGreen)
            )
        }
    }
}
