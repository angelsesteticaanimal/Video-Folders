import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';
import 'package:file_picker/file_picker.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Video Folders',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(),
      home: VideoFolderPlayerPage(),
    );
  }
}

class VideoFolderPlayerPage extends StatefulWidget {
  @override
  _VideoFolderPlayerPageState createState() => _VideoFolderPlayerPageState();
}

class _VideoFolderPlayerPageState extends State<VideoFolderPlayerPage>
    with SingleTickerProviderStateMixin {
  List<File> _videos = [];
  VideoPlayerController? _controllerA;
  VideoPlayerController? _controllerB;
  bool _usingA = true; // true = A is currently visible
  int _currentIndex = 0;
  bool _loading = false;
  bool _isPreparingNext = false;

  // fade animation controller
  late AnimationController _fadeController;
  Duration fadeDuration = Duration(milliseconds: 800);

  final List<String> _extensions = ['.mp4', '.mov', '.mkv', '.webm'];

  // store durations when computed
  Map<int, Duration> _durations = {};

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(vsync: this, duration: fadeDuration)
      ..addListener(() {
        // update UI during crossfade and smoothly interpolate audio volumes
        _updateAudioVolumes();
        setState(() {});
      })
      ..addStatusListener((status) {
        if (status == AnimationStatus.completed) {
          // finish swap: dispose the previous bottom controller
          _finalizeCrossfade();
        }
      });
  }

  @override
  void dispose() {
    _fadeController.dispose();
    _controllerA?.dispose();
    _controllerB?.dispose();
    super.dispose();
  }

  Future<bool> _requestStoragePermission() async {
    if (Platform.isAndroid) {
      if (await Permission.storage.request().isGranted) return true;
      if (await Permission.manageExternalStorage.isDenied) {
        final status = await Permission.manageExternalStorage.request();
        return status.isGranted;
      }
      return false;
    } else if (Platform.isIOS) {
      return true;
    }
    return true;
  }

  Future<void> _pickDirectoryAndLoad() async {
    setState(() => _loading = true);

    final ok = await _requestStoragePermission();
    if (!ok) {
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Permissão de armazenamento necessária')),
      );
      return;
    }

    String? folderPath = await FilePicker.platform.getDirectoryPath();

    if (folderPath == null) {
      final result = await FilePicker.platform.pickFiles(
        allowMultiple: true,
        type: FileType.custom,
        allowedExtensions: _extensions.map((e) => e.replaceFirst('.', '')).toList(),
      );
      if (result != null && result.files.isNotEmpty) {
        _videos = result.paths.whereType<String>().map((p) => File(p)).toList();
      }
    } else {
      final dir = Directory(folderPath);
      final items = dir.listSync(recursive: false);
      _videos = items.whereType<File>().where((f) {
        final ext = f.path.toLowerCase();
        return _extensions.any((e) => ext.endsWith(e));
      }).toList();
      _videos.sort((a, b) => a.path.compareTo(b.path));
    }

    if (_videos.isEmpty) {
      setState(() {
        _loading = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Nenhum vídeo encontrado na pasta / seleção.')),
      );
      return;
    }

    _currentIndex = 0;
    _durations.clear();

    // Precompute durations (opcional)
    await _computeDurations();

    // initialize first controller in A
    await _initializeControllerAtIndex(_currentIndex, asA: true, autoPlay: true);
    setState(() => _loading = false);
  }

  Future<void> _computeDurations() async {
    for (int i = 0; i < _videos.length; i++) {
      try {
        final tmp = VideoPlayerController.file(_videos[i]);
        await tmp.initialize();
        _durations[i] = tmp.value.duration;
        await tmp.dispose();
      } catch (e) {
        // ignore individual failures; leave duration unknown
      }
    }
  }

  Future<void> _initializeControllerAtIndex(int index,
      {required bool asA, bool autoPlay = false}) async {
    final file = _videos[index];
    final controller = VideoPlayerController.file(file);
    await controller.initialize();
    controller.setLooping(false);
    controller.addListener(_onAnyControllerUpdate);

    if (asA) {
      await _controllerA?.pause();
      await _controllerA?.dispose();
      _controllerA = controller;
    } else {
      await _controllerB?.pause();
      await _controllerB?.dispose();
      _controllerB = controller;
    }

    if (autoPlay) {
      await controller.play();
      controller.setVolume(1.0);
    } else {
      controller.setVolume(0.0); // start muted for next during crossfade
    }
    setState(() {});
  }

  void _onAnyControllerUpdate() {
    // called frequently; check for nearing end on the visible controller
    final visible = _usingA ? _controllerA : _controllerB;
    if (visible == null || !visible.value.isInitialized) return;
    final position = visible.value.position;
    final duration = visible.value.duration;
    if (duration == null || duration == Duration.zero) return;
    final remaining = duration - position;

    // start prepare for crossfade when remaining <= fadeDuration and not already preparing
    if (!_isPreparingNext &&
        remaining <= fadeDuration &&
        _videos.length > 1) {
      _prepareNextAndCrossfade();
    }

    // if single video, let it loop normally (no crossfade)
    if (_videos.length == 1) {
      if (visible.value.position >= visible.value.duration &&
          !visible.value.isPlaying) {
        visible.seekTo(Duration.zero);
        visible.play();
      }
    }

    setState(() {}); // update overlays (time estimates)
  }

  Future<void> _prepareNextAndCrossfade() async {
    _isPreparingNext = true;
    final nextIndex = (_currentIndex + 1) % _videos.length;
    final nextIsA = !_usingA; // place next in the hidden controller

    try {
      await _initializeControllerAtIndex(nextIndex, asA: nextIsA, autoPlay: false);
      final nextController = nextIsA ? _controllerA : _controllerB;
      final currentController = _usingA ? _controllerA : _controllerB;
      if (nextController == null || currentController == null) {
        _isPreparingNext = false;
        return;
      }

      // start the next controller muted and playing
      await nextController.play();
      await nextController.setVolume(0.0);

      // start fade animation (audio volumes will be interpolated in listener)
      _fadeController.reset();
      await _fadeController.forward();
      // when completed, _finalizeCrossfade() will be called by status listener
    } catch (e) {
      // fallback: if preparation fails, just wait for natural end
      _isPreparingNext = false;
    }
  }

  void _finalizeCrossfade() {
    // swap visible flag and set volumes correctly; dispose previous controller (now bottom)
    final wasUsingA = _usingA;
    _usingA = !_usingA;
    final visible = _usingA ? _controllerA : _controllerB;
    final hidden = _usingA ? _controllerB : _controllerA;

    if (visible != null && visible.value.isInitialized) visible.setVolume(1.0);
    if (hidden != null) {
      try {
        hidden.pause();
        hidden.setVolume(0.0);
        hidden.dispose().catchError((_) {});
      } catch (_) {}
      if (_usingA) {
        _controllerB = null;
      } else {
        _controllerA = null;
      }
    }

    // advance current index to the one we switched to
    _currentIndex = (_currentIndex + 1) % _videos.length;
    _isPreparingNext = false;
    setState(() {});
  }

  // helper to get current crossfade opacities
  double _opacityForControllerA() {
    if (!_fadeController.isAnimating) {
      return _usingA ? 1.0 : 0.0;
    } else {
      return _usingA ? (1.0 - _fadeController.value) : _fadeController.value;
    }
  }

  double _opacityForControllerB() {
    if (!_fadeController.isAnimating) {
      return _usingA ? 0.0 : 1.0;
    } else {
      return _usingA ? _fadeController.value : (1.0 - _fadeController.value);
    }
  }

  // Smoothly update audio volumes following the visual fade
  void _updateAudioVolumes() {
    // When animation not running, volumes are handled elsewhere (start/end).
    if (!_fadeController.isAnimating) return;

    final volA = _opacityForControllerA().clamp(0.0, 1.0);
    final volB = _opacityForControllerB().clamp(0.0, 1.0);

    try {
      if (_controllerA != null && _controllerA!.value.isInitialized) {
        _controllerA!.setVolume(volA);
      }
    } catch (_) {}

    try {
      if (_controllerB != null && _controllerB!.value.isInitialized) {
        _controllerB!.setVolume(volB);
      }
    } catch (_) {}
  }

  String _formatDuration(Duration d) {
    final total = d.inSeconds;
    final hours = total ~/ 3600;
    final minutes = (total % 3600) ~/ 60;
    final seconds = total % 60;
    if (hours > 0) {
      return '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    } else {
      return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    }
  }

  // compute estimated remaining total time (current remaining + durations of following videos)
  String _estimatedRemaining() {
    final visible = _usingA ? _controllerA : _controllerB;
    if (visible == null || !visible.value.isInitialized) return '--:--';

    final currentDuration = visible.value.duration;
    final currentPosition = visible.value.position;
    if (currentDuration == null) return '--:--';
    Duration totalRemaining = currentDuration - currentPosition;

    // add durations of following videos if known
    for (int i = 1; i < _videos.length; i++) {
      final idx = (_currentIndex + i) % _videos.length;
      if (_durations.containsKey(idx)) {
        totalRemaining += _durations[idx]!;
      } else {
        // if any duration unknown, we can't precisely estimate
        return 'calculando...';
      }
    }
    return _formatDuration(totalRemaining);
  }

  @override
  Widget build(BuildContext context) {
    final controllerAIsReady = _controllerA != null && _controllerA!.value.isInitialized;
    final controllerBIsReady = _controllerB != null && _controllerB!.value.isInitialized;

    return Scaffold(
      body: Stack(
        children: [
          // Bottom (A)
          if (controllerAIsReady)
            Positioned.fill(
              child: Opacity(
                opacity: _opacityForControllerA().clamp(0.0, 1.0),
                child: FittedBox(
                  fit: BoxFit.cover,
                  alignment: Alignment.center,
                  child: SizedBox(
                    width: _controllerA!.value.size.width,
                    height: _controllerA!.value.size.height,
                    child: VideoPlayer(_controllerA!),
                  ),
                ),
              ),
            ),

          // Top (B)
          if (controllerBIsReady)
            Positioned.fill(
              child: Opacity(
                opacity: _opacityForControllerB().clamp(0.0, 1.0),
                child: FittedBox(
                  fit: BoxFit.cover,
                  alignment: Alignment.center,
                  child: SizedBox(
                    width: _controllerB!.value.size.width,
                    height: _controllerB!.value.size.height,
                    child: VideoPlayer(_controllerB!),
                  ),
                ),
              ),
            ),

          // If neither ready, black background
          if (!controllerAIsReady && !controllerBIsReady)
            Container(color: Colors.black),

          // Top-right controls
          Positioned(
            top: 40,
            right: 16,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                ElevatedButton(
                  onPressed: _pickDirectoryAndLoad,
                  child: Text('Selecionar pasta / arquivos'),
                ),
                SizedBox(height: 8),
                if (!_loading && (_controllerA != null || _controllerB != null))
                  ElevatedButton(
                    onPressed: () {
                      final visible = _usingA ? _controllerA : _controllerB;
                      if (visible == null) return;
                      if (visible.value.isPlaying) {
                        visible.pause();
                      } else {
                        visible.play();
                      }
                      setState(() {});
                    },
                    child: Text((_usingA ? _controllerA : _controllerB)?.value.isPlaying == true
                        ? 'Pausa'
                        : 'Play'),
                  ),
              ],
            ),
          ),

          // Bottom-left: estimated remaining
          Positioned(
            left: 16,
            bottom: 24,
            child: Container(
              padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              color: Colors.black54,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.timer, color: Colors.white70),
                  SizedBox(width: 8),
                  Text('Tempo restante: ',
                      style: TextStyle(color: Colors.white70, fontSize: 14)),
                  Text(_estimatedRemaining(),
                      style: TextStyle(color: Colors.white, fontSize: 14)),
                ],
              ),
            ),
          ),

          // Center overlay when loading or no videos
          if (_loading)
            const Center(child: CircularProgressIndicator())
          else if (_videos.isEmpty)
            Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.folder_open, size: 72, color: Colors.white54),
                  SizedBox(height: 12),
                  Text('Nenhum vídeo selecionado', style: TextStyle(color: Colors.white70)),
                  SizedBox(height: 8),
                  ElevatedButton(
                    onPressed: _pickDirectoryAndLoad,
                    child: Text('Selecionar pasta / arquivos'),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}