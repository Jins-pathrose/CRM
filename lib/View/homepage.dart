import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:audioplayers/audioplayers.dart';
import 'package:permission_handler/permission_handler.dart'; // <-- Add this

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final AudioPlayer _audioPlayer = AudioPlayer();
  String? _currentlyPlayingUrl;

  @override
  void initState() {
    super.initState();
    _requestPermissions(); // <-- Call permission function
  }

  Future<void> _requestPermissions() async {
    await [
      Permission.phone,
      Permission.microphone,
      Permission.storage,
    ].request();
  }

  Future<void> _playRecording(String url) async {
    try {
      if (_currentlyPlayingUrl == url) {
        await _audioPlayer.stop();
        setState(() => _currentlyPlayingUrl = null);
      } else {
        await _audioPlayer.play(UrlSource(url));
        setState(() => _currentlyPlayingUrl = url);
      }
    } catch (e) {
      debugPrint("Error playing audio: $e");
    }
  }

  @override
  void dispose() {
    _audioPlayer.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Call Recordings")),
      body: StreamBuilder<QuerySnapshot>(
        stream: FirebaseFirestore.instance
            .collection('call_recordings')
            .orderBy('uploaded_at', descending: true)
            .snapshots(),
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return const Center(child: Text("Error loading recordings"));
          }
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }

          final recordings = snapshot.data!.docs;

          if (recordings.isEmpty) {
            return const Center(child: Text("No call recordings found"));
          }

          return ListView.builder(
            itemCount: recordings.length,
            itemBuilder: (context, index) {
              final doc = recordings[index];
              final filename = doc['filename'];
              final url = doc['url'];

              final isPlaying = _currentlyPlayingUrl == url;

              return ListTile(
                title: Text(filename),
                trailing: IconButton(
                  icon: Icon(isPlaying ? Icons.stop : Icons.play_arrow),
                  onPressed: () => _playRecording(url),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
