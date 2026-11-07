package dev.learnthenwatch.tv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned, immutable offline course. Item identifiers never depend on file order. */
public final class VocabularyCatalog {
    public static final String VERSION = "2026.11.07";
    public final List<Item> items;
    private final Map<String, Item> byId = new LinkedHashMap<>();

    public static final class Item {
        public final String id, stage, topic, word, meaning, example, translation, audio;

        public Item(String id, String stage, String topic, String word, String meaning,
                    String example, String translation, String audio) {
            this.id = id; this.stage = stage; this.topic = topic; this.word = word;
            this.meaning = meaning; this.example = example;
            this.translation = translation; this.audio = audio;
        }

        /** Dictionary clues must not be presented as natural sentence exercises. */
        public boolean naturalExample() { return !example.startsWith("Meaning: "); }
    }

    public VocabularyCatalog(List<Item> source) {
        ArrayList<Item> copy = new ArrayList<>(source);
        for (Item item : copy) {
            if (item == null || item.id == null || byId.put(item.id, item) != null)
                throw new IllegalArgumentException("Duplicate or null curriculum item");
        }
        items = Collections.unmodifiableList(copy);
    }

    public Item get(String id) { return byId.get(id); }

    public List<Item> stage(String stage) {
        ArrayList<Item> result = new ArrayList<>();
        for (Item item : items) if (item.stage.equals(stage)) result.add(item);
        return Collections.unmodifiableList(result);
    }

    /** The caller owns the reader. Bad data prevents starting a reward-bearing session. */
    public static VocabularyCatalog load(Reader reader) throws IOException {
        BufferedReader input = new BufferedReader(reader);
        List<Item> result = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        String line;
        int number = 0;
        while ((line = input.readLine()) != null) {
            number++;
            if (number == 1 && line.startsWith("\ufeff")) line = line.substring(1);
            if (line.trim().isEmpty() || line.startsWith("#") || line.startsWith("id\tstage\t")) continue;
            String[] c = line.split("\t", -1);
            if (c.length != 8) throw new IOException("Course line " + number + ": expected eight columns");
            for (String value : c)
                if (value.trim().isEmpty()) throw new IOException("Course line " + number + ": blank field");
            if (!c[0].matches("[a-z0-9_]+") || seen.put(c[0], true) != null)
                throw new IOException("Course line " + number + ": invalid or duplicate ID");
            if (!c[1].equals("KET") && !c[1].equals("PET"))
                throw new IOException("Course line " + number + ": invalid level");
            if (!c[7].startsWith("audio/") || c[7].contains("..") || c[7].contains("\\"))
                throw new IOException("Course line " + number + ": invalid audio path");
            result.add(new Item(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]));
        }
        if (result.isEmpty()) throw new IOException("The course is empty");
        return new VocabularyCatalog(result);
    }
}
