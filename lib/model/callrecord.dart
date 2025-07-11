import 'dart:io';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class CallUploader {
  final FirebaseStorage _storage = FirebaseStorage.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  Future<void> uploadLatestRecording() async {
    final directory = await getExternalStorageDirectory();
    if (directory == null) return;

    final files = Directory(directory.path)
        .listSync()
        .whereType<File>()
        .where((f) => f.path.endsWith('.3gp') || f.path.endsWith('.m4a'))
        .toList();

    if (files.isEmpty) return;

    // Sort by most recent
    files.sort((a, b) => b.lastModifiedSync().compareTo(a.lastModifiedSync()));
    final fileToUpload = files.first;

    final fileName = fileToUpload.uri.pathSegments.last;
    final storageRef = _storage.ref().child('call_recordings/$fileName');

    try {
      final uploadTask = await storageRef.putFile(fileToUpload);
      final downloadUrl = await uploadTask.ref.getDownloadURL();

      await _firestore.collection('call_recordings').add({
        'filename': fileName,
        'url': downloadUrl,
        'uploaded_at': Timestamp.now(),
      });

      debugPrint("Uploaded: $fileName");
    } catch (e) {
      debugPrint("Upload failed: $e");
    }
  }
}
