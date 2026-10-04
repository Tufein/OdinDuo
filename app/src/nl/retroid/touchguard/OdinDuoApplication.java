package nl.retroid.touchguard;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public final class OdinDuoApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
