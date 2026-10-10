package nl.retroid.touchguard;

/** Session duration uses elapsed time within one boot, never the editable wall clock. */
final class SessionClock {
    private SessionClock() { }

    static boolean sameBoot(String saved, String current) {
        return saved != null && !saved.isEmpty() && saved.equals(current);
    }

    static long start(boolean wasRunning, String savedBoot, String boot, long savedStart, long now) {
        return wasRunning && sameBoot(savedBoot, boot) && savedStart >= 0 && savedStart <= now
                ? savedStart : now;
    }

    static long duration(String savedBoot, String boot, long start, long now) {
        return sameBoot(savedBoot, boot) && start >= 0 && start <= now ? now - start : -1;
    }
}
