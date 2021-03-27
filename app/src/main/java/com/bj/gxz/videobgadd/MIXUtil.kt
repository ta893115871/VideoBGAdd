package com.bj.gxz.videobgadd

import android.media.*
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Created by guxiuzhong@baidu.com on 2021/3/20.
 */
object MIXUtil {

    const val TAG = "MIXUtil"
    fun mix(
        videoPath: String,
        bgAudioPath: String,
        outPath: String,
        startTimeUs: Int,
        endTimeUs: Int,
        videoVolume: Int,
        bgAudioVolume: Int
    ) {
        val parent = Environment.getExternalStorageDirectory()
        val videoPcm = File(parent, "video.pcm").absolutePath
        val bgPcm = File(parent, "bgPcm.pcm").absolutePath

        decodeToPcm(videoPath, videoPcm, startTimeUs, endTimeUs)
        decodeToPcm(bgAudioPath, bgPcm, startTimeUs, endTimeUs)

        val mixPcm = File(parent, "mix.pcm").absolutePath

        mixPcm(videoPcm, bgPcm, mixPcm, videoVolume, bgAudioVolume)

        //  demo的MP3和MP4：采样率是44100hz，声道数是 双声道 2，16位的
        // pcm -> WAV
        val wavFile = File(parent, "mix.wav").absolutePath
        PcmToWavUtil(44100, 2, 16)
            .pcmToWav(mixPcm, wavFile)
        Log.d(TAG, "pcm -> WAV done:$wavFile")
        mixVideoAndBG(videoPath, outPath, startTimeUs, endTimeUs, wavFile)
    }

    private fun mixVideoAndBG(
        videoInput: String,
        output: String,
        startTimeUs: Int,
        endTimeUs: Int,
        wavFile: String
    ) {
        val mediaMuxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(videoInput)

        // 视频轨道取自视频文件，跟视频所有信息一样
        val videoIndex = selectTrack(mediaExtractor, false)
        val videoFormat = mediaExtractor.getTrackFormat(videoIndex)
        val muxerVideoTrackIndex = mediaMuxer.addTrack(videoFormat)


        // 音频轨道取自视频文件，跟视频所有信息一样
        val audioIndex = selectTrack(mediaExtractor, true)
        val audioFormat = mediaExtractor.getTrackFormat(audioIndex)


        // 将音频轨道设置为aac
        val audioBitrate = audioFormat.getInteger(MediaFormat.KEY_BIT_RATE)
        audioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)

        // 返回新的音频轨道
        val muxerAudioIndex = mediaMuxer.addTrack(audioFormat)

        // 混合开始
        mediaMuxer.start()

        //音频的wav
        val pcmExtractor = MediaExtractor()
        pcmExtractor.setDataSource(wavFile)
        val audioTrackIndex = selectTrack(pcmExtractor, true)
        pcmExtractor.selectTrack(audioTrackIndex)
        val pcmTrackFormat = pcmExtractor.getTrackFormat(audioTrackIndex)

        var maxBufferSize = 0
        maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            pcmTrackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else {
            100 * 1000
        }
        Log.d(TAG, "pcmTrackFormat maxBufferSize=$maxBufferSize")


