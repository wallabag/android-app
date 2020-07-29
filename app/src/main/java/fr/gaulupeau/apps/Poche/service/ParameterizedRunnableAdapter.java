package fr.gaulupeau.apps.Poche.service;

import android.content.Context;

import java.util.Objects;

class ParameterizedRunnableAdapter implements ParameterizedRunnable {

    protected Runnable runnable;
    protected Parameterized parameterized;

    public ParameterizedRunnableAdapter(Runnable runnable, Parameterized parameterized) {
        this.runnable = Objects.requireNonNull(runnable);
        this.parameterized = parameterized;
    }

    @Override
    public void run(Context context) {
        try {
            if (parameterized != null) parameterized.setContext(context);
            runnable.run();
        } finally {
            if (parameterized != null) parameterized.setContext(null);
        }
    }

}
