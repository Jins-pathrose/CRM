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
    _loadRecordings();
  }

  Future<void> _loadRecordings() async {
    final permission = Platform.isAndroid
        ? await Permission.manageExternalStorage.request()
        : await Permission.storage.request();

    if (!permission.isGranted) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Storage permission denied")),
        );
      }
      return;
    }

    try {
      // Use app's internal cache directory where .m4a is stored
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
      appBar: AppBar(title: const Text("Local Call Recordings")),
      body: recordings.isEmpty
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
    );
  }
}