        val encodeFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2
        )
        //比特率
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate)
        //音质等级
        encodeFormat.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectLC
        )
        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize)
        // 编码器aac
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        // 编码 wav -> aac
        val info = MediaCodec.BufferInfo()
        var encodeDone = false
        var buffer = ByteBuffer.allocateDirect(maxBufferSize)
        while (!encodeDone) {
            val inputBufferIndex = encoder.dequeueInputBuffer(10_000)
            if (inputBufferIndex >= 0) {
                val sampleTime = pcmExtractor.sampleTime
                //  pts小于0  来到了文件末尾 通知编码器  不用编码了
                if (sampleTime < 0) {
                    Log.d(TAG, "sampleTime<0")
                    encoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                } else {
                    val flags = pcmExtractor.sampleFlags
                    val size = pcmExtractor.readSampleData(buffer, 0)
                    val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                    inputBuffer!!.clear()
                    inputBuffer.put(buffer)
                    encoder.queueInputBuffer(inputBufferIndex, 0, size, sampleTime, flags)
                    // 读取下一帧数据
                    pcmExtractor.advance()
                }
            }
            var outputBufferIndex = encoder.dequeueOutputBuffer(info, 10_000)
            while (outputBufferIndex >= 0) {
                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    encodeDone = true
                    Log.d(TAG, "BUFFER_FLAG_END_OF_STREAM break")
                    break
                }
                val encodeOutputBuffer = encoder.getOutputBuffer(outputBufferIndex)

                mediaMuxer.writeSampleData(muxerAudioIndex, encodeOutputBuffer!!, info)

                encoder.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = encoder.dequeueOutputBuffer(info, 10_000)
            }
        }
        Log.d(TAG, "pcm -> aac audio done")

        // 选择视频轨道，开始写入视频
        mediaExtractor.selectTrack(videoIndex)
        mediaExtractor.seekTo(startTimeUs.toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        maxBufferSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else {
            100 * 1000
        }
        Log.d(TAG, "videoFormat=$videoFormat")
        Log.d(TAG, "start video mixVideoAndMusic maxBufferSize=$maxBufferSize")
        buffer = ByteBuffer.allocateDirect(maxBufferSize)
        while (true) {
            val sampleTimeUs = mediaExtractor.sampleTime
            if (sampleTimeUs == -1L) {
                Log.d(TAG, "sampleTimeUs == -1")
                break
            }
            if (sampleTimeUs < startTimeUs) {
                mediaExtractor.advance()
                continue
            }
            if (sampleTimeUs > endTimeUs) {
                Log.d(TAG, "sampleTimeUs > endTimeUs")
                break
            }
            info.presentationTimeUs = sampleTimeUs - startTimeUs
            info.flags = mediaExtractor.sampleFlags
            info.size = mediaExtractor.readSampleData(buffer, 0)
            if (info.size < 0) {
                Log.d(TAG, "info.size<0 break")
                break
            }
            mediaMuxer.writeSampleData(muxerVideoTrackIndex, buffer, info)
            mediaExtractor.advance()
        }
        Log.d(TAG, "video done")

        pcmExtractor.release()
        mediaExtractor.release()
        encoder.stop()
        encoder.release()
        mediaMuxer.stop()
        mediaMuxer.release()
        Log.d(TAG, "all done")
    }

    private fun mixPcm(
        pcm1: String,
        pcm2: String,
        mixPcm: String,
        videoVolume: Int,
        bgAudioVolume: Int
    ) {
        val volume1: Float = videoVolume * 1.0f / 100
        val volume2: Float = bgAudioVolume * 1.0f / 100
        val buffSize = 2048
        val buffer1 = ByteArray(buffSize)
        val buffer2 = ByteArray(buffSize)
        val buffer3 = ByteArray(buffSize)
        val fis1 = FileInputStream(pcm1)
        val fis2 = FileInputStream(pcm2)
        val fosMix = FileOutputStream(mixPcm)
        var isEnd1 = false
        var isEnd2 = false
        var temp1: Short
        var temp2: Short
        var temp: Int
        var ret1 = -1
        var ret2 = -1
        while (!isEnd1 || !isEnd2) {
            if (!isEnd1) {
                ret1 = fis1.read(buffer1)
                isEnd1 = ret1 == -1
            }
            if (!isEnd2) {
                ret2 = fis2.read(buffer2)
                isEnd2 = ret2 == -1
                for (i in buffer2.indices step 2) {
                    // java 版本清楚些
                    // temp1 = (short) ((buffer1[i] & 0xff) | (buffer1[i + 1] & 0xff) << 8);
                    // temp2 = (short) ((buffer2[i] & 0xff) | (buffer2[i + 1] & 0xff) << 8);

//                    temp1 = PcmToWavUtil.to(buffer1[i], buffer1[i + 1])
//                    temp2 = PcmToWavUtil.to(buffer2[i], buffer2[i + 1])

                    temp1 =
                        ((buffer1[i].toInt() and 0xff) or ((buffer1[i + 1].toInt() and 0xff) shl 8)).toShort()

                    temp2 =
                        ((buffer2[i].toInt() and 0xff) or ((buffer2[i + 1].toInt() and 0xff) shl 8)).toShort()

                    // 两个short变量相加 会大于short
                    temp = (temp1 * volume1 + temp2 * volume2).toInt()
                    // short类型的取值范围[-32768 ~ 32767]
                    if (temp > 32767) {
                        temp = 32767
                    } else if (temp < -32768) {
                        temp = -32768
                    }

                    // java 版本清楚些
                    // buffer3[i] = (byte) (temp & 0x00ff);
                    // buffer3[i + 1] = (byte) ((temp & 0xFF00) >> 8 );

                    // 低八位 高八位 低八位 高八位 。。。
                    buffer3[i] = (temp and 0x00ff).toByte()
                    buffer3[i + 1] = (temp and 0xff00).shr(8).toByte()
                }
                fosMix.write(buffer3)
            }
        }
        fis1.close()
        fis2.close()
        fosMix.flush()
        fosMix.close()
        Log.d(TAG, "mixPcm:$mixPcm")
    }

    private fun decodeToPcm(srcPath: String, outPcmPath: String, startTimeUs: Int, endTimeUs: Int) {
        if (endTimeUs < startTimeUs) {
            return
        }
        val outPcmFile = File(outPcmPath)
        val writePcmChannel = FileOutputStream(outPcmFile).channel
        val mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(srcPath)
        val audioTrack: Int = selectTrack(mediaExtractor, true)
        if (audioTrack == -1) {
            return
        }
        mediaExtractor.selectTrack(audioTrack)
        mediaExtractor.seekTo(startTimeUs.toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val format = mediaExtractor.getTrackFormat(audioTrack)
        Log.d(TAG, "format=$format")
        val maxBufferSize: Int
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            Log.d(TAG, "KEY_MAX_INPUT_SIZE")
        } else {
            maxBufferSize = 100 * 1000
        }
        Log.d(TAG, "maxBufferSize=$maxBufferSize")
        val buffer = ByteBuffer.allocateDirect(maxBufferSize)
        val mediaCodec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        mediaCodec.configure(format, null, null, 0)
        mediaCodec.start()
        val info = MediaCodec.BufferInfo()
        while (true) {
            val inputIndex = mediaCodec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val sampleTimeUs = mediaExtractor.sampleTime
                if (sampleTimeUs == -1L) {
                    break
                } else if (sampleTimeUs > endTimeUs) {
                    break
                } else if (sampleTimeUs < sampleTimeUs) {
                    mediaExtractor.advance()
                }
                info.presentationTimeUs = sampleTimeUs
                info.flags = mediaExtractor.sampleFlags
                info.size = mediaExtractor.readSampleData(buffer, 0)

                val data = ByteArray(buffer.remaining())
                buffer.get(data)

                val inputBuffer = mediaCodec.getInputBuffer(inputIndex)
                inputBuffer!!.clear()
                inputBuffer.put(data)
                mediaCodec.queueInputBuffer(
                    inputIndex,
                    0,
                    info.size,
                    info.presentationTimeUs,
                    info.flags
                )
                mediaExtractor.advance()
            }
            var outputIndex = mediaCodec.dequeueOutputBuffer(info, 10_000)
            while (outputIndex >= 0) {
                val outByteBuffer = mediaCodec.getOutputBuffer(outputIndex)
                // pcm
                writePcmChannel.write(outByteBuffer)

                mediaCodec.releaseOutputBuffer(outputIndex, false)
                outputIndex = mediaCodec.dequeueOutputBuffer(info, 10_000)
            }
        }
        Log.d(TAG, "decode pcm done:$outPcmPath")
    }

    private fun selectTrack(mediaExtractor: MediaExtractor, audio: Boolean): Int {
        val count = mediaExtractor.trackCount
        for (i in 0 until count) {
            val format = mediaExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (audio) {
                if (mime!!.startsWith("audio/")) {
                    return i
                }
            } else {
                if (mime!!.startsWith("video/")) {
                    return i
                }
            }
        }
        return -1
    }
}