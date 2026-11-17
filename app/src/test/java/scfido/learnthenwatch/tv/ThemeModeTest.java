package scfido.learnthenwatch.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ThemeModeTest {
    @Test public void automaticUsesTheSystemFlagAndExplicitModesOverrideIt() {
        assertFalse(ThemeMode.AUTO.isDark(false));
        assertTrue(ThemeMode.AUTO.isDark(true));
        assertFalse(ThemeMode.LIGHT.isDark(false));
        assertFalse(ThemeMode.LIGHT.isDark(true));
        assertTrue(ThemeMode.DARK.isDark(false));
        assertTrue(ThemeMode.DARK.isDark(true));
    }

    @Test public void missingAndUnknownStoredValuesUseAutomatic() {
        assertEquals(ThemeMode.AUTO, ThemeMode.fromStored(null));
        assertEquals(ThemeMode.AUTO, ThemeMode.fromStored(""));
        assertEquals(ThemeMode.AUTO, ThemeMode.fromStored("unknown"));
        for (ThemeMode mode : ThemeMode.values())
            assertEquals(mode, ThemeMode.fromStored(mode.storedValue));
    }

    @Test public void settingsCopyAndDefaultsIncludeAppearance() {
        ParentSettings settings = new ParentSettings();
        assertEquals(ThemeMode.AUTO, settings.themeMode);
        settings.themeMode = ThemeMode.DARK;
        ParentSettings copy = settings.copy();
        assertEquals(ThemeMode.DARK, copy.themeMode);
        copy.themeMode = ThemeMode.LIGHT;
        assertEquals(ThemeMode.DARK, settings.themeMode);
        settings.validate();
        settings.themeMode = null;
        assertThrows(IllegalArgumentException.class, settings::validate);
    }
}
