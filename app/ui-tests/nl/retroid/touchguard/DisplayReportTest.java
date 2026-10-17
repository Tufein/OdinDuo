package nl.retroid.touchguard;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.display.DisplayManager;
import android.view.Display;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDisplayManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w1080dp-h1920dp-mdpi")
public class DisplayReportTest {
    @Test public void reportsSeparateDisplayStatesWithoutChangingThem() {
        Context context = RuntimeEnvironment.getApplication();
        DisplayManager manager = context.getSystemService(DisplayManager.class);
        int externalId = ShadowDisplayManager.addDisplay("w1920dp-h1080dp-land-mdpi", "private display name");
        Display primary = manager.getDisplay(Display.DEFAULT_DISPLAY);
        Display external = manager.getDisplay(externalId);
        Shadows.shadowOf(primary).setState(Display.STATE_OFF);
        Shadows.shadowOf(external).setState(Display.STATE_ON);

        String report = DisplayReport.read(context);

        assertTrue(report, report.contains("Default display ID: 0\nDefault display: present"));
        assertTrue(report, report.contains("Available display IDs: 0, " + externalId));
        assertTrue(report, report.contains("Display 0 (default): valid=true; reported_state=OFF (1)"));
        assertTrue(report, report.contains("Display " + externalId + ": valid=true; reported_state=ON (2)"));
        assertTrue(report, report.contains("physical=1920x1080"));
        assertTrue(report, report.contains("refresh=60.00 Hz"));
        assertFalse(report.contains("private display name"));
        assertEquals(Display.STATE_OFF, primary.getState());
        assertEquals(Display.STATE_ON, external.getState());
        assertEquals(2, manager.getDisplays().length);
    }

    @Test public void handlesAbsentDefaultDisplay() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSystemService(DisplayManager.class);
        ShadowDisplayManager.removeDisplay(Display.DEFAULT_DISPLAY);

        String report = DisplayReport.read(context);

        assertTrue(report, report.contains("Default display: absent"));
        assertTrue(report, report.contains("Available display IDs: none"));
    }

    @Test public void handlesMissingDisplayService() {
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Object getSystemService(String name) {
                return Context.DISPLAY_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };

        assertTrue(DisplayReport.read(context).contains("Display service: unavailable"));
    }

    @Test public void handlesDeniedDisplayServiceWithoutExportingExceptionMessage() {
        Context context = failingContext(new SecurityException("private vendor identifier"));

        String report = DisplayReport.read(context);

        assertTrue(report, report.contains("Display service: permission denied"));
        assertFalse(report.contains("private vendor identifier"));
    }

    @Test public void handlesServiceFailureWithoutExportingExceptionMessage() {
        Context context = failingContext(new IllegalStateException("private vendor identifier"));

        String report = DisplayReport.read(context);

        assertTrue(report, report.contains("Display service: unavailable (IllegalStateException)"));
        assertFalse(report.contains("private vendor identifier"));
    }

    private static Context failingContext(RuntimeException failure) {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Object getSystemService(String name) {
                if (Context.DISPLAY_SERVICE.equals(name)) throw failure;
                return super.getSystemService(name);
            }
        };
    }
}
