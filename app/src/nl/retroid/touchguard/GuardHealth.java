package nl.retroid.touchguard;

/** Helper liveness policy. Call with uptime, which excludes time spent in deep sleep. */
final class GuardHealth {
    enum State { HEALTHY, STARTING, RECOVERING, LOST }
    private long launchedAt;
    private long missingSince = -1;
    private boolean seenHeartbeat;
    private long recoveryWindow = -1;
    private int recoveries;

    void launched(long uptime) {
        launchedAt = uptime;
        missingSince = -1;
        seenHeartbeat = false;
    }

    State observe(boolean alive, long uptime) {
        if (alive) {
            seenHeartbeat = true;
            missingSince = -1;
            return State.HEALTHY;
        }
        if (!seenHeartbeat) return uptime - launchedAt >= 15000 ? State.LOST : State.STARTING;
        if (missingSince < 0) missingSince = uptime;
        return uptime - missingSince >= 10000 ? State.LOST : State.RECOVERING;
    }

    boolean startupExpired(long uptime) { return uptime - launchedAt >= 15000; }

    boolean allowRecovery(long uptime) {
        if (recoveryWindow < 0 || uptime - recoveryWindow >= 60000) {
            recoveryWindow = uptime;
            recoveries = 0;
        }
        return ++recoveries <= 2;
    }
}
