package fr.gaulupeau.apps.Poche.ui;

import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;

import android.app.Activity;
import android.os.Build;

import java.util.Map;
import java.util.WeakHashMap;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;

public class Themes {

    private static Theme theme;

    private static Map<Activity, Theme> appliedThemes = new WeakHashMap<>();

    static {
        init();
    }

    public static void init() {
        Themes.theme = App.getSettings().getTheme();
    }

    public static Theme getCurrentTheme() {
        return theme;
    }

    public static void applyTheme(Activity activity) {
        applyTheme(activity, true);
    }

    public static void applyTheme(Activity activity, boolean actionBar) {
        activity.setTheme(actionBar ? theme.getResId() : theme.getNoActionBarResId());
        appliedThemes.put(activity, theme);
        applyDarkTheme();
    }

    public static void applyDarkTheme() {
        if (theme.name().toLowerCase().contains("light")) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (theme.name().toLowerCase().contains("dark")) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            // `Set by Battery Saver` for Q above (inclusive), `Use system default` for Q below
            // https://medium.com/androiddevelopers/appcompat-v23-2-daynight-d10f90c83e94
            AppCompatDelegate.setDefaultNightMode(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ?
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static void applyDialogTheme(final Activity activity) {
        activity.setTheme(theme.getDialogResId());
        appliedThemes.put(activity, theme);
        applyDarkTheme();
    }

    public static void checkTheme(Activity activity) {
        Theme appliedTheme = appliedThemes.get(activity);
        if (appliedTheme != theme) {
            activity.recreate();
        }
    }

    public enum Theme {
        DEFAULT(
                R.string.themeName_default,
                R.style.Theme_App,
                R.style.Theme_App_NoActionBar,
                R.style.Theme_App_DialogTheme
        ),

        CONTRAST(
                R.string.themeName_contrast,
                R.style.Theme_App_Contrast,
                R.style.Theme_App_Contrast_NoActionBar,
                R.style.Theme_App_DialogTheme
        ),

        LIGHT(
                R.string.themeName_light,
                R.style.Theme_App,
                R.style.Theme_App_NoActionBar,
                R.style.Theme_App_DialogTheme
        ),

        LIGHT_CONTRAST(
                R.string.themeName_light_contrast,
                R.style.Theme_App_Contrast,
                R.style.Theme_App_Contrast_NoActionBar,
                R.style.Theme_App_DialogTheme
        ),

        E_INK(
                R.string.themeName_eink,
                R.style.Theme_App_Contrast,
                R.style.Theme_App_Contrast_NoActionBar,
                R.style.Theme_App_DialogTheme
        ),

        DARK(
                R.string.themeName_dark,
                R.style.Theme_App,
                R.style.Theme_App_NoActionBar,
                R.style.Theme_App_DialogTheme
        ),

        DARK_CONTRAST(
                R.string.themeName_dark_contrast,
                R.style.Theme_App_Contrast,
                R.style.Theme_App_Contrast_NoActionBar,
                R.style.Theme_App_DialogTheme
        ),

        SOLARIZED(
                R.string.themeName_solarized,
                R.style.Theme_App_Solarized,
                R.style.Theme_App_Solarized_NoActionBar,
                R.style.Theme_App_DialogTheme
        );

        private int nameId;
        private int resId;
        private int noActionBarResId;
        private int dialogResId;

        Theme(@StringRes int nameId, @StyleRes int resId,
              @StyleRes int noActionBarResId, @StyleRes int dialogResId) {
            this.nameId = nameId;
            this.resId = resId;
            this.noActionBarResId = noActionBarResId;
            this.dialogResId = dialogResId;
        }

        public @StringRes int getNameId() {
            return nameId;
        }

        public @StyleRes int getResId() {
            return resId;
        }

        public @StyleRes int getNoActionBarResId() {
            return noActionBarResId;
        }

        public @StyleRes int getDialogResId() {
            return dialogResId;
        }

    }

}
