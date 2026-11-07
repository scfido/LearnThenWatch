import local.mytv.english.AppSettings;
import local.mytv.english.Curriculum;
import local.mytv.english.SessionEngine;
import local.mytv.english.ViewingPolicy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public final class StudyRulesTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long minute = 60000L;
        check(ViewingPolicy.grant(0, 10 * minute, 60 * minute, true) == 10 * minute, "first round earns ten minutes");
        check(ViewingPolicy.grant(minute, 10 * minute, 60 * minute, true) == 0, "cannot stack rounds");
        check(ViewingPolicy.grant(0, 10 * minute, 3 * minute, true) == 3 * minute, "last reward respects daily cap");
        check(ViewingPolicy.grant(0, 10 * minute, 0, true) == 0, "daily cap blocks rewards");
        check(ViewingPolicy.grant(0, 10 * minute, 60 * minute, false) == 0, "practice does not silently earn");
        check(ViewingPolicy.consume(5000, 1000, 60000) == 1000, "cannot spend beyond wallet");
        check(ViewingPolicy.consume(5000, 60000, 1000) == 1000, "cannot spend beyond daily remainder");
        check(ViewingPolicy.consume(-1, 60000, 60000) == 0, "negative consumption is ignored");
        check(ViewingPolicy.clampBalance(10 * minute, 30 * minute, 29 * minute) == minute, "lowering cap clips wallet");
        check(ViewingPolicy.dailyRemaining(30 * minute, 40 * minute) == 0, "daily remainder never negative");

        check(SessionEngine.dueDate("2026-12-31", 0).equals("2027-01-01"), "review crosses year boundary");
        check(SessionEngine.dueDate("2028-02-28", 0).equals("2028-02-29"), "review handles leap years");
        check(SessionEngine.nextReviewStep(3, true, false) == 3, "early correct practice cannot advance memory status");
        check(SessionEngine.nextReviewStep(3, false, true) == 0, "wrong answer resets interval");
        check(SessionEngine.nextReviewStep(4, true, true) == 5, "independent final review achieves mastery");

        List<Curriculum.Item> items = Arrays.asList(
            new Curriculum.Item("ket_a", "KET", "School", "teacher", "老师", "Our teacher reads a story.", "老师读故事。", "audio/a.mp3"),
            new Curriculum.Item("ket_b", "KET", "School", "book", "书", "This book is blue.", "这本书是蓝色的。", "audio/b.mp3"),
            new Curriculum.Item("ket_c", "KET", "Food", "apple", "苹果", "I eat an apple.", "我吃苹果。", "audio/c.mp3"),
            new Curriculum.Item("ket_d", "KET", "Travel", "arrive", "到达", "Meaning: arrive — 到达", "词义提示：到达", "audio/d.mp3")
        );
        Random random = new Random(11);
        for (Curriculum.Item item : items) for (int variant = 0; variant < 12; variant++) {
            SessionEngine.Question q = SessionEngine.question(item, items, variant, random);
            check(q.options.length == 3 && new HashSet<>(Arrays.asList(q.options)).size() == 3, "three distinct options");
            check(q.correct >= 0 && q.correct < 3, "answer is included");
            boolean usesWord = q.type.equals("LISTEN") || q.type.equals("SENTENCE");
            check(q.options[q.correct].equals(usesWord ? item.word : item.meaning), "answer matches learning item");
            if (!item.naturalExample()) check(!q.type.equals("SENTENCE") && !q.type.equals("CONTEXT"), "definition hints never become context exercises");
        }
        List<Curriculum.Item> synonyms = Arrays.asList(
            new Curriculum.Item("pet_movie", "PET", "Arts", "movie", "电影", "We watched a movie.", "我们看了一部电影。", "audio/e.mp3"),
            new Curriculum.Item("pet_film", "PET", "Arts", "film", "电影；胶片", "The film is long.", "电影很长。", "audio/f.mp3"),
            items.get(0), items.get(1), items.get(2));
        for (int variant = 0; variant < 4; variant++) {
            SessionEngine.Question q = SessionEngine.question(synonyms.get(0), synonyms, variant, random);
            for (int index = 0; index < q.options.length; index++) if (index != q.correct)
                check(!q.options[index].equals("film") && !q.options[index].contains("电影"), "synonyms cannot be competing answers");
        }
        AppSettings defaults = new AppSettings(); defaults.validate();
        check(defaults.taskCount == 5 && defaults.rewardMinutes == 10 && defaults.dailyLimitMinutes == 60, "agreed default policy");
        boolean rejected = false; defaults.rewardMinutes = 0;
        try { defaults.validate(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid parent settings rejected");
        System.out.println("PASS: wallet, cap, review spacing, contextual content, question choices, settings");
    }
}
