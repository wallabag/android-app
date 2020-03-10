package fr.gaulupeau.apps.Poche.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.core.util.Consumer;

import fr.gaulupeau.apps.Poche.service.tasks.ActionRequestTask;
import fr.gaulupeau.apps.Poche.service.tasks.DownloadArticleAsFileTask;
import fr.gaulupeau.apps.Poche.service.tasks.FetchArticleImagesTask;
import fr.gaulupeau.apps.Poche.service.tasks.SimpleTask;
import fr.gaulupeau.apps.Poche.service.tasks.SweepDeletedArticlesTask;
import fr.gaulupeau.apps.Poche.service.tasks.SyncOfflineChangesTask;
import fr.gaulupeau.apps.Poche.service.tasks.UpdateArticlesTask;

public class ServiceHelper {

    private static final String TAG = ServiceHelper.class.getSimpleName();

    public static void startService(Context context, ActionRequest request) {
        ActionRequestTask task;
        boolean mainService = false;

        switch (request.getAction()) {
            case SYNC_QUEUE:
                task = new SyncOfflineChangesTask(request);
                mainService = true;
                break;

            case UPDATE_ARTICLES:
                task = new UpdateArticlesTask(request);
                mainService = true;
                break;

            case SWEEP_DELETED_ARTICLES:
                task = new SweepDeletedArticlesTask(request);
                mainService = true;
                break;

            case FETCH_IMAGES:
                task = new FetchArticleImagesTask(request);
                break;

            case DOWNLOAD_AS_FILE:
                task = new DownloadArticleAsFileTask(request);
                break;

            default:
                throw new RuntimeException("Action is not implemented: " + request.getAction());
        }

        enqueueSimpleServiceTask(context, mainService ? MainService.class : SecondaryService.class, task);
    }

    public static void enqueueSimpleServiceTask(Context context, SimpleTask task) {
        enqueueSimpleServiceTask(context, MainService.class, task);
    }

    public static void enqueueSimpleServiceTask(Context context,
                                                Class<? extends TaskService> serviceClass,
                                                SimpleTask task) {
        context.startService(TaskService.newSimpleTaskIntent(context, serviceClass, task));
    }

    public static void enqueueServiceTask(Context context, TaskService.Task task,
                                          Runnable postCallCallback) {
        enqueueServiceTask(context, MainService.class, task, postCallCallback);
    }

    public static void enqueueServiceTask(Context context,
                                          Class<? extends TaskService> serviceClass,
                                          TaskService.Task task,
                                          Runnable postCallCallback) {
        performBoundServiceCall(context, serviceClass, binder -> {
            TaskService.TaskServiceBinder service = (TaskService.TaskServiceBinder) binder;
            service.enqueueTask(task);
        }, postCallCallback);
    }

    public static void performBoundServiceCall(Context context,
                                               Class<? extends TaskService> serviceClass,
                                               Consumer<IBinder> action,
                                               Runnable postCallCallback) {
        Log.d(TAG, "performBoundServiceCall() started");

        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected() name=" + name);

                try {
                    Log.v(TAG, "onServiceConnected() executing action");
                    action.accept(service);
                    Log.v(TAG, "onServiceConnected() finished executing action");
                } catch (Exception e) {
                    Log.w(TAG, "onServiceConnected() exception", e);
                    throw e; // ignore?
                } finally {
                    Log.v(TAG, "onServiceConnected() unbinding service");
                    context.unbindService(this);
                    Log.v(TAG, "onServiceConnected() posting postCallCallback");
                    if (postCallCallback != null) {
                        new Handler(context.getMainLooper()).post(postCallCallback);
                    }
                }
                Log.v(TAG, "onServiceConnected() finished");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected() name=" + name);
            }
        };

        Log.d(TAG, "performBoundServiceCall() binding service");
        Intent serviceIntent = new Intent(context, serviceClass);
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        Log.d(TAG, "performBoundServiceCall() finished");
    }

}
