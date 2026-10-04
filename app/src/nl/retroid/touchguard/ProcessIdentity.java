package nl.retroid.touchguard;

final class ProcessIdentity {
    private ProcessIdentity() { }

    static String startTicks(String stat) {
        int end = stat.lastIndexOf(") ");
        if (end < 0) throw new IllegalArgumentException("Invalid process identity");
        String[] fields = stat.substring(end + 2).trim().split("\\s+");
        if (fields.length < 20 || !fields[19].matches("[0-9]+")) {
            throw new IllegalArgumentException("Missing process start time");
        }
        return fields[19];
    }

    static String quote(String value) {
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid path");
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
