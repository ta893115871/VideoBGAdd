package com.bj.gxz.videobgadd

import android.content.Context
import android.content.res.AssetFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Created by guxiuzhong on 2021/3/20.
 */
object FileUtil {

    fun copyAssets(context: Context, assetsName: String, destPath: String) {
        val assetFileDescriptor: AssetFileDescriptor = context.assets.openFd(assetsName)
        val from = FileInputStream(assetFileDescriptor.fileDescriptor).channel
        val to = FileOutputStream(destPath).channel
        from.transferTo(assetFileDescriptor.startOffset, assetFileDescriptor.length, to)
        from.close()
        to.close()
    }
}
