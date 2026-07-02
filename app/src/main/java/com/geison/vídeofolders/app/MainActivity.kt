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
    private val videoUris = mutableListOf<Uri>()
    private var index = 0

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
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, PICK_FOLDER)
        }

        videoView.setOnCompletionListener {
            playNext()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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

    private fun loadVideos(uri: Uri) {
        videoUris.clear()

        val folder = DocumentFile.fromTreeUri(this, uri)

        folder?.listFiles()?.forEach {
            if (it.type?.startsWith("video") == true) {
                videoUris.add(it.uri)
            }
        }

        if (videoUris.isNotEmpty()) {
            play(0)
        }
    }

    private fun play(i: Int) {
        index = i
        videoView.setVideoURI(videoUris[index])
        videoView.start()
    }

    private fun playNext() {
        if (videoUris.isEmpty()) return

        index++
        if (index >= videoUris.size) index = 0

        play(index)
    }
}
