package com.minima.os.ui.launcher

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.os.data.memory.ContextEngine

@Composable
fun OnboardingFlow(
    questions: List<ContextEngine.OnboardingQuestion>,
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf("") }

    if (currentIndex >= questions.size) {
        onSkip()
        return
    }

    val question = questions[currentIndex]

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFF7C6FED),
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // "Let me get to know you"
        if (currentIndex == 0) {
            Text(
                "Let me get to know you",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Answer a few questions so I can personalize your experience",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Progress dots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            questions.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentIndex) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (index <= currentIndex) Color(0xFF7C6FED)
                            else Color.White.copy(alpha = 0.2f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Question
        Text(
            question.question,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (answer.isEmpty()) {
                Text(
                    "Type your answer...",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
            BasicTextField(
                value = answer,
                onValueChange = { answer = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(Color(0xFF7C6FED)),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Skip
            TextButton(
                onClick = {
                    answer = ""
                    if (currentIndex < questions.size - 1) {
                        currentIndex++
                    } else {
                        onSkip()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }

            // Next
            Button(
                onClick = {
                    if (answer.isNotBlank()) {
                        onAnswer(question.prefix + answer.trim())
                        answer = ""
                        if (currentIndex < questions.size - 1) {
                            currentIndex++
                        } else {
                            onSkip()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C6FED)
                ),
                enabled = answer.isNotBlank()
            ) {
                Text(
                    if (currentIndex == questions.size - 1) "Done" else "Next",
                    fontSize = 13.sp
                )
            }
        }
    }
}
