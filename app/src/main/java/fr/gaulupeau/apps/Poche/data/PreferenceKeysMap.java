package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.preference.Preference;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;

public class PreferenceKeysMap {

    private static final String TAG = PreferenceKeysMap.class.getSimpleName();

    private static class Holder {
        static final PreferenceKeysMap INSTANCE = new PreferenceKeysMap(App.getInstance());
    }

    private final Map<String, Integer> preferenceKeysMap;

    public static PreferenceKeysMap getInstance() {
        return Holder.INSTANCE;
    }

    public PreferenceKeysMap(Context context) {
        preferenceKeysMap = new HashMap<>();

        try {
            for (Field field : R.string.class.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)
                        && !Modifier.isPrivate(modifiers)
                        && field.getType().equals(int.class)) {
                    try {
                        if (field.getName().startsWith("pref_key_")) {
                            int resID = field.getInt(null);
                            addToMap(context, resID);
                        }
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        Log.e(TAG, "init() exception", e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "init() exception", e);
        }
    }

    private void addToMap(Context context, int resID) {
        preferenceKeysMap.put(context.getString(resID), resID);
    }

    public int getPrefKeyId(Preference preference) {
        return getPrefKeyIdByStringKey(preference.getKey());
    }

    public int getPrefKeyIdByStringKey(String value) {
        if (TextUtils.isEmpty(value)) return -1;

        Integer id = preferenceKeysMap.get(value);

        return id != null ? id : -1;
    }

}
