package fr.gaulupeau.apps.Poche.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import fr.gaulupeau.apps.Poche.data.OperationsHelper;

public class TaskService extends Service {

    public static final String ACTION_SIMPLE_TASK = "action_simple_task";

    public interface Task {
        void run(Context context);
    }

    public class TaskServiceBinder extends Binder {
        public void enqueueTask(Task task) {
            TaskService.this.enqueueTask(task, true);
        }
    }

    private static final String TAG = TaskService.class.getSimpleName();

    /**
     * The time to wait for more tasks before stopping the service (in milliseconds).
     * The setting is not precise.
     */
    private static final int WAIT_TIME = 1000;

    private Thread taskThread;

    private final Object startIdLock = new Object();
    private volatile int lastStartId;

    private BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();

    public static Intent newStartIntent(Context context) {
        return new Intent(context, TaskService.class);
    }

    public static Intent newSimpleTaskIntent(Context context) {
        Intent intent = newStartIntent(context);
        intent.setAction(ACTION_SIMPLE_TASK);
        return intent;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");

        taskThread = new Thread(this::run, "TaskService-taskThread");
        taskThread.start();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");

        if (taskThread != null) {
            taskThread.interrupt();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");

        if (ACTION_SIMPLE_TASK.equals(intent.getAction())) {
            Task task = taskFromActionRequest(ActionRequest.fromIntent(intent));
            if (task != null) {
                enqueueTask(task, false);
            }
        }

        synchronized (startIdLock) {
            lastStartId = startId;
        }

        return START_NOT_STICKY;
    }

    private Task taskFromActionRequest(ActionRequest request) {
        if (request == null) {
            Log.d(TAG, "taskFromActionRequest() request is null");
            return null;
        }

        switch (request.getAction()) {
            case SET_ARTICLE_PROGRESS:
                return c -> OperationsHelper.setArticleProgressBG(
                        c, request.getArticleID(), Float.parseFloat(request.getExtra()));

            default:
                Log.e(TAG, "Unknown action requested: " + request.getAction());
                break;
        }

        return null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");

        return new TaskServiceBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind()");
    }

    private void run() {
        while (true) {
            Task task;
            try {
                task = taskQueue.poll(WAIT_TIME, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.d(TAG, "run() interrupted");
                break;
            }

            if (task != null) {
                try {
                    Log.v(TAG, "run() running a task");
                    task.run(this);
                    Log.v(TAG, "run() finished a task");
                } catch (Exception e) {
                    Log.e(TAG, "run() exception during task execution", e);
                }
            }

            synchronized (startIdLock) {
                if (taskQueue.isEmpty()) {
                    Log.d(TAG, "run() no more tasks; notifying that we are ready to stop");
                    readyToStop();
                }
            }
        }
    }

    private void ensureStarted() {
        Log.d(TAG, "ensureStarted()");

        startService(newStartIntent(this));
    }

    private void readyToStop() {
        Log.d(TAG, "readyToStop()");

        if (!stopSelfResult(lastStartId)) {
            Log.d(TAG, "readyToStop() startId didn't match");
        }
    }

    private void enqueueTask(Task task, boolean ensureStarted) {
        Log.d(TAG, "enqueueTask()");
        Objects.requireNonNull(task, "task is null");

        Log.v(TAG, "enqueueTask() enqueueing task");
        taskQueue.add(task);

        if (ensureStarted) {
            Log.v(TAG, "enqueueTask() starting service");
            ensureStarted();
            Log.v(TAG, "enqueueTask() started service");
        }
    }

}
