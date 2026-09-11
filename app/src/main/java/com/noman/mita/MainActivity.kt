package com.noman.mita

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

enum class MitaState {
    IDLE, LISTENING, THINKING, SPEAKING
}

class MainActivity : ComponentActivity(), VoiceListener {

    private lateinit var memoryManager: MemoryManager
    private lateinit var actionHandler: ActionHandler
    private lateinit var mitaBrain: MitaBrain
    private lateinit var voiceManager: VoiceManager
    private lateinit var prefs: SharedPreferences

    private val mitaState = mutableStateOf(MitaState.IDLE)
    private val lastUserText = mutableStateOf("")
    private val mitaResponseText = mutableStateOf("হাই বন্ধু! আমি মিতা। তোমার সাথে আড্ডা দিতে এবং সাহায্য করতে আমি প্রস্তুত।")
    private val apiKey = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("mita_settings", Context.MODE_PRIVATE)
        apiKey.value = prefs.getString("gemini_api_key", "") ?: ""

        memoryManager = MemoryManager(this)
        actionHandler = ActionHandler(this)
        mitaBrain = MitaBrain(memoryManager, actionHandler)
        voiceManager = VoiceManager(this, this)

        setContent {
            MitaAppUI(
                state = mitaState.value,
                userText = lastUserText.value,
                mitaText = mitaResponseText.value,
                apiKey = apiKey.value,
                onApiKeySave = { key ->
                    apiKey.value = key
                    prefs.edit().putString("gemini_api_key", key).apply()
                },
                onMicClick = { toggleListening() },
                onSendMessage = { text -> handleUserMessage(text) },
                onStopSpeaking = { voiceManager.stopSpeaking() }
            )
        }
    }

    private fun toggleListening() {
        if (mitaState.value == MitaState.LISTENING) {
            voiceManager.stopListening()
        } else {
            // Check Audio permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                voiceManager.startListening()
            }
        }
    }

    private fun handleUserMessage(text: String) {
        if (text.isBlank()) return
        lastUserText.value = text
        mitaState.value = MitaState.THINKING
        voiceManager.stopSpeaking()

        // Call Gemini Coroutine
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        scope.launch {
            val response = mitaBrain.processUserMessage(apiKey.value, text)
            mitaResponseText.value = response
            mitaState.value = MitaState.SPEAKING
            voiceManager.speak(response)
        }
    }

    override fun onListeningStateChanged(isListening: Boolean) {
        runOnUiThread {
            if (isListening) {
                mitaState.value = MitaState.LISTENING
            } else if (mitaState.value == MitaState.LISTENING) {
                mitaState.value = MitaState.IDLE
            }
        }
    }

    override fun onSpeechResult(text: String) {
        runOnUiThread {
            handleUserMessage(text)
        }
    }

    override fun onSpeechError(errorMessage: String) {
        runOnUiThread {
            mitaState.value = MitaState.IDLE
            mitaResponseText.value = errorMessage
        }
    }

    override fun onSpeakingStateChanged(isSpeaking: Boolean) {
        runOnUiThread {
            mitaState.value = if (isSpeaking) MitaState.SPEAKING else MitaState.IDLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.destroy()
    }
}

