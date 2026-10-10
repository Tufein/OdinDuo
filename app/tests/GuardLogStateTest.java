package nl.retroid.touchguard;

public final class GuardLogStateTest {
    private static void expect(GuardLogState.State expected, String log) {
        GuardLogState.State actual = GuardLogState.read(log);
        if (actual != expected) throw new AssertionError(expected + " != " + actual);
    }

    private static void expectDetach(boolean expected, String log) {
        if (GuardLogState.stoppedAfterDetach(log) != expected)
            throw new AssertionError("Incorrect detach classification: " + log);
    }

    public static void main(String[] args) {
        expect(GuardLogState.State.EMPTY, "");
        expect(GuardLogState.State.WAITING, "100 READY waiting for RDS; owner=123");
        expect(GuardLogState.State.STARTING, "RDS detected path=/sys/device");
        String active = "100 STATE control=on runtime=active host=active\n";
        expect(GuardLogState.State.ACTIVE, active);
        expect(GuardLogState.State.STARTING, active + "101 STATE control=auto runtime=suspended host=active\n");
        expect(GuardLogState.State.ACTIVE, active + "101 STATE control=on runtime=suspended host=active\n102 " + active);
        expect(GuardLogState.State.STOPPED, active + "101 GUARD stopped\nSTOP acknowledged test result=0\n");
        expect(GuardLogState.State.ERROR, active + "RESTORE FAILED /sys/device\nGUARD stopped\n");
        expect(GuardLogState.State.ERROR, "PIN readback failed /sys/device");
        expect(GuardLogState.State.ERROR, "App identity verification failed");
        // Stock Odin trace: a killed waiting worker is cleaned up by its watchdog.
        // It must offer Retry rather than claiming a display was disconnected.
        String waiting = "1791595335 READY waiting for RDS; duration=0s owner=20964 worker=27481 watchdog=27507\n";
        String watchdogStop = "1791595342 GUARD stopped (watchdog)\n1791595343 STOP completed\nSTOP acknowledged test result=0\n";
        expectDetach(false, waiting + watchdogStop);
        String detach = "RDS detached/replaced; restoring\nGUARD stopped\n";
        expectDetach(true, waiting + active + detach);
        expectDetach(false, waiting + active + detach + waiting + watchdogStop);
        expectDetach(false, waiting + active + "RDS detached/replaced; restoring\nRESTORE FAILED /sys/device\nGUARD stopped\n");
        expectDetach(false, waiting + active + "RDS detached/replaced; restoring\n");
        System.out.println("Guard status checks passed: latest observation, disconnect, watchdog stop and error precedence");
    }
}
