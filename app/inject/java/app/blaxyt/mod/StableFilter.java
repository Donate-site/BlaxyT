package app.blaxyt.mod;

import java.util.ArrayList;
import java.util.List;

/** A list stays the same object until a rule actually excludes an entry. */
final class StableFilter {
    interface Exclusion { boolean rejects(Object value); }
    private StableFilter() {}

    static List select(List source, Exclusion rule) {
        if (source == null) return null;
        ArrayList result = null;
        int position = 0;
        for (Object value : source) {
            if (rule.rejects(value)) {
                if (result == null) {
                    result = new ArrayList(source.size());
                    result.addAll(source.subList(0, position));
                }
            } else if (result != null) {
                result.add(value);
            }
            position++;
        }
        return result == null ? source : result;
    }
}
