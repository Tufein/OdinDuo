package nl.retroid.touchguard;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class GuardServiceLifecycleTest {
    @Test public void destructionWaitsForEarlierRestorationEvenWithoutStarting() throws Exception {
        Field field = GuardService.class.getDeclaredField("pendingRestoration");
        field.setAccessible(true);
        CountDownLatch previous = new CountDownLatch(1);
        field.set(null, previous);
        ServiceController<GuardService> controller = Robolectric.buildService(GuardService.class).create();
        controller.destroy();
        CountDownLatch current = (CountDownLatch) field.get(null);
        try {
            assertNotSame(previous, current);
            assertFalse("A later cleanup must not finish ahead of the previous one",
                    current.await(1, TimeUnit.SECONDS));
        } finally {
            previous.countDown();
            assertTrue("Cleanup should finish after its predecessor", current.await(5, TimeUnit.SECONDS));
            field.set(null, new CountDownLatch(0));
        }
    }

    @Test public void rapidCreateDestroyKeepsTheWholeRestorationChain() throws Exception {
        Field field = GuardService.class.getDeclaredField("pendingRestoration");
        field.setAccessible(true);
        CountDownLatch previous = new CountDownLatch(1);
        field.set(null, previous);
        ServiceController<GuardService> first = Robolectric.buildService(GuardService.class).create();
        first.destroy();
        CountDownLatch firstDone = (CountDownLatch) field.get(null);
        ServiceController<GuardService> second = Robolectric.buildService(GuardService.class).create();
        second.destroy();
        CountDownLatch secondDone = (CountDownLatch) field.get(null);
        try {
            assertFalse("A rapid stop must not release a new launch while old cleanup is pending",
                    secondDone.await(1, TimeUnit.SECONDS));
            assertEquals(1L, firstDone.getCount());
        } finally {
            previous.countDown();
            assertTrue(firstDone.await(5, TimeUnit.SECONDS));
            assertTrue(secondDone.await(5, TimeUnit.SECONDS));
            field.set(null, new CountDownLatch(0));
        }
    }
}
