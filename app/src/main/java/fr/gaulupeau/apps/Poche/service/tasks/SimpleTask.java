package fr.gaulupeau.apps.Poche.service.tasks;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.reflect.Array;

public class SimpleTask implements Parcelable {

    public static final String SIMPLE_TASK = "wallabag.extra.simple_task";

    public static SimpleTask fromIntent(Intent intent) {
        return intent.getParcelableExtra(SIMPLE_TASK);
    }

    public void run(Context context) {
        // nothing
    }

    // Parcelable implementation

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // nothing
    }

    protected void readFromParcel(Parcel in) {
        // nothing
    }

    protected SimpleTask() {}

    public static final TaskCreator<SimpleTask> CREATOR = new TaskCreator<>(SimpleTask.class);

    protected static class TaskCreator<T extends SimpleTask> implements Parcelable.Creator<T> {

        Class<T> clazz;

        TaskCreator(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T createFromParcel(Parcel in) {
            T instance;
            try {
                instance = clazz.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Uh oh");
            }

            instance.readFromParcel(in);
            return instance;
        }

        @Override
        public T[] newArray(int size) {
            @SuppressWarnings("unchecked")
            T[] array = (T[]) Array.newInstance(clazz, size);
            return array;
        }

    }

}
