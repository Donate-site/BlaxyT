"""Exact publication labels: calendar boundaries, DST, milliseconds and invalid data."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
HARNESS = r'''package app.blaxyt.mod;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.TimeZone;
public final class PublicationTimeHarness {
    static int checks;
    static long time(String value) { return ZonedDateTime.parse(value).toEpochSecond(); }
    static void check(long created, long now, String language, String zone, String expected) {
        String result=PublicationTime.label(created,now,new Locale(language),TimeZone.getTimeZone(zone));
        if(expected==null ? result!=null : !expected.equals(result))
            throw new AssertionError("Expected "+expected+" but got "+result);
        checks++;
    }
    public static void main(String[] args) {
        long now=time("2026-11-22T12:00:00+03:00");
        check(time("2026-11-22T09:05:42+03:00"),now,"ru","Europe/Moscow","Сегодня в 09:05");
        check(time("2026-11-21T19:36:59+03:00"),now,"ru","Europe/Moscow","Вчера в 19:36");
        check(time("2025-11-19T15:45:00+03:00"),now,"ru","Europe/Moscow","19 ноября 2025 в 15:45");
        check(time("2026-11-20T15:45:00+03:00"),now,"ru","Europe/Moscow","20 ноября 2026 в 15:45");
        check(time("2026-11-21T23:59:00+03:00"),time("2026-11-22T00:01:00+03:00"),"ru","Europe/Moscow","Вчера в 23:59");
        check(time("2025-12-31T23:59:00+03:00"),time("2026-01-01T00:01:00+03:00"),"ru","Europe/Moscow","Вчера в 23:59");
        check(time("2024-02-29T10:00:00+03:00"),time("2024-03-01T11:00:00+03:00"),"ru","Europe/Moscow","Вчера в 10:00");
        check(time("2026-11-01T00:30:00-04:00"),time("2026-11-02T00:45:00-05:00"),"ru","America/New_York","Вчера в 00:30");
        check(time("2026-03-08T01:30:00-05:00"),time("2026-03-09T01:00:00-04:00"),"ru","America/New_York","Вчера в 01:30");
        check(time("2026-11-21T23:30:00Z"),now,"ru","Europe/Moscow","Сегодня в 02:30");
        check(time("2026-11-21T23:30:00Z"),now,"ru","UTC","Вчера в 23:30");
        check(time("2025-11-19T15:45:00+03:00")*1000,now,"ru","Europe/Moscow","19 ноября 2025 в 15:45");
        check(0,now,"ru","UTC",null);
        check(-1,now,"ru","UTC",null);
        check(Long.MAX_VALUE,now,"ru","UTC",null);
        check(now+86401,now,"ru","UTC",null);
        check(time("2026-11-21T19:36:00+03:00"),now,"uk","Europe/Moscow","Учора о 19:36");
        check(time("2025-11-19T15:45:00+03:00"),now,"uk","Europe/Moscow","19 листопада 2025 о 15:45");
        check(time("2026-11-21T19:36:00+03:00"),now,"en","Europe/Moscow","Yesterday at 19:36");
        check(time("2025-11-19T15:45:00+03:00"),now,"en","Europe/Moscow","19 November 2025 at 15:45");
        check(time("2026-11-21T19:36:00+03:00"),now,"be","Europe/Moscow","Вчера в 19:36");
        System.out.println(checks+" publication-time cases passed");
    }
}'''

class PublicationTimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('java') and shutil.which('javac'), 'JDK required')
    def test_exact_local_publication_time(self):
        with tempfile.TemporaryDirectory(prefix='blaxyt-date-check-') as folder:
            work=pathlib.Path(folder)
            harness=work/'PublicationTimeHarness.java'
            harness.write_text(HARNESS,encoding='utf-8')
            source=ROOT/'inject/java/app/blaxyt/mod/PublicationTime.java'
            result=subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(source),str(harness)],capture_output=True,text=True,encoding='utf-8')
            self.assertEqual(result.returncode,0,result.stderr)
            result=subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',str(work),'app.blaxyt.mod.PublicationTimeHarness'],capture_output=True,text=True,encoding='utf-8')
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertIn('21 publication-time cases passed',result.stdout)

if __name__=='__main__':
    unittest.main()
