package com.example.audio_call;

import static com.example.audio_call.utils.AudioCallConstants.IP_ADDRESS;
import static com.example.audio_call.utils.AudioCallConstants.PORT_NUMBER;
import static java.nio.ByteOrder.BIG_ENDIAN;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.firebase.messaging.FirebaseMessaging;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

public class CallWaiting extends AppCompatActivity {

    private int audioPort;

    private int ackPort;

    private int rrPort;

    TextView callename;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_waiting);
        Log.d("CallWaiting", "CallWaiting Class called");

        callename = findViewById(R.id.caller_name);

        String phoneNumber = getIntent().getStringExtra("phoneNumber");
        String username = getIntent().getStringExtra("userName");
        callename.setText(username + "...");
        getFCMtoken(phoneNumber);

    }
    Socket socket = null;
    final String[] fcmTokenHolder = {null};
    void getFCMtoken(String phoneNumber) {
        Log.d("FCMToken", "Token: " + "Waiting for FCM token");
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if(task.isSuccessful() && task.getResult() != null) {
                fcmTokenHolder[0] = task.getResult();

                Log.d("Phone Number", "Phone Number: " + phoneNumber);
                String str = String.join(" ", "Fetch", phoneNumber);
                byte[] request_bytes = str.getBytes();
                byte[] len = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(str.length()).array();
                byte[] data = new byte[4+request_bytes.length];
                System.arraycopy(len, 0, data, 0, 4);
                System.arraycopy(request_bytes, 0, data, 4, request_bytes.length);

                byte[] tokenBytes = fcmTokenHolder[0].getBytes();
                byte[] delimiter = "|".getBytes();
                byte[] combinedData = new byte[len.length + request_bytes.length + delimiter.length + tokenBytes.length];
                System.arraycopy(data, 0, combinedData, 0, data.length);
                System.arraycopy(delimiter, 0, combinedData, data.length, delimiter.length);
                System.arraycopy(tokenBytes, 0, combinedData, data.length + delimiter.length, tokenBytes.length);


                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            socket = new Socket(IP_ADDRESS, PORT_NUMBER);
                            send(combinedData);

                            DataInputStream dataInputStreamInstance = new DataInputStream(socket.getInputStream());

                            byte[] ports = new byte[1024];

                            dataInputStreamInstance.read(ports);

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

                            Intent intent = new Intent(getApplicationContext(), Call.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra("audioPort", audioPort);
                            intent.putExtra("ackPort", ackPort);
                            intent.putExtra("rrPort", rrPort);
                            getApplicationContext().startActivity(intent);

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                    }
                });
                thread.start();
            }
        });
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