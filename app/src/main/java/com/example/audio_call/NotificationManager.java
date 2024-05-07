package com.example.audio_call;

import static android.content.Context.MODE_PRIVATE;
import static com.example.audio_call.utils.AudioCallConstants.IP_ADDRESS;
import static com.example.audio_call.utils.AudioCallConstants.PORT_NUMBER;
import static java.nio.ByteOrder.BIG_ENDIAN;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

public class NotificationManager extends BroadcastReceiver {

    Socket socket = null;


    @Override
    public void onReceive(Context context, Intent intent) {

        int audioPort = intent.getIntExtra("audioPort", 0);
        int ackPort = intent.getIntExtra("ackPort", 0);
        int rrPort = intent.getIntExtra("rrPort", 0);
        String phoneNumber = intent.getStringExtra("phone_number");

        Log.d("NotificationManager", "Audio Port = "+ audioPort +
                " Ack Port = "+ackPort + "RR Port = "+ rrPort);

        String str = String.join(" ", "Accepted", phoneNumber);
        byte[] request_bytes = str.getBytes();
        byte[] len = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(str.length()).array();
        byte[] data = new byte[4+request_bytes.length];
        System.arraycopy(len, 0, data, 0, 4);
        System.arraycopy(request_bytes, 0, data, 4, request_bytes.length);


        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket(IP_ADDRESS, PORT_NUMBER);
                    send(data);

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        });
        thread.start();

        Intent callIntent = new Intent(context, Call.class);
        callIntent.putExtra("audioPort", audioPort);
        callIntent.putExtra("ackPort", ackPort);
        callIntent.putExtra("rrPort", rrPort);
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(callIntent);
    }
    DataOutputStream dataOutputStreamInstance = null;
    public void send(byte[] data) {
        Log.d("CreateRoom", "Sending data...");
        try {
            dataOutputStreamInstance = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            Log.e("CreateRoom", "Error creating DataOutputStream: " + e.getMessage());
            throw new RuntimeException(e);
        }
        try {
            dataOutputStreamInstance.write(data);
            dataOutputStreamInstance.flush();
            Log.d("CreateRoom", "Data sent successfully.");
        } catch (IOException e) {
            Log.e("CreateRoom", "Error sending data: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
