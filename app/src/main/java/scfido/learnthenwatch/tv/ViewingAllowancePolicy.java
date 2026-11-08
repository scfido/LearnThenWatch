package scfido.learnthenwatch.tv;

/** Pure arithmetic for the single-round wallet. All inputs and outputs use milliseconds. */
public final class ViewingAllowancePolicy {
    private ViewingAllowancePolicy() { }

    public static long dailyRemaining(long dailyLimitMs, long usedMs) {
        return Math.max(0, dailyLimitMs - Math.max(0, usedMs));
    }

    public static long grant(long currentBalanceMs, long requestedMs, long remainingMs,
            boolean rewardEligible) {
        if (!rewardEligible || currentBalanceMs > 0 || remainingMs <= 0) return 0;
        return Math.max(0, Math.min(requestedMs, remainingMs));
    }

    public static long consume(long deltaMs, long currentBalanceMs, long remainingMs) {
        return Math.max(0, Math.min(Math.min(deltaMs, currentBalanceMs), remainingMs));
    }

    public static long clampBalance(long balanceMs, long dailyLimitMs, long usedMs) {
        return Math.max(0, Math.min(balanceMs, dailyRemaining(dailyLimitMs, usedMs)));
    }
}
