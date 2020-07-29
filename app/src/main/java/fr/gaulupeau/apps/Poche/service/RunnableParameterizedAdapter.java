package fr.gaulupeau.apps.Poche.service;

class RunnableParameterizedAdapter extends ParameterizedAdapter implements Runnable {

    protected final ParameterizedRunnable parameterizedRunnable;

    public RunnableParameterizedAdapter(ParameterizedRunnable parameterizedRunnable) {
        this.parameterizedRunnable = parameterizedRunnable;
    }

    @Override
    public void run() {
        parameterizedRunnable.run(context);
    }

}