@Composable
fun MitaAppUI(
    state: MitaState,
    userText: String,
    mitaText: String,
    apiKey: String,
    onApiKeySave: (String) -> Unit,
    onMicClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStopSpeaking: () -> Unit
) {
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    var inputApiKey by remember { mutableStateOf(apiKey) }
    var manualText by remember { mutableStateOf("") }

    // Request permissions launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B0F19)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (state) {
                                    MitaState.IDLE -> Color(0xFF00E5FF)
                                    MitaState.LISTENING -> Color(0xFFFF4081)
                                    MitaState.THINKING -> Color(0xFFFFAB00)
                                    MitaState.SPEAKING -> Color(0xFF00E676)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "মিতা (Mita AI)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9)
                    )
                }

                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            // 2. Center: Glowing Sci-Fi Interactive Orb
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                MitaGlowingOrb(state = state, onClick = onMicClick)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when (state) {
                        MitaState.IDLE -> "কথা বলতে মাইকে চাপ দাও, বন্ধু!"
                        MitaState.LISTENING -> "মিতা মন দিয়ে শুনছে..."
                        MitaState.THINKING -> "মিতা ভেবে দেখছে..."
                        MitaState.SPEAKING -> "মিতা কথা বলছে... (থামাতে চাপুন)"
                    },
                    fontSize = 15.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            // 3. Conversation Card & Quick Chips
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Quick Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickChip("🔦 ফ্ল্যাশলাইট জ্বালাও") { onSendMessage("ফ্ল্যাশলাইট জ্বালাও") }
                    QuickChip("⏰ সকাল ৭টায় অ্যালার্ম") { onSendMessage("কাল সকাল ৭টায় অ্যালার্ম দিয়ে দাও") }
                    QuickChip("📝 কিপে নোট রাখো") { onSendMessage("গুগল কিপে লিখে রাখো: কালকে বইমেলায় যেতে হবে") }
                    QuickChip("❤️ কেমন আছো মিতা?") { onSendMessage("কেমন আছো বন্ধু? আজকে কী করছো?") }
                    QuickChip("🔋 ব্যাটারি কত আছে?") { onSendMessage("আমার ফোনের ব্যাটারি ও বর্তমান সময় বলো") }
                }

                // Chat Message Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (userText.isNotBlank()) {
                            Text(
                                text = "তুমি: $userText",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF00E5FF)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = mitaText,
                            fontSize = 15.sp,
                            color = Color(0xFFF1F5F9),
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Text Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        placeholder = { Text("বাংলায় কিছু লিখো...", color = Color(0xFF64748B)) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (manualText.isNotBlank()) {
                                onSendMessage(manualText)
                                manualText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF00E5FF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color(0xFF0B0F19)
                        )
                    }
                }
            }
        }

        // Settings Dialog for API Key
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { if (apiKey.isNotBlank()) showSettingsDialog = false },
                title = { Text("Gemini API Key সেটআপ", color = Color.White) },
                text = {
                    Column {
                        Text(
                            "মিতার ব্রেন সক্রিয় করার জন্য একটি ফ্রি Gemini API Key প্রয়োজন (Google AI Studio থেকে ফ্রিতে পাওয়া যায়):",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputApiKey,
                            onValueChange = { inputApiKey = it },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onApiKeySave(inputApiKey.trim())
                            showSettingsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("সংরক্ষণ করুন", color = Color(0xFF0B0F19))
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}

@Composable
fun QuickChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = Color(0xFFE2E8F0), fontSize = 12.sp)
    }
}

@Composable
fun MitaGlowingOrb(state: MitaState, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (state == MitaState.LISTENING || state == MitaState.SPEAKING) 0.95f else 1f,
        targetValue = if (state == MitaState.LISTENING || state == MitaState.SPEAKING) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == MitaState.LISTENING) 700 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val coreColor by animateColorAsState(
        targetValue = when (state) {
            MitaState.IDLE -> Color(0xFF00E5FF)
            MitaState.LISTENING -> Color(0xFFFF4081)
            MitaState.THINKING -> Color(0xFFFFAB00)
            MitaState.SPEAKING -> Color(0xFF7C4DFF)
        },
        animationSpec = tween(durationMillis = 500),
        label = "orbColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .clickable { onClick() }
    ) {
        // Outer Glow Aura
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.45f),
                            coreColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Middle Glowing Ring
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(if (state == MitaState.SPEAKING) pulseScale * 1.05f else 1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.8f),
                            coreColor.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Inner Core Orb with Mic Icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(90.dp)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            coreColor,
                            coreColor.copy(alpha = 0.7f)
                        )
                    )
                )
        ) {
            Icon(
                imageVector = if (state == MitaState.LISTENING) Icons.Default.Mic else Icons.Default.Mic,
                contentDescription = "Mic",
                tint = Color(0xFF0B0F19),
                modifier = Modifier.size(42.dp)
            )
        }
    }
}
