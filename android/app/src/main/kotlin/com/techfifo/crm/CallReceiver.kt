package com.techfifo.crm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallReceiver : BroadcastReceiver() {

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFile: File? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.e("CallReceiver", "Context or Intent is null")
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d("CallReceiver", "Phone state changed: $state")

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (!isRecording) {
                    Log.d("CallReceiver", "Call started, starting recording...")
                    startRecording(context)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (isRecording) {
                    Log.d("CallReceiver", "Call ended, stopping recording...")
                    stopRecording(context)
                }
            }

            else -> Log.d("CallReceiver", "Other phone state: $state")
        }
    }

    private fun startRecording(context: Context) {
        try {
            if (isRecording) {
                Log.w("CallReceiver", "Recording already in progress")
                return
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "call_recording_$timeStamp.3gp"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)
            currentFile = file

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.d("CallReceiver", "Recording started: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("CallReceiver", "Error starting recording: ${e.message}", e)
            // Clean up on error
            recorder?.release()
            recorder = null
            isRecording = false
        }
    }

    private fun stopRecording(context: Context) {
        if (!isRecording) {
            Log.w("CallReceiver", "stopRecording called but not recording")
            return
        }

        if (recorder != null) {
            try {
                recorder?.apply {
                    stop()
                    reset()
                    release()
                }
                Log.d("CallReceiver", "Recording stopped")
                
                // Upload to Firebase if we have a file
                currentFile?.let { file ->
                    uploadToFirebase(context, file)
                }
                
            } catch (e: Exception) {
                Log.e("CallReceiver", "Error stopping recording: ${e.message}", e)
            }
        } else {
            Log.w("CallReceiver", "stopRecording called but recorder is null")
        }

        // Clean up regardless of success/failure
        recorder = null
        isRecording = false
        currentFile = null
    }

    private fun uploadToFirebase(context: Context, file: File?) {
        if (file == null || !file.exists()) {
            Log.e("FirebaseUpload", "File is null or doesn't exist, cannot upload")
            return
        }

        // Add delay to ensure network is available after call
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            attemptUpload(context, file, 0)
        }, 2000)
    }

    private fun attemptUpload(context: Context, file: File, retryCount: Int) {
        val maxRetries = 3
        
        try {
            // Initialize Firebase if not already done
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            Log.d("FirebaseUpload", "Firebase initialized, attempt ${retryCount + 1}")

            val storageRef = Firebase.storage.reference
            val fileRef = storageRef.child("call_recordings/${file.name}")
            val uri = Uri.fromFile(file)

            Log.d("FirebaseUpload", "Uploading file: ${file.absolutePath}")

            val uploadTask = fileRef.putFile(uri)

            uploadTask.addOnSuccessListener {
                Log.d("FirebaseUpload", "Upload success: ${file.name}")

                fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val firestore = Firebase.firestore
                    val recordingData = hashMapOf(
                        "filename" to file.name,
                        "url" to downloadUri.toString(),
                        "uploaded_at" to System.currentTimeMillis(),
                        "file_size" to file.length(),
                        "retry_count" to retryCount
                    )

                    firestore.collection("call_recordings")
                        .add(recordingData)
                        .addOnSuccessListener {
                            Log.d("Firestore", "Metadata uploaded successfully for ${file.name}")
                            // Delete local file after successful upload (optional)
                            // file.delete()
                        }
                        .addOnFailureListener { ex ->
                            Log.e("Firestore", "Metadata upload failed: ${ex.message}", ex)
                            if (retryCount < maxRetries) {
                                retryUpload(context, file, retryCount + 1)
                            }
                        }
                }.addOnFailureListener { ex ->
                    Log.e("FirebaseUpload", "Download URL fetch failed: ${ex.message}", ex)
                    if (retryCount < maxRetries) {
                        retryUpload(context, file, retryCount + 1)
                    }
                }

            }.addOnFailureListener { ex ->
                Log.e("FirebaseUpload", "File upload failed: ${ex.message}", ex)
                if (retryCount < maxRetries) {
                    retryUpload(context, file, retryCount + 1)
                }
            }

        } catch (e: Exception) {
            Log.e("FirebaseUpload", "Exception during upload: ${e.message}", e)
            if (retryCount < maxRetries) {
                retryUpload(context, file, retryCount + 1)
            }
        }
    }

    private fun retryUpload(context: Context, file: File, retryCount: Int) {
        val delayMs = (retryCount * 5000).toLong() // 5s, 10s, 15s delays
        Log.d("FirebaseUpload", "Retrying upload in ${delayMs}ms, attempt $retryCount")
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            attemptUpload(context, file, retryCount)
        }, delayMs)
    }
}