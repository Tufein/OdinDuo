package nl.retroid.touchguard;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;

public final class OdinDuoApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(getSharedPreferences("appearance", MODE_PRIVATE)
                .getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
