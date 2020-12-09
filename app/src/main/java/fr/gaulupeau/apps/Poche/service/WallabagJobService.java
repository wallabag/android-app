package fr.gaulupeau.apps.Poche.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import fr.gaulupeau.apps.Poche.events.ConnectivityChangedEvent;
import fr.gaulupeau.apps.Poche.events.EventHelper;

public class WallabagJobService extends JobService {

    private static final int CONNECTIVITY_CHANGE_JOB_ID = 1;

    private static final String TAG = WallabagJobService.class.getSimpleName();

    public static void enable(Context context, boolean enable) {
        Log.d(TAG, String.format("enable(%s) started", enable));

        JobScheduler scheduler =
                (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if(enable) {
            boolean alreadyScheduled = false;
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                JobInfo job = scheduler.getPendingJob(CONNECTIVITY_CHANGE_JOB_ID);
                if(job != null) alreadyScheduled = true;
            } else {
                for(JobInfo jobInfo: scheduler.getAllPendingJobs()) {
                    if(jobInfo.getId() == CONNECTIVITY_CHANGE_JOB_ID) {
                        alreadyScheduled = true;
                        break;
                    }
                }
            }

            if(alreadyScheduled) {
                Log.d(TAG, "enable() the job is already scheduled");
                return;
            }

            ComponentName serviceName = new ComponentName(context, WallabagJobService.class);
            JobInfo jobInfo = new JobInfo.Builder(CONNECTIVITY_CHANGE_JOB_ID, serviceName)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build();

            Log.d(TAG, "enable() trying to schedule a job");
            int result = scheduler.schedule(jobInfo);
            if(result == JobScheduler.RESULT_FAILURE) {
                Log.e(TAG, "enable(): Couldn't schedule JobScheduler job");
            } else {
                Log.d(TAG, "enable(): JobScheduler job scheduled");
            }
        } else {
            Log.d(TAG, "enable() trying to cancel a job");
            scheduler.cancel(CONNECTIVITY_CHANGE_JOB_ID);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob() started");

        EventHelper.postEvent(new ConnectivityChangedEvent());

        // not sure about it
        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

}
