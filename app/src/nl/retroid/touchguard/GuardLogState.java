package nl.retroid.touchguard;

/** Interpret the latest power observation, rather than an earlier successful sample. */
final class GuardLogState {
    enum State { EMPTY, WAITING, STARTING, ACTIVE, STOPPED, ERROR }

    static State read(String log) {
        State state = State.EMPTY;
        for (String line : log.split("\n")) {
            if (line.contains("FAILED") || line.contains("readback failed")
                    || line.contains("already exists") || line.contains("verification failed")
                    || line.contains("rejected unexpected")) return State.ERROR;
            if (line.contains("GUARD stopped")) state = State.STOPPED;
            else if (line.contains("STATE control=on runtime=active host=active")) state = State.ACTIVE;
            else if (line.contains("STATE ") || line.contains("RDS detected")) state = State.STARTING;
            else if (line.contains("READY waiting for RDS")) state = State.WAITING;
        }
        return state;
    }
}
