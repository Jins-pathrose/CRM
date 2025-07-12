package com.techfifo.crm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Environment
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
                Log.d("CallReceiver", "Call started")
                startRecording(context)
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d("CallReceiver", "Call ended")
                stopRecording(context)
            }
        }
    }

    private fun startRecording(context: Context) {
        try {
            if (isRecording) return

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "call_recording_$timeStamp.m4a"
            val file = File(context.cacheDir, fileName)

            // Save path persistently
            context.getSharedPreferences("call_prefs", Context.MODE_PRIVATE).edit()
                .putString("current_path", file.absolutePath).apply()

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.d("CallReceiver", "Recording started: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("CallReceiver", "Recording error: ${e.message}")
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

            // Retrieve path from SharedPreferences
            val path = context.getSharedPreferences("call_prefs", Context.MODE_PRIVATE)
                .getString("current_path", null)

            Log.d("CallReceiver", "Recording stopped: $path")

        } catch (e: Exception) {
            Log.e("CallReceiver", "Stop error: ${e.message}")
        } finally {
            recorder = null
            isRecording = false
        }
    }
}
