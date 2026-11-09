package scfido.learnthenwatch.tv;

import java.util.ArrayList;
import java.util.List;

/** Recovery only edits our component; each write starts from the latest system list. */
final class MonitorRecoveryPolicy {
    interface Access {
        boolean allowed();
        boolean connected();
        String services();
        void services(String value);
        boolean accessibilityEnabled();
        void enableAccessibility();
    }
    private final Access access;
    private final String component;
    MonitorRecoveryPolicy(Access access, String component) { this.access = access; this.component = component; }

    void restore() {
        if (!access.allowed() || access.connected()) return;
        String current = access.services();
        if (!contains(current)) {
            List<String> entries = entries(current);
            entries.add(component);
            access.services(String.join(":", entries));
        }
        if (!access.accessibilityEnabled()) access.enableAccessibility();
    }

    boolean detachStalled() {
        if (!access.allowed() || access.connected()) return false;
        String current = access.services();
        if (!contains(current)) return false;
        List<String> others = entries(current);
        others.removeIf(entry -> canonical(entry).equals(canonical(component)));
        access.services(String.join(":", others));
        return true;
    }

    private boolean contains(String value) {
        for (String entry : entries(value)) if (canonical(entry).equals(canonical(component))) return true;
        return false;
    }
    private static List<String> entries(String value) {
        List<String> result = new ArrayList<>();
        if (value != null) for (String entry : value.split(":")) {
            if (!entry.isEmpty() && !"null".equals(entry)) result.add(entry);
        }
        return result;
    }
    private static String canonical(String value) {
        int slash = value.indexOf('/');
        if (slash >= 0 && slash + 1 < value.length() && value.charAt(slash + 1) == '.')
            return value.substring(0, slash + 1) + value.substring(0, slash) + value.substring(slash + 1);
        return value;
    }
}
