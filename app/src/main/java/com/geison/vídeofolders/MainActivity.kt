package com.videofolders.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile

class MainActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private var videoUris: MutableList<Uri> = mutableListOf()
    private var currentIndex = 0

    private val PICK_FOLDER = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        val button = Button(this)
        button.text = "Selecionar Pasta"

        videoView = VideoView(this)

        layout.addView(button)
        layout.addView(videoView)

        setContentView(layout)

        button.setOnClickListener {
            openFolderPicker()
        }

        videoView.setOnCompletionListener {
            playNext()
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, PICK_FOLDER)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_FOLDER && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return

            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            loadVideos(uri)
        }
    }

    private fun loadVideos(folderUri: Uri) {
        videoUris.clear()

        val folder = DocumentFile.fromTreeUri(this, folderUri)

        folder?.listFiles()?.forEach { file ->
            if (file.type?.startsWith("video") == true) {
                videoUris.add(file.uri)
            }
        }

        videoUris.sortBy { it.toString() }

        if (videoUris.isNotEmpty()) {
            playVideo(0)
        }
    }

    private fun playVideo(index: Int) {
        currentIndex = index
        videoView.setVideoURI(videoUris[index])
        videoView.start()
    }

    private fun playNext() {
        if (videoUris.isEmpty()) return

        currentIndex++
        if (currentIndex >= videoUris.size) {
            currentIndex = 0
        }

        playVideo(currentIndex)
    }
}
