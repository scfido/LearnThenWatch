package dev.learnthenwatch.tv;

/** Parent preferences. A copy is returned by the repository. */
public final class ParentSettings {
    public int taskCount = 5;
    public int rewardMinutes = 10;
    public int dailyLimitMinutes = 60;
    public int dailyNewLimit = 5;
    public int currentUnit = 0;
    public int volume = 80;
    public String stage = "KET";
    public boolean autoSpeak = true;
    public boolean chineseHints = true;
    public boolean enabled = true;

    public ParentSettings copy() {
        ParentSettings c = new ParentSettings();
        c.taskCount = taskCount; c.rewardMinutes = rewardMinutes;
        c.dailyLimitMinutes = dailyLimitMinutes; c.dailyNewLimit = dailyNewLimit;
        c.currentUnit = currentUnit; c.volume = volume; c.stage = stage;
        c.autoSpeak = autoSpeak; c.chineseHints = chineseHints; c.enabled = enabled;
        return c;
    }

    public void validate() {
        if (taskCount < 1 || taskCount > 20 || rewardMinutes < 1 || rewardMinutes > 60
                || dailyLimitMinutes < 1 || dailyLimitMinutes > 240
                || dailyNewLimit < 1 || dailyNewLimit > 30 || currentUnit < 0
                || volume < 0 || volume > 100 || !("KET".equals(stage) || "PET".equals(stage))) {
            throw new IllegalArgumentException("家长配置超出有效范围");
        }
    }
}
