package fr.gaulupeau.apps.Poche.ui.preferences;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

/**
 * Based on http://kvance.livejournal.com/1039349.html
 */
public class FloatEditTextPreference extends EditTextPreference {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public FloatEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public FloatEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FloatEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FloatEditTextPreference(Context context) {
        super(context);
    }

    @Override
    protected boolean persistString(String value) {
        return value != null && persistFloat(Float.valueOf(value));
    }

    @Override
    protected String getPersistedString(String defaultReturnValue) {
        if(!shouldPersist() || !getSharedPreferences().contains(getKey())) {
            return defaultReturnValue;
        }

        return String.valueOf(getSharedPreferences().getFloat(getKey(), 0));
    }

}
