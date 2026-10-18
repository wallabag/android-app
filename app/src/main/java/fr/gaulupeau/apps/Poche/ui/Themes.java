package fr.gaulupeau.apps.Poche.ui;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import com.google.android.material.color.DynamicColors;

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
        return theme.resolve();
    }

    public static void applyTheme(Activity activity) {
        applyTheme(activity, true);
    }

    public static void applyTheme(Activity activity, boolean actionBar) {
        Theme currentTheme = getCurrentTheme();
        activity.setTheme(actionBar ? currentTheme.getResId() : currentTheme.getNoActionBarResId());
        applyDynamicColors(activity, currentTheme);
        appliedThemes.put(activity, currentTheme);
    }

    public static void applyDialogTheme(Activity activity) {
        Theme currentTheme = getCurrentTheme();
        activity.setTheme(currentTheme.getDialogResId());
        applyDynamicColors(activity, currentTheme);
        appliedThemes.put(activity, currentTheme);
    }

    private static void applyDynamicColors(Activity activity, Theme currentTheme) {
        if(currentTheme.isDynamicColorsSupported()) {
            DynamicColors.applyToActivityIfAvailable(activity);
        }
    }

    public static void checkTheme(Activity activity) {
        Theme appliedTheme = appliedThemes.get(activity);
        if(appliedTheme != getCurrentTheme()) activity.recreate();
    }

    public enum Theme {
        SYSTEM(
                R.string.themeName_system,
                R.style.LightTheme,
                R.style.LightTheme_NoActionBar,
                R.style.DialogTheme
        ),

        LIGHT(
                R.string.themeName_light,
                R.style.LightTheme,
                R.style.LightTheme_NoActionBar,
                R.style.DialogTheme
        ),

        LIGHT_CONTRAST(
                R.string.themeName_light_contrast,
                R.style.LightThemeContrast,
                R.style.LightThemeContrast_NoActionBar,
                R.style.DialogTheme
        ),

        E_INK(
                R.string.themeName_eink,
                R.style.LightThemeContrast,
                R.style.LightThemeContrast_NoActionBar,
                R.style.DialogTheme
        ),

        DARK(
                R.string.themeName_dark,
                R.style.DarkTheme,
                R.style.DarkTheme_NoActionBar,
                R.style.DialogThemeDark
        ),

        DARK_CONTRAST(
                R.string.themeName_dark_contrast,
                R.style.DarkThemeContrast,
                R.style.DarkThemeContrast_NoActionBar,
                R.style.DialogThemeDark
        ),

        SOLARIZED(
                R.string.themeName_solarized,
                R.style.SolarizedTheme,
                R.style.SolarizedTheme_NoActionBar,
                R.style.DialogTheme
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

        public Theme resolve() {
            if(this != SYSTEM) {
                return this;
            }

            int nightMode = Resources.getSystem().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK
            ;

            return nightMode == Configuration.UI_MODE_NIGHT_YES ? DARK : LIGHT;
        }

        public boolean isDynamicColorsSupported() {
            return this == LIGHT || this == DARK;
        }

    }

}
