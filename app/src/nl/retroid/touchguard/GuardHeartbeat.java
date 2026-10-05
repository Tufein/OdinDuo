package nl.retroid.touchguard;

/** Session-scoped observations, separate from the bounded diagnostic log. */
final class GuardHeartbeat {
    final boolean alive;
    final GuardLogState.State state;

    private GuardHeartbeat(boolean alive, GuardLogState.State state) {
        this.alive = alive;
        this.state = state;
    }

    static GuardHeartbeat read(String token, String record, long elapsedSeconds) {
        try {
            String[] fields = record.trim().split("\\s+");
            if (!token.matches("[a-f0-9]{32}") || fields.length < 2 || fields.length > 3
                    || !token.equals(fields[0])) return missing();
            long seconds = Long.parseLong(fields[1]);
            if (seconds < 0 || seconds > elapsedSeconds || elapsedSeconds - seconds > 10) return missing();
            GuardLogState.State state = null;
            if (fields.length == 3) {
                state = GuardLogState.State.valueOf(fields[2]);
                if (state != GuardLogState.State.WAITING && state != GuardLogState.State.STARTING
                        && state != GuardLogState.State.ACTIVE) return missing();
            }
            // Two-field heartbeats remain valid for sessions adopted from older APKs.
            return new GuardHeartbeat(true, state);
        } catch (RuntimeException exception) { return missing(); }
    }

    static GuardHeartbeat missing() { return new GuardHeartbeat(false, null); }

    GuardLogState.State observation(String log) {
        GuardLogState.State logged = GuardLogState.read(log);
        if (logged == GuardLogState.State.ERROR || logged == GuardLogState.State.STOPPED) return logged;
        return alive && state != null ? state : logged;
    }
}
