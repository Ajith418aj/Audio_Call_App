package com.example.audio_call;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.OneTimeWorkRequest;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.nio.ByteBuffer;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFirebaseMessagingService";
    private static final String CHANNEL_ID = "my_channel_id";
    private static final int NOTIFICATION_ID = 1;

    @SuppressLint("LongLogTag")
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        System.out.print("onMessageReceived function is being called");
        Log.d(TAG, "From: " + remoteMessage.getFrom());
         preferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());

//------------ This is w.r.t to calling feature-------------------------------
           // SharedPreferences preferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//------------ This is w.r.t to calling feature-------------------------------

            // Using a Handler to run the service-related code on a separate thread
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (!foregroundServiceRunning()) {
                        // Run on a separate thread
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "foregroundServiceRunning");
                                String encodedPortsInfo = remoteMessage.getData().get("PortsInfo");
                                phone_number = remoteMessage.getData().get("room_name");
                                byte[] ports = new byte[1024];

                                ports = Base64.decode(encodedPortsInfo, Base64.DEFAULT);

                                byte[] portRRBytes = new byte[4];
                                System.arraycopy(ports, 0, portRRBytes, 0, 4);
                                audioPort = ByteBuffer.wrap(portRRBytes).getInt();

                                byte[] audioPortBytes = new byte[4];
                                System.arraycopy(ports, 4, audioPortBytes, 0, 4);
                                ackPort = ByteBuffer.wrap(audioPortBytes).getInt();

                                byte[] ackPortBytes = new byte[4];
                                System.arraycopy(ports, 8, ackPortBytes, 0, 4);
                                rrPort = ByteBuffer.wrap(ackPortBytes).getInt();

                                Log.d("Received Ports", "Audio Port = "+ audioPort +
                                        " Ack Port = "+ackPort + "RR Port = "+ rrPort);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putInt("audioPort", audioPort);
                                editor.putInt("ackPort", ackPort);
                                editor.putInt("rrPort", rrPort);
                                editor.putString("phone_number", phone_number);

                                //editor.putString("room_name", roomName);
                                editor.apply();
                                startForegroundService(preferences);
                            }
                        }).start();
                    }

                    handleDataPayload(remoteMessage.getData());

                    // Display the notification to the user on the main thread
