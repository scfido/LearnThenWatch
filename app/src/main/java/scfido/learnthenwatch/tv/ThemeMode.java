package scfido.learnthenwatch.tv;

/** Persisted appearance preference, resolved against the television's night-mode flag. */
public enum ThemeMode {
    AUTO("auto", "自动"), LIGHT("light", "浅色"), DARK("dark", "深色");

    public final String storedValue;
    public final String label;

    ThemeMode(String storedValue, String label) {
        this.storedValue = storedValue;
        this.label = label;
    }

    public static ThemeMode fromStored(String value) {
        for (ThemeMode mode : values()) if (mode.storedValue.equals(value)) return mode;
        return AUTO;
    }

    public ThemeMode next() {
        switch (this) {
            case AUTO: return LIGHT;
            case LIGHT: return DARK;
            default: return AUTO;
        }
    }

    public boolean isDark(boolean systemDark) {
        return this == DARK || (this == AUTO && systemDark);
    }
}
