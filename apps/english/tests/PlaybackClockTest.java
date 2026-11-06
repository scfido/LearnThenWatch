import local.mytv.english.PlaybackClock;
import local.mytv.english.Questions;
import java.util.Random;

public class PlaybackClockTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        PlaybackClock clock = new PlaybackClock(0);
        clock.tick(0, true);
        for (int second = 1; second <= 119; second++) clock.tick(second * 1000, true);
        check(clock.elapsed() == 119000, "must not ask before two minutes");
        clock.tick(120000, true);
        check(clock.elapsed() == 120000, "must ask at two minutes");
        clock.tick(121000, false); clock.tick(500000, false); clock.tick(501000, true);
        check(clock.elapsed() == 120000, "paused time must not count");
        clock.tick(502000, true);
        check(clock.elapsed() == 121000, "resumed playback must count");
        clock.tick(900000, true);
        check(clock.elapsed() == 123000, "a sleeping or disconnected monitor must not charge the gap");
        PlaybackClock restored = new PlaybackClock(clock.elapsed());
        restored.tick(1000000, true);
        check(restored.elapsed() == 123000, "restoring must not charge process downtime");
        restored.reset(); restored.tick(1100000, true);
        check(restored.elapsed() == 0, "correct answer starts a new interval");
        Random random = new Random(42);
        int last = -1;
        for (int i = 0; i < 1000; i++) {
            Questions q = new Questions(random, last);
            check(q.index != last, "do not immediately repeat a question");
            check(q.correct >= 0 && q.correct < 3, "answer must be displayed");
            check(!q.options[0].equals(q.options[1]) && !q.options[0].equals(q.options[2])
                    && !q.options[1].equals(q.options[2]), "choices must be distinct");
            last = q.index;
        }
        System.out.println("PASS: playback threshold, pause, resume, sleep, restore, reset, question selection");
    }
}
