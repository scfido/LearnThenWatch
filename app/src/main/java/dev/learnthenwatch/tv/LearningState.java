package dev.learnthenwatch.tv;

/** Read models shared by the launcher and accessibility overlay. */
public final class LearningState {
    private LearningState() { }

    public static final class Snapshot {
        public String day, activeSessionId;
        public long balanceMs, todayUsedMs, dailyRemainingMs;
        public int learnedCount, dueCount, masteredCount, todayRounds, firstCorrect, totalAnswered;
    }

    public static final class Session {
        public String id, stage, status;
        public long rewardMs, grantedMs;
        public int taskCount, completedTasks, correctionCount;
        public boolean rewardEligible;
    }

    public static final class Task {
        public String id, itemId, type, prompt, explanation;
        public String[] options;
        public int correct, correctionAttempts;
        public boolean correction, introduced, needsIntro;
    }

    public static final class AnswerResult {
        public boolean correct, firstAttempt, needsCorrection, sessionComplete;
        public String message;
    }

    public static final class ItemProgress {
        public String itemId, stage, dueDay;
        public boolean learned, mastered;
        public int wrongCount, reviewStep;
    }

    public static final class DayStats {
        public String day;
        public long usedMs;
        public int rounds, firstCorrect, totalAnswered, newCount;
    }
}
