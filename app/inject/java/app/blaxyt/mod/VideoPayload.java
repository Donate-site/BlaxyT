package app.blaxyt.mod;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodecList;
import java.io.File;
import java.io.IOException;

/** A container signature alone cannot distinguish an M4A soundtrack from a video. */
final class VideoPayload {
    static void validate(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            boolean video = false;
            for (int i=0; i<extractor.getTrackCount(); i++) {
                MediaFormat format=extractor.getTrackFormat(i);
                String mime=format.getString(MediaFormat.KEY_MIME);
                if(mime==null || !mime.startsWith("video/")) continue;
                if(!format.containsKey(MediaFormat.KEY_WIDTH) || !format.containsKey(MediaFormat.KEY_HEIGHT)
                        || format.getInteger(MediaFormat.KEY_WIDTH)<=0 || format.getInteger(MediaFormat.KEY_HEIGHT)<=0) continue;
                if(new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)==null)
                    throw new IOException("VIDEO_CODEC_UNSUPPORTED");
                extractor.selectTrack(i);
                if(extractor.getSampleTime()<0) throw new IOException("VIDEO_TRACK_EMPTY");
                video=true;
                break;
            }
            if(!video) throw new IOException("VIDEO_TRACK_MISSING");
        } catch(IOException error) { throw error; }
        catch(RuntimeException error) { throw new IOException("VIDEO_INVALID",error); }
        finally { extractor.release(); }
    }
}
