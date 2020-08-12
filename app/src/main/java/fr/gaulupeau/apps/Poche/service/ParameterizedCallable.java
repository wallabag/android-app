package fr.gaulupeau.apps.Poche.service;

import android.content.Context;

public interface ParameterizedCallable<V> {
    V call(Context context) throws Exception;
}
