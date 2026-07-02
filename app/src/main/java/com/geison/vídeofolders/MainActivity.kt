package com.geison.videofolders

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Typeface
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var message: TextView
    private lateinit var chooseButton: Button
    private lateinit var neonTitle: TextView
    private lateinit var prefs: SharedPreferences
    private var player: ExoPlayer? = null

    private val videoExtensions = setOf("mp4", "mov", "mkv", "webm", "3gp", "m4v", "avi")

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                persistFolderPermission(result.data, uri)
                prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
                loadVideosFromFolder(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("video_folder_player", MODE_PRIVATE)
        keepScreenOn()
        setupLayout()
        setupPlayer()

        val savedFolder = prefs.getString(KEY_FOLDER_URI, null)
        if (savedFolder == null) {
            showFolderSelection("Escolha a pasta dos vídeos para iniciar.")
        } else {
            loadVideosFromFolder(Uri.parse(savedFolder))
        }
    }

    override fun onResume() {
        super.onResume()
        enterFullScreen()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun setupLayout() {
        root = FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.BLACK)

        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        message = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply { setMargins(32, 32, 32, 32) }
        }

        chooseButton = Button(this).apply {
            text = "Escolher pasta"
            visibility = View.GONE
            setOnClickListener { openFolderPicker() }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { setMargins(16, 16, 16, 80) }
        }

        neonTitle = TextView(this).apply {
            text = "Video Folders"
            setTextColor(android.graphics.Color.CYAN)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(24f, 0f, 0f, android.graphics.Color.CYAN)
            setBackgroundColor(android.graphics.Color.argb(145, 0, 0, 0))
            letterSpacing = 0.08f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(58),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
            startAnimation(
                AlphaAnimation(0.35f, 1.0f).apply {
                    duration = 650
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                    interpolator = LinearInterpolator()
                }
            )
        }

        root.addView(playerView)
        root.addView(neonTitle)
        root.addView(message)
        root.addView(chooseButton)
        setContentView(root)
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            exoPlayer.playWhenReady = true
            playerView.player = exoPlayer
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        folderPicker.launch(intent)
    }

    private fun persistFolderPermission(data: Intent?, uri: Uri) {
        val flags = data?.flags ?: 0
        val takeFlags = flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Não foi possível salvar a permissão da pasta.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadVideosFromFolder(folderUri: Uri) {
        enterFullScreen()
        val folder = DocumentFile.fromTreeUri(this, folderUri)
        if (folder == null || !folder.exists() || !folder.isDirectory) {
            showFolderSelection("Não consegui acessar a pasta salva. Escolha a pasta novamente.")
            return
        }

        val videos = folder.listFiles()
            .filter { it.isFile && isVideoFile(it) }
            .sortedBy { it.name?.lowercase() ?: "" }

        if (videos.isEmpty()) {
            showFolderSelection("Nenhum vídeo encontrado nessa pasta. Coloque arquivos .mp4, .mov, .mkv, .webm ou .3gp e escolha a pasta novamente.")
            return
        }

        hideMessages()
        val mediaItems = videos.map { MediaItem.fromUri(it.uri) }
        player?.apply {
            clearMediaItems()
            setMediaItems(mediaItems)
            prepare()
            play()
        }
    }

    private fun isVideoFile(file: DocumentFile): Boolean {
        val mime = file.type ?: ""
        if (mime.startsWith("video/")) return true
        val name = file.name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in videoExtensions
    }

    private fun showFolderSelection(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
        chooseButton.visibility = View.VISIBLE
        player?.clearMediaItems()
    }

    private fun hideMessages() {
        message.visibility = View.GONE
        chooseButton.visibility = View.GONE
    }

    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun enterFullScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"
    }
}
