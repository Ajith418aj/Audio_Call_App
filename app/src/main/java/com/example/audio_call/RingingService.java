package com.example.audio_call;

// Example in RingingService.java
// ...

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Timer;
import java.util.TimerTask;

public class RingingService extends Service {
    private static final String TAG = "RingingService"; // Add this line
    private MediaPlayer mediaPlayer;
    public static final String ACTION_PLAY = "com.example.audio_call.action.PLAY";
    private Context context;
    private static final int NOTIFICATION_ID = 1; // Add this line
    private static final String CHANNEL_ID = "my_foreground_channel"; // Add this line
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.e("Ringing Service", "Ringing Service called");
        String roomName = intent.getStringExtra("ROOM_NAME");
        String password = intent.getStringExtra("PASSWORD");
        // Display the foreground notification
        startForeground(NOTIFICATION_ID, createNotification());

        // Play the sound
        playRingingSound();
        return START_NOT_STICKY;
    }

    private void playRingingSound() {
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.ringing);
            if (mediaPlayer == null) {
                Log.e(TAG, "MediaPlayer creation failed. Resource not found or invalid?");
                return;
            }

            mediaPlayer.start();

            // Stop the sound after 10 seconds (adjust duration as needed)
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    stopRingingSound();
                }
            }, 10000); // 10000 milliseconds = 10 seconds

        } catch (Exception e) {
            Log.e(TAG, "Error initializing/playing MediaPlayer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopRingingSound() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Stop the foreground service
        stopForeground(true);
        stopSelf();
    }

    private Notification createNotification() {
        Log.d("Ringing Service", "createNotification" );



        // Create an explicit intent for an activity in your app
//------------ This is w.r.t to calling feature-------------------------------
        SharedPreferences preferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        int audioPort = preferences.getInt("audioPort", -1);
        int ackPort = preferences.getInt("ackPort", -1);
        int rrPort = preferences.getInt("rrPort", -1);
        String phone_number = preferences.getString("phone_number", "");
        String roomName = preferences.getString("roomName", null);

        Log.d("AudioPort", "Value: " + audioPort);
        Log.d("AckPort", "Value: " + ackPort);
        Log.d("RrPort", "Value: " + rrPort);

        if (audioPort != -1 && ackPort != -1 && rrPort != -1) {
            // Create an explicit intent for the Call activity
            Intent callIntent = new Intent(context, NotificationManager2.class);
            callIntent.putExtra("audioPort", audioPort);
            callIntent.putExtra("ackPort", ackPort);
            callIntent.putExtra("rrPort", rrPort);
            callIntent.putExtra("roomName", roomName);
            callIntent.putExtra("phone_number", phone_number);

            // Use SharedPreferences to store the FCM token persistently
            String fcmTokenDuringCreate = preferences.getString("fcmToken", null);
            callIntent.putExtra("fcmToken", fcmTokenDuringCreate);
            //startActivity(intent);
            //startActivityForResult(callIntent, 1001);

            // Create a unique requestCode to differentiate between different PendingIntents
            int requestCode = (int) System.currentTimeMillis();

            PendingIntent callPendingIntent = PendingIntent.getActivity(this, requestCode, callIntent, PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Audio_call")
                    .setContentText("Incoming call")
                    .setContentIntent(callPendingIntent);

            // Create the notification channel for devices running Android 8.0 (Oreo) and higher
            createNotificationChannel();

            return builder.build();


        }else {
            Log.e(TAG, "Invalid port values");
        }
//------------ This is w.r.t to calling feature-------------------------------
//        Intent intent = new Intent(this, MainActivity.class);
//
//        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
//
//        // Build the notification
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setSmallIcon(R.drawable.ic_notification)
//                .setContentTitle("Audio_call")
//                .setContentText("Incoming call")
//                .setContentIntent(PendingIntent);
//
//        // Create the notification channel for devices running Android 8.0 (Oreo) and higher
//        createNotificationChannel();
//
//        return builder.build();
        return null; // remove this line if above calling feature code is to be removed
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        // Release resources when the service is stopped
        stopRingingSound();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
