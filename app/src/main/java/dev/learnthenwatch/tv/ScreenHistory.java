package dev.learnthenwatch.tv;

import java.util.ArrayList;
import java.util.List;

/** Destination history, including the remote focus and scroll position at each entry. */
public final class ScreenHistory {
    public static final class Screen {
        public final String name, argument;
        public final int index;
        public String focus, contentFocus;
        public int scroll;
        public Screen(String name) { this(name, "", 0); }
        public Screen(String name, String argument) { this(name, argument, 0); }
        public Screen(String name, String argument, int index) {
            this.name = name; this.argument = argument; this.index = index;
        }
        public boolean same(Screen other) {
            return other != null && name.equals(other.name) && argument.equals(other.argument) && index == other.index;
        }
    }
    private final List<Screen> history = new ArrayList<>();
    public ScreenHistory(Screen home) { reset(home); }
    public Screen current() { return history.get(history.size() - 1); }
    public void open(Screen screen) { if (!current().same(screen)) history.add(screen); }
    public void replace(Screen screen) { history.set(history.size() - 1, screen); }
    public void reset(Screen screen) { history.clear(); history.add(screen); }
    public Screen back() {
        if (history.size() <= 1) return null;
        history.remove(history.size() - 1); return current();
    }
    public boolean backTo(String name) {
        for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).name.equals(name)) {
            while (history.size() > i + 1) history.remove(history.size() - 1);
            return true;
        }
        return false;
    }
    public List<Screen> entries() { return new ArrayList<>(history); }
    public void restore(List<Screen> entries) {
        if (entries == null || entries.isEmpty()) return;
        history.clear(); history.addAll(entries);
    }
}
