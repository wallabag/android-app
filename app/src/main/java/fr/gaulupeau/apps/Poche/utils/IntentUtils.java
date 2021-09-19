package fr.gaulupeau.apps.Poche.utils;

import android.app.PendingIntent;
import android.os.Build;

public class IntentUtils {

    public static final int FLAG_IMMUTABLE
            = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;

}
