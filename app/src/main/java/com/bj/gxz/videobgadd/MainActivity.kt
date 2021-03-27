package com.bj.gxz.videobgadd

import android.Manifest
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.jaygoo.widget.RangeSeekBar
import java.io.File

class MainActivity : AppCompatActivity() {
    private companion object {
        val MP4_NAME: String = "demo.mp4"
        val BG_NAME: String = "demo.mp3"
        val MIX_NAME: String = "mix.mp4"
    }

    private lateinit var rangeSeekBar: RangeSeekBar
    private lateinit var musicSeekBar: SeekBar
    private lateinit var videoSeekBar: SeekBar
    private lateinit var videoView: VideoView
    private var bgAudioVolume = 0
    private var videoVolume = 0
    private var duration = 0
    private var lastMin = 0f


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermission()
        initView()
        copyFileToSd()
        startPlay(File(Environment.getExternalStorageDirectory(), MP4_NAME).absolutePath)
        handler.postDelayed(runnable, 1000)
    }

    private fun initView() {
        rangeSeekBar = findViewById(R.id.id_rangeSeekBar)
        musicSeekBar = findViewById(R.id.musicSeekBar)
        videoSeekBar = findViewById(R.id.videoSeekBar)
        videoView = findViewById(R.id.id_videoView)
        videoView.setOnCompletionListener {
            val msec: Int = rangeSeekBar.currentRange?.get(0)?.times(1000)?.toInt() ?: 0
            videoView.seekTo(msec)
        }
        musicSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                bgAudioVolume = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }
        })
        videoSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                videoVolume = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
        videoView.setOnPreparedListener { mp ->
            duration = mp.getDuration() / 1000
            mp.setLooping(true)
            rangeSeekBar.setRange(0f, duration.toFloat())
            rangeSeekBar.setValue(0f, duration.toFloat())
            rangeSeekBar.isEnabled = true
        }
        rangeSeekBar.setOnRangeChangedListener { view, min, max, isFromUser ->
            if (lastMin != min) {
                lastMin = min
                videoView.seekTo(min.toInt() * 1000)
            }
        }

    }

    private fun copyFileToSd() {
        val mp3Path = File(Environment.getExternalStorageDirectory(), BG_NAME).absolutePath
        val videoPath = File(Environment.getExternalStorageDirectory(), MP4_NAME).absolutePath
        FileUtil.copyAssets(this, BG_NAME, mp3Path)
        FileUtil.copyAssets(this, MP4_NAME, videoPath)
    }

    private fun startPlay(path: String) {
        val layoutParams = videoView.layoutParams
        layoutParams.height = 672
        layoutParams.width = 1280
        videoView.layoutParams = layoutParams
        videoView.setVideoPath(path)
        videoView.start()
    }

    fun addBg(view: View) {
        val parent = Environment.getExternalStorageDirectory()
        val videoFile = File(parent, MP4_NAME)
        val audioFile = File(parent, BG_NAME)
        val outputFile = File(parent, MIX_NAME)
        Thread {
            Log.i("MixProcess", "start:" + (rangeSeekBar.currentRange[0] * 1000 * 1000).toInt())
            Log.i("MixProcess", "end:" + (rangeSeekBar.currentRange[1] * 1000 * 1000).toInt())
            Log.i("MixProcess", "videoVolume:$videoVolume")
            Log.i("MixProcess", "bgAudioVolume:$bgAudioVolume")
            MIXUtil.mix(
                videoFile.absolutePath,
                audioFile.absolutePath,
                outputFile.absolutePath,
                (rangeSeekBar.currentRange[0] * 1000 * 1000).toInt(),
                (rangeSeekBar.currentRange[1] * 1000 * 1000).toInt(),
                videoVolume,
                bgAudioVolume
            )

            runOnUiThread {
                Toast.makeText(this@MainActivity, "完毕", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    // 简单的处理下权限
    fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO,
                ), 1
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val runnable: Runnable = object : Runnable {
        override fun run() {
            if (videoView.currentPosition >= rangeSeekBar.currentRange[1] * 1000) {
                videoView.seekTo(rangeSeekBar.currentRange[0].toInt() * 1000)
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView.stopPlayback()
        handler.removeCallbacksAndMessages(null)
    }
}