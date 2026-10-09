package nl.retroid.touchguard;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import java.util.Locale;

final class AppLanguage {
    private AppLanguage() { }

    static Context current(Context context) {
        LocaleManager manager = context.getSystemService(LocaleManager.class);
        LocaleList locales = manager == null ? LocaleList.getEmptyLocaleList() : manager.getApplicationLocales();
        if (locales.isEmpty()) locales = Resources.getSystem().getConfiguration().getLocales();
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocales(locales);
        return context.createConfigurationContext(configuration);
    }

    static Context english(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(Locale.ENGLISH));
        return context.createConfigurationContext(configuration);
    }
}
