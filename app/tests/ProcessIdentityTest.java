package nl.retroid.touchguard;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class ProcessIdentityTest {
    public static void main(String[] args) throws Exception {
        StringBuilder stat = new StringBuilder("42 (retroid (touch) guard) S");
        for (int i = 1; i <= 30; i++) stat.append(' ').append(i);
        if (!"19".equals(ProcessIdentity.startTicks(stat.toString()))) throw new AssertionError("PID reuse identity");
        for (String bad : new String[]{"42 retroid S 1", "42 (retroid) S 1 2"}) {
            try { ProcessIdentity.startTicks(bad); throw new AssertionError("Truncated identity accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        String value = "/data/test dir/a'$(printf substituted)`printf changed`";
        Process shell = new ProcessBuilder("/bin/sh", "-c", "printf '%s' " + ProcessIdentity.quote(value)).start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        int count;
        while ((count = shell.getInputStream().read(buffer)) >= 0) bytes.write(buffer, 0, count);
        if (shell.waitFor() != 0 || !value.equals(new String(bytes.toByteArray(), StandardCharsets.UTF_8))) {
            throw new AssertionError("Shell path quoting executed substitutions or lost content");
        }
        System.out.println("Process identity and shell argument checks passed");
    }
}
