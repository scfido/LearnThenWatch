package dev.learnthenwatch.tv;

import dev.learnthenwatch.tv.VocabularyCatalog;
import dev.learnthenwatch.tv.LessonPlanner;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Exercise every shipped item against the real question generator and bundled media. */
public final class VocabularyCatalogTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @org.junit.Test
    public void regression() throws Exception {
        File assets = new File(System.getProperty("learnthenwatch.assetsDir")); VocabularyCatalog course;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(new File(assets, "curriculum.tsv")), StandardCharsets.UTF_8)) {
            course = VocabularyCatalog.load(reader);
        }
        int questions = 0, examples = 0; Set<String> audio = new HashSet<>();
        for (String level : new String[]{"KET", "PET"}) {
            List<VocabularyCatalog.Item> items = course.stage(level);
            check(items.size() >= 1000, "Full stage missing: " + level);
            for (VocabularyCatalog.Item item : items) {
                File media = new File(assets, item.audio);
                check(media.isFile() && media.length() > 100, "Missing or empty audio: " + item.id);
                audio.add(item.audio); if (item.naturalExample()) examples++;
                for (int variant = 0; variant < 4; variant++) {
                    LessonPlanner.Question q = LessonPlanner.question(item, items, variant, new Random(item.id.hashCode() + variant));
                    check(q.options.length == 3 && new HashSet<>(Arrays.asList(q.options)).size() == 3, "Duplicate options: " + item.id);
                    check(q.correct >= 0 && q.correct < 3, "Answer not displayed: " + item.id);
                    boolean word = "LISTEN".equals(q.type) || "SENTENCE".equals(q.type);
                    check(q.options[q.correct].equals(word ? item.word : item.meaning), "Answer does not match item: " + item.id);
                    check(item.naturalExample() || !("CONTEXT".equals(q.type) || "SENTENCE".equals(q.type)), "Definition presented as example: " + item.id);
                    questions++;
                }
            }
        }
        System.out.println("PASS: " + course.items.size() + " items, " + examples + " natural examples, " + audio.size()
                + " bundled audio files, " + questions + " generated questions");
    }
}
