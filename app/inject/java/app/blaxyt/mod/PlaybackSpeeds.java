package app.blaxyt.mod;

import java.util.ArrayList;
import java.util.List;

/** Extend the native menu's data, preserving its native selection and playback callbacks. */
public final class PlaybackSpeeds {
    private PlaybackSpeeds() {}
    public static List<Float> extend(List<Float> nativeValues) {
        ArrayList<Float> result = new ArrayList<Float>(nativeValues);
        for (float value=3f; value<=5f; value+=1f) {
            if (!result.contains(value)) result.add(value);
        }
        return result;
    }
}
