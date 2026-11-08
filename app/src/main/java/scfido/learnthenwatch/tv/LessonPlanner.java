package scfido.learnthenwatch.tv;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic question construction and review rules, independent of Android. */
public final class LessonPlanner {
    public static final int[] REVIEW_DAYS = {1, 3, 7, 14, 30};
    private LessonPlanner() { }

    public static final class Question {
        public String type, prompt, explanation;
        public String[] options;
        public int correct;
    }

    public static Question question(VocabularyCatalog.Item item, List<VocabularyCatalog.Item> pool,
            int variant, Random random) {
        Question q = new Question();
        int v = Math.floorMod(variant, 4);
        if (v >= 2 && !item.naturalExample()) v %= 2;
        q.type = new String[] {"WORD", "LISTEN", "SENTENCE", "CONTEXT"}[v];
        String answer;
        boolean wordOptions = v == 1 || v == 2;
        if (v == 0) {
            q.prompt = "“" + item.word + "” 的意思是？"; answer = item.meaning;
        } else if (v == 1) {
            q.prompt = "听录音，选择你听到的单词或短语"; answer = item.word;
        } else if (v == 2) {
            Pattern p = Pattern.compile("(?i)(?<![a-z])" + Pattern.quote(item.word) + "(?![a-z])");
            Matcher m = p.matcher(item.example);
            if (m.find()) {
                q.prompt = "请选择表示「" + item.meaning + "」的词完成句子：\n" + m.replaceFirst("____");
                answer = item.word;
            } else {
                q.type = "CONTEXT"; wordOptions = false;
                q.prompt = item.example + "\n“" + item.word + "” 的意思是？";
                answer = item.meaning;
            }
        } else {
            q.prompt = item.example + "\n这里的 “" + item.word + "” 表示什么？";
            answer = item.meaning;
        }
        List<String> options = new ArrayList<>(); options.add(answer);
        List<VocabularyCatalog.Item> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        for (VocabularyCatalog.Item other : shuffled) {
            String candidate = wordOptions ? other.word : other.meaning;
            if (!sameWordFamily(other.word, item.word) && !meaningsOverlap(item.meaning, other.meaning)
                    && !containsIgnoreCase(options, candidate)) {
                options.add(candidate);
                if (options.size() == 3) break;
            }
        }
        if (options.size() != 3 && wordOptions) return question(item, pool, 0, random);
        if (options.size() != 3) throw new IllegalArgumentException("课程缺少不同义项的干扰选项");
        Collections.shuffle(options, random);
        q.options = options.toArray(new String[0]); q.correct = options.indexOf(answer);
        q.explanation = item.word + " · " + item.meaning + "\n" + item.example
                + (item.translation.isEmpty() ? "" : "\n" + item.translation);
        return q;
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        for (String v : values) if (v.toLowerCase(Locale.ROOT).equals(candidate.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    /** Avoid synonyms, alternative spellings and obvious inflections as competing answers. */
    private static boolean sameWordFamily(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String y = b.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (x.equals(y)) return true;
        for (String suffix : new String[]{"s", "es", "ed", "ing"})
            if (x.equals(y + suffix) || y.equals(x + suffix)) return true;
        return (x.endsWith("ies") && (x.substring(0, x.length() - 3) + "y").equals(y))
                || (y.endsWith("ies") && (y.substring(0, y.length() - 3) + "y").equals(x));
    }

    private static boolean meaningsOverlap(String a, String b) {
        Pattern chinese = Pattern.compile("[\\p{IsHan}]+");
        List<String> left = new ArrayList<>(), right = new ArrayList<>();
        Matcher x = chinese.matcher(a), y = chinese.matcher(b);
        while (x.find()) if (!"名动形副介代连数感助量".contains(x.group())) left.add(x.group());
        while (y.find()) if (!"名动形副介代连数感助量".contains(y.group())) right.add(y.group());
        for (String l : left) for (String r : right)
            if (l.contains(r) || r.contains(l)) return true;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    public static String dueDate(String day, int step) {
        int bounded = Math.max(0, Math.min(step, REVIEW_DAYS.length - 1));
        return LocalDate.parse(day).plusDays(REVIEW_DAYS[bounded]).toString();
    }

    /** Only a due, independently correct answer advances a review step. */
    public static int nextReviewStep(int step, boolean independentlyCorrect, boolean due) {
        if (!independentlyCorrect) return 0;
        return due ? Math.min(REVIEW_DAYS.length, step + 1) : step;
    }
}
