package com.noman.mita

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MitaBrain(private val memoryManager: MemoryManager, private val actionHandler: ActionHandler) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    // Chat history memory
    private val chatHistory = mutableListOf<ChatMessage>()

    data class ChatMessage(val isUser: Boolean, val text: String)

    suspend fun processUserMessage(apiKey: String, userMessage: String): String = withContext(Dispatchers.IO) {
        val keys = apiKey.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) return@withContext "এপিআই কি (API Key) পাওয়া যায়নি। সেটিংসে গিয়ে Key দিন।"

        // Add to history
        chatHistory.add(ChatMessage(true, userMessage))

        val requestJson = buildGeminiRequest()
        var lastError = ""

        for (key in keys) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$key"
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody(mediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val mitaText = parseAndExecuteResponse(responseBody, key, userMessage)
                    chatHistory.add(ChatMessage(false, mitaText))
                    return@withContext mitaText
                } else {
                    lastError = response.code.toString()
                    // If Rate Limited (429) or Unauthorized (401), try the next key!
                    if (response.code == 429 || response.code == 401) {
                        continue
                    } else {
                        return@withContext "সার্ভার এরর: ${response.code}"
                    }
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Unknown Error"
                continue
            }
        }
        
        // If all keys failed
        return@withContext "দুঃখিত বন্ধু, এআই সার্ভারের সাথে সংযোগে সমস্যা হচ্ছে (Error: $lastError)। একটু পরে আবার চেষ্টা করো, অথবা সেটিংসে কমা (,) দিয়ে আরেকটি নতুন এপিআই কি যুক্ত করো।"
    }

    private fun buildGeminiRequest(): JsonObject {
        val root = JsonObject()

        // 1. System Instructions
        val systemInstruction = JsonObject()
        val sysParts = JsonArray()
        val sysText = JsonObject()

        val memoryContext = memoryManager.getMemorySummary()
        val prompt = """
            তুমি হলে 'মিতা' (Mita), একজন অত্যন্ত বন্ধুত্বপূর্ণ, চটপটে এবং হেল্পফুল পার্সোনাল এআই অ্যাসিস্ট্যান্ট। 
            তোমার ব্যবহারকারীর নাম 'নোমান'। তুমি নোমানকে 'তুমি' বা 'বন্ধু' বলে সম্বোধন করবে। 
            তোমার ব্যক্তিত্ব হবে একদম একজন সত্যিকারের বন্ধুর মতো।
            
            [নির্দেশনা]:
            ১. সব সময় বাংলায় উত্তর দেবে। 
            ২. খুব স্বাভাবিক এবং ইনফরমাল ভাষায় কথা বলবে (যেমন: 'কী অবস্থা বন্ধু?', 'অ্যালার্ম দিয়ে দিয়েছি!')।
            ৩. উত্তরগুলো স্বাভাবিক কথার মতো রাখবে (অতিরিক্ত দীর্ঘ না করে শ্রুতিমধুর ও প্রাণবন্ত)।
            ৪. নোমান যদি ফোনের কোনো কমান্ড দেয় (যেমন: ফ্ল্যাশলাইট, অ্যালার্ম, টাইমার, গুগল কিপ নোট, কল দেওয়া), তাহলে তুমি সংশ্লিষ্ট ফাংশন কল করবে।
            ৫. নোমান যদি নিজের সম্পর্কে কোনো নতুন তথ্য বা পছন্দ জানায়, তবে 'remember_user_fact' ফাংশন কল করে তা মনে রাখবে।
            
            [বর্তমান স্মৃতিভাণ্ডার]:
            $memoryContext
        """.trimIndent()

        sysText.addProperty("text", prompt)
        sysParts.add(sysText)
        systemInstruction.add("parts", sysParts)
        root.add("systemInstruction", systemInstruction)

        // 2. Contents (Chat History)
        val contents = JsonArray()
        
        // Take max 10 recent messages to prevent token limits (429)
        val recentHistory = chatHistory.takeLast(10)
        
        for (msg in recentHistory) {
            val contentObj = JsonObject()
            contentObj.addProperty("role", if (msg.isUser) "user" else "model")
            val parts = JsonArray()
            val userPart = JsonObject()
            userPart.addProperty("text", msg.text)
            parts.add(userPart)
            contentObj.add("parts", parts)
            contents.add(contentObj)
        }
        root.add("contents", contents)

        // 3. Tools / Function Declarations
        val tools = JsonArray()
        val toolObj = JsonObject()
        val functionDeclarations = JsonArray()

        functionDeclarations.add(createFunctionDef(
            "toggle_flashlight",
            "ফোনের ফ্ল্যাশলাইট বা টর্চ জ্বালানো অথবা নেভানো",
            listOf("enable" to "BOOLEAN")
        ))
        functionDeclarations.add(createFunctionDef(
            "set_alarm",
            "নির্দিষ্ট সময়ে অ্যালার্ম সেট করা",
            listOf("hour" to "INTEGER", "minute" to "INTEGER", "message" to "STRING")
        ))
        functionDeclarations.add(createFunctionDef(
            "set_timer",
            "নির্দিষ্ট সেকেন্ড বা মিনিটের জন্য কাউন্টডাউন টাইমার দেওয়া",
            listOf("seconds" to "INTEGER", "message" to "STRING")
        ))
        functionDeclarations.add(createFunctionDef(
            "save_to_keep",
            "গুগল কিপ (Google Keep) অথবা নোটস অ্যাপে কোনো নোট সংরক্ষণ করা",
            listOf("content" to "STRING")
        ))
        functionDeclarations.add(createFunctionDef(
            "dial_phone",
            "ফোনের কোনো নাম্বারে কল ডায়াল করা",
            listOf("phone_number" to "STRING")
        ))
        functionDeclarations.add(createFunctionDef(
            "remember_user_fact",
            "ব্যবহারকারীর কোনো নতুন ব্যক্তিগত পছন্দ, অভ্যাস বা তথ্য দীর্ঘমেয়াদী স্মৃতিতে সেভ করা",
            listOf("fact" to "STRING")
        ))

        toolObj.add("functionDeclarations", functionDeclarations)
        tools.add(toolObj)
        root.add("tools", tools)

        return root
    }

    private fun createFunctionDef(name: String, description: String, params: List<Pair<String, String>>): JsonObject {
        val def = JsonObject()
        def.addProperty("name", name)
        def.addProperty("description", description)
        val parameters = JsonObject()
        parameters.addProperty("type", "OBJECT")
        val properties = JsonObject()
        val required = JsonArray()

        for ((propName, propType) in params) {
            val prop = JsonObject()
            prop.addProperty("type", propType)
            properties.add(propName, prop)
            required.add(propName)
        }
        parameters.add("properties", properties)
        parameters.add("required", required)
        def.add("parameters", parameters)
        return def
    }

    private fun parseAndExecuteResponse(responseBody: String, apiKey: String, originalPrompt: String): String {
        try {
            val root = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = root.getAsJsonArray("candidates") ?: return "মিতা উত্তর তৈরি করতে পারেনি।"
            if (candidates.size() == 0) return "কিছু বলতে পারছি না বন্ধু।"

            val candidate = candidates[0].asJsonObject
            val content = candidate.getAsJsonObject("content")
            val parts = content.getAsJsonArray("parts")

            var regularText = ""
            for (part in parts) {
                val p = part.asJsonObject
                if (p.has("text")) {
                    regularText += p.get("text").asString + " "
                }
                if (p.has("functionCall")) {
                    val fn = p.getAsJsonObject("functionCall")
                    val fnName = fn.get("name").asString
                    val args = fn.getAsJsonObject("args")

                    val actionResult = when (fnName) {
                        "toggle_flashlight" -> {
                            val enable = args.get("enable").asBoolean
                            actionHandler.toggleFlashlight(enable)
                        }
                        "set_alarm" -> {
                            val hour = args.get("hour").asInt
                            val minute = args.get("minute").asInt
                            val msg = args.get("message")?.asString ?: "মিতা অ্যালার্ম"
                            actionHandler.setAlarm(hour, minute, msg)
                        }
                        "set_timer" -> {
                            val sec = args.get("seconds").asInt
                            val msg = args.get("message")?.asString ?: "মিতা টাইমার"
                            actionHandler.setTimer(sec, msg)
                        }
                        "save_to_keep" -> {
                            val text = args.get("content").asString
                            actionHandler.createKeepNote(text)
                        }
                        "dial_phone" -> {
                            val number = args.get("phone_number").asString
                            actionHandler.dialCall(number)
                        }
                        "remember_user_fact" -> {
                            val fact = args.get("fact").asString
                            memoryManager.saveFact(fact)
                            "মনে রাখলাম বন্ধু! তোমার এই তথ্যটি আমার স্মৃতিতে সেভ করে নিলাম।"
                        }
                        else -> ""
                    }
                    if (actionResult.isNotEmpty()) {
                        regularText = if (regularText.isNotBlank()) "$regularText\n$actionResult" else actionResult
                    }
                }
            }

            return regularText.trim().ifEmpty { "ঠিক আছে বন্ধু, কাজটি করে দিয়েছি!" }
        } catch (e: Exception) {
            return "রেসপন্স বুঝতে পারিনি বন্ধু: ${e.localizedMessage}"
        }
    }
}
