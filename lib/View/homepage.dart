import 'dart:io';
import 'package:flutter/material.dart';
import 'package:audioplayers/audioplayers.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  List<File> recordings = [];
  final AudioPlayer _audioPlayer = AudioPlayer();
  String? _currentlyPlayingPath;

  @override
  void initState() {
    super.initState();
    _requestPermissionsAndLoad();
  }

  Future<void> _requestPermissionsAndLoad() async {
    Map<Permission, PermissionStatus> statuses = await [
      Permission.microphone,
      Permission.phone,
      Platform.isAndroid ? Permission.manageExternalStorage : Permission.storage,
    ].request();

    bool allGranted = statuses.values.every((status) => status.isGranted);
    if (!allGranted && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Some permissions were denied")),
      );
      return;
    }

    await _loadRecordings();
  }

  Future<void> _loadRecordings() async {
    try {
      final cacheDir = await getTemporaryDirectory();
      final files = cacheDir
          .listSync()
          .whereType<File>()
          .where((f) => f.path.endsWith('.m4a'))
          .toList();

      files.sort((a, b) => b.lastModifiedSync().compareTo(a.lastModifiedSync()));

      if (mounted) {
        setState(() => recordings = files);
      }
    } catch (e) {
      debugPrint("Error loading recordings: $e");
    }
  }

  Future<void> _playRecording(File file) async {
    try {
      if (_currentlyPlayingPath == file.path) {
        await _audioPlayer.stop();
        setState(() => _currentlyPlayingPath = null);
      } else {
        await _audioPlayer.stop();
        await _audioPlayer.play(DeviceFileSource(file.path));
        setState(() => _currentlyPlayingPath = file.path);
      }
    } catch (e) {
      debugPrint("Playback error: $e");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Failed to play recording")),
        );
      }
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
      appBar: AppBar(
        title: const Text("Local Call Recordings"),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadRecordings,
            tooltip: "Refresh",
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _loadRecordings,
        child: recordings.isEmpty
            ? const Center(child: Text("No recordings found"))
            : ListView.builder(
                itemCount: recordings.length,
                itemBuilder: (context, index) {
                  final file = recordings[index];
                  final isPlaying = file.path == _currentlyPlayingPath;

                  return ListTile(
                    title: Text(file.uri.pathSegments.last),
                    subtitle: Text(file.path),
                    trailing: IconButton(
                      icon: Icon(isPlaying ? Icons.stop : Icons.play_arrow),
                      onPressed: () => _playRecording(file),
                    ),
                  );
                },
              ),
      ),
    );
  }
}
