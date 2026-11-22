package app.blaxyt.mod;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Exact server publication time in the viewer's local calendar and time zone. */
final class PublicationTime {
    private PublicationTime() {}

    static String label(long seconds, long now, Locale locale, TimeZone zone) {
        if (seconds > 100000000000L) seconds /= 1000L;
        if (seconds < 1000000000L || seconds > now + 86400L) return null;
        String language = locale.getLanguage();
        boolean ru = "ru".equals(language) || "be".equals(language);
        boolean uk = "uk".equals(language);
        Locale words = ru ? new Locale("ru") : uk ? new Locale("uk") : locale;
        Calendar published = Calendar.getInstance(zone, words);
        published.setTimeInMillis(seconds * 1000L);
        Calendar today = Calendar.getInstance(zone, words);
        today.setTimeInMillis(now * 1000L);
        String clock = format("HH:mm", words, zone, seconds);
        if (sameDay(published, today))
            return (ru ? "Сегодня в " : uk ? "Сьогодні о " : "Today at ") + clock;
        today.add(Calendar.DATE, -1);
        if (sameDay(published, today))
            return (ru ? "Вчера в " : uk ? "Учора о " : "Yesterday at ") + clock;
        return format("d MMMM yyyy", words, zone, seconds)
                + (ru ? " в " : uk ? " о " : " at ") + clock;
    }

    private static boolean sameDay(Calendar first, Calendar second) {
        return first.get(Calendar.ERA) == second.get(Calendar.ERA)
                && first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private static String format(String pattern, Locale locale, TimeZone zone, long seconds) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
        formatter.setTimeZone(zone);
        return formatter.format(new Date(seconds * 1000L));
    }
}
