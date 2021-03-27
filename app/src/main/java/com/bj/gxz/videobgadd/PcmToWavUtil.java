package com.bj.gxz.videobgadd;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by guxiuzhong@baidu.com on 2021/2/13.
 */
public class PcmToWavUtil {
    // 采样率
    private int mSampleRate;
    // 声道数 单声道：1或双声道：2
    private int mChannelCount;
    // 采样位数，8或16
    private int bitNum;

    public PcmToWavUtil(int sampleRate, int channelCount, int bitNum) {
        this.mSampleRate = sampleRate;
        this.mChannelCount = channelCount;
        this.bitNum = bitNum;
    }

    public void pcmToWav(String inFilename, String outFilename) {
        FileInputStream in;
        FileOutputStream out;
        long totalAudioLen;
        long totalDataLen;
        long longSampleRate = mSampleRate;
        int channels = mChannelCount;
        //音频数据传送速率,采样率*通道数*采样深度/8
        long byteRate = bitNum * mSampleRate * channels / 8;
        byte[] data = new byte[8192];
        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            // pcm数据的总大小
            totalAudioLen = in.getChannel().size();
            // 总大小，不包括RIFF和WAV，所以是44 - 8 = 36，在加上PCM文件大小
            totalDataLen = totalAudioLen + 36;

            writeWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);
            int ret;
            while ((ret = in.read(data)) != -1) {
                out.write(data, 0, ret);
            }
            out.flush();
            out.close();
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 加入wav文件头
     */
    private void writeWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        // 数据大小
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';  //WAVE
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' '; //过渡字节
        header[16] = 16;  // 4 bytes
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        // 2字节数据，内容为一个短整数，表示格式种类（值为1时，表示数据为线性PCM编码）
        header[20] = 1;   // format = 1
        header[21] = 0;
        header[22] = (byte) channels;  //通道数（单声道为1，双声道为2）
        header[23] = 0;
        //采样率，每个通道的播放速度，用4字节表示
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        //音频数据传送速率,采样率*通道数*采样深度/8
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        // 确定系统一次要处理多少个这样字节的数据，确定缓冲区，通道数*采样位数  用2个字节表示
        header[32] = (byte) (channels * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16;  // bits per sample 每个样本的数据位数
        header[35] = 0;
        header[36] = 'd'; //data
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        // pcm数据的大小
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }

    public static short to(byte b1, byte b2) {
        return (short) ((b1 & 0xff) | (b2 & 0xff) << 8);
    }
}
