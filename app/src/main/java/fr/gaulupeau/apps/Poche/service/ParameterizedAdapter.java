package fr.gaulupeau.apps.Poche.service;

import android.content.Context;

abstract class ParameterizedAdapter implements Parameterized {

    protected Context context;

    @Override
    public void setContext(Context context) {
        this.context = context;
    }

}