//                    showNotification(remoteMessage.getNotification().getTitle(), remoteMessage.getNotification().getBody());
                }
            });
        }
    }

    private int audioPort;

    private int ackPort;

    private int rrPort;
    private String phone_number;
    private SharedPreferences preferences;
    private void handleDataPayload(Map<String, String> data) {
        // Extract data from the payload and take appropriate actions
        String title = data.get("title");
        String encodedPortsInfo = data.get("PortsInfo");
        phone_number = data.get("room_name");
        byte[] decodedPortsInfo = Base64.decode(encodedPortsInfo, Base64.DEFAULT);
        byte[] ports = new byte[1024];

        ports = Base64.decode(encodedPortsInfo, Base64.DEFAULT);

        byte[] portRRBytes = new byte[4];
        System.arraycopy(ports, 0, portRRBytes, 0, 4);
        audioPort = ByteBuffer.wrap(portRRBytes).getInt();

        byte[] audioPortBytes = new byte[4];
        System.arraycopy(ports, 4, audioPortBytes, 0, 4);
        ackPort = ByteBuffer.wrap(audioPortBytes).getInt();

        byte[] ackPortBytes = new byte[4];
        System.arraycopy(ports, 8, ackPortBytes, 0, 4);
        rrPort = ByteBuffer.wrap(ackPortBytes).getInt();


        Log.d("Received Ports", "Audio Port = "+ audioPort +
                " Ack Port = "+ackPort + "RR Port = "+ rrPort);

        Log.d("Body", "Ports from Control Server: " + audioPort);
        Log.d("Body", "Ports from Control Server: " + decodedPortsInfo.toString());

        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("audioPort", audioPort);
        editor.putInt("ackPort", ackPort);
        editor.putInt("rrPort", rrPort);
        editor.putString("phone_number", phone_number);

        //editor.putString("room_name", roomName);
        editor.apply();
        // Display notification or perform other actions based on the data payload
        showNotification(title, encodedPortsInfo);
    }



    @SuppressLint("LongLogTag")
    @Override
    // This function is called when the token is refreshed
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);
        // You may need to send the new token to your server for further processing.
        // For example, you can send it to your backend server.
    }

    @SuppressLint("LongLogTag")
    private void showNotification(String title, String body) {

//------------ This is w.r.t to calling feature-------------------------------
//        SharedPreferences preferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
//        int audioPort = preferences.getInt("audioPort", -1);
//        int ackPort = preferences.getInt("ackPort", -1);
//        int rrPort = preferences.getInt("rrPort", -1);
//
//        Log.d("AudioPort", "Value: " + audioPort);
//        Log.d("AckPort", "Value: " + ackPort);
//        Log.d("RrPort", "Value: " + rrPort);
//
//        if (audioPort != -1 && ackPort != -1 && rrPort != -1) {
//            // Create an explicit intent for the Call activity
//            Intent callIntent = new Intent(this, Call.class);
//            callIntent.putExtra("audioPort", audioPort);
//            callIntent.putExtra("ackPort", ackPort);
//            callIntent.putExtra("rrPort", rrPort);
//
//            // Use SharedPreferences to store the FCM token persistently
//            String fcmTokenDuringCreate = preferences.getString("fcmToken", null);
//            callIntent.putExtra("fcmToken", fcmTokenDuringCreate);
//            //startActivity(intent);
//            //startActivityForResult(callIntent, 1001);
//
//            // Create a unique requestCode to differentiate between different PendingIntents
//            int requestCode = (int) System.currentTimeMillis();
//
//            PendingIntent callPendingIntent = PendingIntent.getActivity(this, requestCode, callIntent, 0);
//
//            createNotificationChannel();
//
//            // Build the notification
//            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
//                    .setSmallIcon(R.drawable.ic_notification)
//                    .setContentTitle(title)
//                    .setContentText(body)
//                    .setContentIntent(callPendingIntent)  // Use callPendingIntent instead of pendingIntent
//                    .addAction(R.drawable.ic_notification, "Play", getPlayPendingIntent());
//        }else {
//            Log.e(TAG, "Invalid port values");
//        }
//------------ This is w.r.t to calling feature-------------------------------



        // Create the notification channel
        createNotificationChannel();

        // Create an explicit intent for an activity in your app
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_notification, "Play", getPlayPendingIntent());

        // Start the foreground service
//        startForegroundService();
    }

    private PendingIntent getPlayPendingIntent() {
        Log.e("MyFirebaseMessagingService", "getPlayPendingIntent method called");
        Intent playIntent = new Intent(this, RingingService.class);
        playIntent.setAction(RingingService.ACTION_PLAY);
        return PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    // Start the foreground service
    private void startForegroundService(SharedPreferences preferences) {
        Intent serviceIntent = new Intent(this, RingingService.class);
        serviceIntent.putExtra("ROOM_NAME", preferences.getString("roomName", ""));
        startService(serviceIntent);
//        Intent serviceIntent = new Intent ( this, RingingService.class );
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder ( RingingService.class ).addTag ( "BACKUP_WORKER_TAG" ).build ();
//            WorkManager.getInstance ( this ).enqueue ( request );
//        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            this.startForegroundService ( serviceIntent );
//        } else {
//            this.startService ( serviceIntent );
//        }
    }

    // Create the notification channel for devices running Android 8.0 (Oreo) and higher
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "My Channel", importance);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public boolean foregroundServiceRunning(){
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for(ActivityManager.RunningServiceInfo service: activityManager.getRunningServices(Integer.MAX_VALUE)){
            if(RingingService.class.getName().equals(service.service.getClassName())){
                return true;
            }
        }
        return false;
    }

}
