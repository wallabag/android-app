package fr.gaulupeau.apps.Poche.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import fr.gaulupeau.apps.Poche.service.tasks.SimpleTask;

@SuppressLint("Registered") // subclassed
public abstract class TaskService extends Service {

    public static final String ACTION_SIMPLE_TASK = "action_simple_task";

    public static final String PARAM_FOREGROUND = "param_foreground";

    public class TaskServiceBinder extends Binder {
        public void enqueue(ParameterizedRunnable parameterizedRunnable) {
            TaskService.this.enqueueTask(parameterizedRunnable, true);
        }
    }

    /**
     * The time to wait for more tasks before stopping the service (in milliseconds).
     * The setting is not precise.
     */
    private static final int WAIT_TIME = 1000;

    private final String tag;

    private Thread taskThread;

    private final Object startIdLock = new Object();
    private volatile int lastStartId;

    private final BlockingQueue<ParameterizedRunnable> taskQueue = new LinkedBlockingQueue<>();

    private volatile boolean running;

    public static Intent newStartIntent(Context context,
                                        Class<? extends TaskService> serviceClass) {
        return new Intent(context, serviceClass);
    }

    public static Intent newSimpleTaskIntent(Context context,
                                             Class<? extends TaskService> serviceClass,
                                             SimpleTask task) {
        Intent intent = newStartIntent(context, serviceClass);
        intent.setAction(ACTION_SIMPLE_TASK);
        intent.putExtra(SimpleTask.SIMPLE_TASK, task);
        return intent;
    }

    public TaskService(String name) {
        this.tag = name;
    }

    protected int getThreadPriority() {
        return Process.THREAD_PRIORITY_BACKGROUND;
    }

    @Override
    public void onCreate() {
        Log.d(tag, "onCreate()");

        running = true;

        taskThread = new Thread(this::run, tag + "-taskThread");
        taskThread.start();
    }

    @Override
    public void onDestroy() {
        Log.d(tag, "onDestroy()");

        running = false;

        if (taskThread != null) {
            taskThread.interrupt();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(tag, "onStartCommand()");

        if (intent.getBooleanExtra(PARAM_FOREGROUND, false)) {
            setForeground(true);
        }

        if (ACTION_SIMPLE_TASK.equals(intent.getAction())) {
            ParameterizedRunnable task = taskFromSimpleTask(SimpleTask.fromIntent(intent));
            if (task != null) {
                enqueueTask(task, false);
            }
        }

        synchronized (startIdLock) {
            lastStartId = startId;
        }

        return START_NOT_STICKY;
    }

    private ParameterizedRunnable taskFromSimpleTask(SimpleTask simpleTask) {
        if (simpleTask == null) {
            Log.d(tag, "taskFromActionRequest() request is null");
            return null;
        }

        return simpleTask::run;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(tag, "onBind()");

        return new TaskServiceBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(tag, "onUnbind()");

        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(tag, "onRebind()");
    }

    private void run() {
        Process.setThreadPriority(getThreadPriority());

        while (running) {
            ParameterizedRunnable task;
            try {
                task = taskQueue.poll(WAIT_TIME, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.d(tag, "run() poll interrupted");
                break;
            }

            if (task != null) {
                try {
                    Log.v(tag, "run() running a task");
                    task.run(this);
                    Log.v(tag, "run() finished a task");
                } catch (Exception e) {
                    Log.e(tag, "run() exception during task execution", e);
                }
            }

            synchronized (startIdLock) {
                if (taskQueue.isEmpty()) {
                    Log.d(tag, "run() no more tasks; notifying that we are ready to stop");
                    readyToStop();
                }
            }
        }

        if (!taskQueue.isEmpty()) {
            Log.w(tag, "run() stopping, but the queue is not empty");
        }
    }

    private void readyToStop() {
        Log.d(tag, "readyToStop()");

        setForeground(false);

        if (!stopSelfResult(lastStartId)) {
            Log.d(tag, "readyToStop() startId didn't match");
        }
    }

    private void enqueueTask(ParameterizedRunnable task, boolean ensureStarted) {
        Log.d(tag, "enqueueTask()");
        Objects.requireNonNull(task);

        Log.v(tag, "enqueueTask() enqueueing task");
        taskQueue.add(task);

        if (ensureStarted) {
            Log.v(tag, "enqueueTask() starting service");
            ensureStarted();
            Log.v(tag, "enqueueTask() started service");
        }
    }

    private void ensureStarted() {
        Log.d(tag, "ensureStarted()");

        startService(newStartIntent(this, getClass()));
    }

    private void setForeground(boolean foreground) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return; // normal services are good enough before Oreo
        }

        if (foreground) {
            startForeground(getForegroundNotificationId(), getForegroundNotification());
        } else {
            stopForeground(true);
        }
    }

    protected abstract int getForegroundNotificationId();

    protected abstract Notification getForegroundNotification();

}
