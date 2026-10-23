package clre20.customDisc.audio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 讀取 Ogg Vorbis 檔的聲道數、取樣率與長度，不需要外部函式庫。
 * 格式錯誤時丟出的 IOException 訊息是 messages.yml 的 sound-error.* 鍵。
 * 長度 = 最後一個 Ogg page 的 granule position（總取樣數） / 取樣率。
 */
public record OggInfo(int channels, int sampleRate, double seconds) {

    public boolean stereo() {
        return channels > 1;
    }

    public static OggInfo read(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long size = raf.length();
            if (size < 58) {
                throw new IOException("sound-error.too-small");
            }

            byte[] head = new byte[(int) Math.min(size, 4096)];
            raf.readFully(head);
            if (!(head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S')) {
                throw new IOException("sound-error.not-ogg");
            }
            int serial = le32(head, 14);
            int segments = head[26] & 0xFF;
            int dataStart = 27 + segments;
            if (dataStart + 16 > head.length) {
                throw new IOException("sound-error.incomplete");
            }
            // Vorbis identification header: 0x01 'vorbis' version(4) channels(1) rate(4)
            if (head[dataStart] != 0x01 || head[dataStart + 1] != 'v' || head[dataStart + 2] != 'o'
                    || head[dataStart + 3] != 'r' || head[dataStart + 4] != 'b' || head[dataStart + 5] != 'i'
                    || head[dataStart + 6] != 's') {
                throw new IOException("sound-error.not-vorbis");
            }
            int channels = head[dataStart + 11] & 0xFF;
            int rate = le32(head, dataStart + 12);
            if (channels <= 0 || rate <= 0) {
                throw new IOException("sound-error.bad-format");
            }

            long granule = lastGranule(raf, size, serial);
            if (granule <= 0) {
                throw new IOException("sound-error.no-length");
            }
            return new OggInfo(channels, rate, granule / (double) rate);
        }
    }

    private static long lastGranule(RandomAccessFile raf, long size, int serial) throws IOException {
        int window = (int) Math.min(size, 256 * 1024);
        byte[] tail = new byte[window];
        raf.seek(size - window);
        raf.readFully(tail);
        for (int i = window - 27; i >= 0; i--) {
            if (tail[i] == 'O' && tail[i + 1] == 'g' && tail[i + 2] == 'g' && tail[i + 3] == 'S'
                    && le32(tail, i + 14) == serial) {
                long granule = le64(tail, i + 6);
                if (granule > 0) {
                    return granule;
                }
            }
        }
        return -1;
    }

    private static int le32(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | (b[o + 3] & 0xFF) << 24;
    }

    private static long le64(byte[] b, int o) {
        long v = 0;
        for (int i = 7; i >= 0; i--) {
            v = (v << 8) | (b[o + i] & 0xFF);
        }
        return v;
    }
}
