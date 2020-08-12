package fr.gaulupeau.apps.Poche.service;

import java.util.concurrent.Callable;

class CallableParameterizedAdapter<V> extends ParameterizedAdapter implements Callable<V> {

    protected final ParameterizedCallable<V> parameterizedCallable;

    public CallableParameterizedAdapter(ParameterizedCallable<V> parameterizedCallable) {
        this.parameterizedCallable = parameterizedCallable;
    }

    @Override
    public V call() throws Exception {
        return parameterizedCallable.call(context);
    }

}
