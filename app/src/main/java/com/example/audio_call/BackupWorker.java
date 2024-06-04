package com.example.audio_call;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class BackupWorker extends Worker {

    private static final String TAG = "BackupWorker";

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.e("BackupWorker", "doWork");
        Context context = getApplicationContext();
        Intent serviceIntent = new Intent(context, RingingService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.e("BackupWorker", "doWork2");
            context.startForegroundService(serviceIntent);
        } else {
            Log.e("BackupWorker", "doWork3");
            context.startService(serviceIntent);
        }
        return Result.success();
    }
}
