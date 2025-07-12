package com.techfifo.crm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallReceiver : BroadcastReceiver() {

    companion object {
        var recorder: MediaRecorder? = null
        var isRecording = false
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d("CallReceiver", "📞 Call started")
                startRecording(context)
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d("CallReceiver", "📴 Call ended")
                stopRecording(context)
            }
        }
    }

    private fun startRecording(context: Context) {
        if (isRecording) return

        val androidVersion = Build.VERSION.SDK_INT
        val manufacturer = Build.MANUFACTURER.lowercase()

        Log.d("CallReceiver", "🔍 Manufacturer: $manufacturer | Android version: $androidVersion")

        if (androidVersion >= Build.VERSION_CODES.Q) {
            Log.w("CallReceiver", "🚫 Android 10+ blocks call audio recording for 3rd-party apps.")
        }

        if (manufacturer.contains("samsung") || manufacturer.contains("xiaomi") || manufacturer.contains("oppo")) {
            Log.w("CallReceiver", "⚠️ Recording may be blocked on $manufacturer devices.")
        }

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "call_recording_$timeStamp.m4a"
            val file = File(context.cacheDir, fileName)

            Log.d("CallReceiver", "🎙 Preparing to record. File path: ${file.absolutePath}")

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            context.getSharedPreferences("call_prefs", Context.MODE_PRIVATE).edit()
                .putString("current_path", file.absolutePath).apply()

            Log.d("CallReceiver", "✅ Recording started: ${file.absolutePath}")
            Log.d("CallReceiver", "🔊 Audio source used: ${MediaRecorder.AudioSource.VOICE_COMMUNICATION}")

        } catch (e: Exception) {
            Log.e("CallReceiver", "❌ Recording failed: ${e.message}")
            recorder?.release()
            recorder = null
            isRecording = false
        }
    }

    private fun stopRecording(context: Context) {
        try {
            recorder?.apply {
                stop()
                release()
            }

            val path = context.getSharedPreferences("call_prefs", Context.MODE_PRIVATE)
                .getString("current_path", null)

            val recordedFile = File(path ?: "")
            Log.d("CallReceiver", "🛑 Recording stopped. File saved: $path")
            Log.d("CallReceiver", "📂 File size: ${recordedFile.length()} bytes")

        } catch (e: Exception) {
            Log.e("CallReceiver", "❌ Stop error: ${e.message}")
        } finally {
            recorder = null
            isRecording = false
        }
    }
}
