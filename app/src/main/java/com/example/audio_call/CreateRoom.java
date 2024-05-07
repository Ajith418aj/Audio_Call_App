package com.example.audio_call;

import static com.example.audio_call.utils.AudioCallConstants.IP_ADDRESS;
import static com.example.audio_call.utils.AudioCallConstants.PORT_NUMBER;
import static java.nio.ByteOrder.BIG_ENDIAN;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;


public class CreateRoom extends AppCompatActivity implements View.OnClickListener{

    private EditText room_id;
    private EditText password;

    private String button_clicked;

    private int audioPort;

    private int ackPort;

    private int rrPort;
    private String fcmTokenDuringCreate = null; // Add this line
    private SharedPreferences preferences; // this is used to store the fcmToken persistently, so that the host when it joins it's own
    // cont... meeting room, it is able to send the token (obtained when creating the room) to the control server when it joins

    //cont... for example when a user joins the room "sam" as roomName and roomPass, the user who hosted that room will receive a call


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room);
        FirebaseApp.initializeApp(this);
        // Initialize SharedPreferences
        preferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);

        room_id = findViewById(R.id.room_id);
        password = findViewById(R.id.password);
        button_clicked = getIntent().getStringExtra("ButtonClicked");

        Button clicked_button;
        if(button_clicked.equals("C")) {
            clicked_button = findViewById(R.id.create_btn);
            clicked_button.setText("Create");
            clicked_button.setTag("Create");
            clicked_button.setOnClickListener(this);

        } else if (button_clicked.equals("J")) {
            clicked_button = findViewById(R.id.create_btn);
            clicked_button.setText("Join");
            clicked_button.setTag("Join");
            clicked_button.setOnClickListener(this);
        }


    }

    Socket socket = null;
    DataOutputStream dataOutputStreamInstance = null;

    String roomName = null;
    // AsyncTask to perform network operations in the background
    private static class SendDataTask extends AsyncTask<byte[], Void, Void> {
        private DataOutputStream dataOutputStreamInstance;

        SendDataTask(Socket socket) {
            try {
                this.dataOutputStreamInstance = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                Log.e("CreateRoom", "Error creating DataOutputStream: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        @Override
        protected Void doInBackground(byte[]... params) {
            byte[] data = params[0];
            try {
                Log.d("CreateRoom", "Sending data...");
                dataOutputStreamInstance.write(data);
                dataOutputStreamInstance.flush();
                Log.d("CreateRoom", "Data sent successfully.");
            } catch (IOException e) {
                Log.e("CreateRoom", "Error sending data: " + e.getMessage());
            }
            return null;
        }
    }
    public void send(byte[] data) {
        // Create an instance of SendDataTask and execute it
        new SendDataTask(socket).execute(data);
    }

    @Override
    public void onClick(View view) {
        final String[] fcmTokenHolder = {null};
        roomName = room_id.getText().toString();
        String roomPass = password.getText().toString();
        String request = view.getTag().toString();
        Intent intent = new Intent(this, Call.class);


//------------ This is w.r.t to calling feature-------------------------------
        // when the host receives a call, it will tap on the notification and it should lead
        //cont... the host straight to the meeting room
//        if (request.equals("Create") && roomName.contains("_host")) {
//            preferences.edit().putString("roomName", roomName).apply();
//            preferences.edit().putString("password", roomPass).apply();
//        }
//------------ This is w.r.t to calling feature----------------------------------

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    //socket = new Socket("10.129.131.231", 8086);
                    socket = new Socket(IP_ADDRESS, PORT_NUMBER);
                    String str = String.join(" ", request, roomName, roomPass);
                    byte[] request_bytes = str.getBytes();
                    byte[] len = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(str.length()).array();
                    byte[] data = new byte[4+request_bytes.length];
                    System.arraycopy(len, 0, data, 0, 4);
                    System.arraycopy(request_bytes, 0, data, 4, request_bytes.length);
//                    String fcmToken= null;
//                    String fcmToken_Joinee = FirebaseMessaging.getInstance().getToken().getResult();

//--------------------------------retrieving and sending FCM token-------------------------------
//                    if (request.equals("Create") && roomName.equals("sam") && roomPass.equals("sam")) {
                    if (request.equals("Create") && str.contains("_host")) {
                        // Your code block here
                        try {

                            // Retrieve the FCM token if the created room is in the "List"
                            FirebaseMessaging.getInstance().getToken()
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful() && task.getResult() != null) {
//                                            String fcmToken = task.getResult();
                                            fcmTokenHolder[0] = task.getResult();
                                            fcmTokenDuringCreate = fcmTokenHolder[0];
//                                            fcmToken_Joinee = fcmToken;
                                            // Save FCM token to SharedPreferences
                                            preferences.edit().putString("fcmToken", fcmTokenDuringCreate).apply();
                                            // Log the FCM token
                                            Log.d("FCMToken", "Token: " + fcmTokenDuringCreate);
                                            byte[] tokenBytes = fcmTokenHolder[0].getBytes();
                                            byte[] delimiter = "|".getBytes();
                                            byte[] combinedData = new byte[len.length + request_bytes.length + delimiter.length + tokenBytes.length];
                                            System.arraycopy(data, 0, combinedData, 0, data.length);
                                            System.arraycopy(delimiter, 0, combinedData, data.length, delimiter.length);
                                            System.arraycopy(tokenBytes, 0, combinedData, data.length + delimiter.length, tokenBytes.length);
                                            // Convert combinedData to a string using UTF-8 encoding
                                            String combinedDataString = new String(combinedData, StandardCharsets.UTF_8);
                                            Log.d("CombinedData", "Combined Data: " + combinedDataString);

                                            Log.d("CombinedDataLength", "Combined Data Length: " + combinedData.length);

                                            System.out.print("CombinedData in bytes: ");
                                            for (byte b : combinedData) {
                                                System.out.print(b + " ");
                                            }
                                            System.out.println();  // Add a newline for better formatting
                                            send(combinedData);

                                            // Now you can proceed to use the modified 'data' array
                                        } else {
                                            // Handle the error
                                            Exception exception = task.getException();
                                            if (exception != null) {
                                                Log.e("FCM Token Error", exception.getMessage());
                                            }
                                        }
                                    });
                        } catch (Exception e) {
                            Log.e("CreateRoom", "Error in FCM token retrieval: " + e.getMessage());
                        }
                        // }
                        // }
                    }
                    // Check if the FCM token is available during Join request
                    else if (request.equals("Join") && (fcmTokenDuringCreate = preferences.getString("fcmToken", null)) != null){
                        try{


                            // FCM token is available, proceed with your join logic

                            Log.d("FCMToken", "Token before sending join request: " + fcmTokenDuringCreate);

                            // send the token along with the required data to the control server
                            byte[] tokenBytes = fcmTokenDuringCreate.getBytes();
                            byte[] delimiter = "|".getBytes();
                            byte[] combinedData = new byte[len.length + request_bytes.length + delimiter.length + tokenBytes.length];
                            System.arraycopy(data, 0, combinedData, 0, data.length);
                            System.arraycopy(delimiter, 0, combinedData, data.length, delimiter.length);
                            System.arraycopy(tokenBytes, 0, combinedData, data.length + delimiter.length, tokenBytes.length);
                            // Convert combinedData to a string using UTF-8 encoding
                            String combinedDataString = new String(combinedData, StandardCharsets.UTF_8);
                            System.out.print("CombinedData in bytes for Join request: ");
                            for (byte b : combinedData) {
                                System.out.print(b + " ");
                            }
                            System.out.println();  // Add a newline for better formatting
                            send(combinedData);
                        } catch (Exception e) {
                            Log.e("CreateRoom", "Error in FCM token retrieval for Join request: " + e.getMessage());
                        }

                    }



//--------------------------------retrieving and sending FCM token-------------------------------

//                    dataOutputStreamInstance = new DataOutputStream(socket.getOutputStream());
//                    dataOutputStreamInstance.write(data);
//                    dataOutputStreamInstance.flush();
                    else {
                        Log.d("FCMToken", "Token for join request: " + fcmTokenDuringCreate);
                        Log.d("DataLength", "Data Length: " + data.length);
                        String DataString = new String(data, StandardCharsets.UTF_8);

                        Log.d("Data", "Data: " + DataString);
                        System.out.print("data in bytes: ");
                        for (byte b : data) {
                            System.out.print(b + " ");
                        }

                        send(data);
                    }


                    /* Receive TCP, UDP port details from control.py
                        TCP -  for acknowledgement and reception report
                        UDP - for sending audio data.
                     */
                    DataInputStream dataInputStreamInstance = new DataInputStream(socket.getInputStream());

                    byte[] ports = new byte[1024];

                    dataInputStreamInstance.read(ports);

                    byte[] portRRBytes = new byte[4];
                    System.arraycopy(ports, 4, portRRBytes, 0, 4);
                    audioPort = ByteBuffer.wrap(portRRBytes).getInt();

                    byte[] audioPortBytes = new byte[4];
                    System.arraycopy(ports, 8, audioPortBytes, 0, 4);
                    ackPort = ByteBuffer.wrap(audioPortBytes).getInt();

                    byte[] ackPortBytes = new byte[4];
                    System.arraycopy(ports, 12, ackPortBytes, 0, 4);
                    rrPort = ByteBuffer.wrap(ackPortBytes).getInt();

                    Log.d("Received Ports", "Audio Port = "+ audioPort +
                            " Ack Port = "+ackPort + "RR Port = "+ rrPort);


                    intent.putExtra("audioPort", audioPort);
                    intent.putExtra("ackPort", ackPort);
                    intent.putExtra("rrPort", rrPort);
                    intent.putExtra("room_name", roomName);

                    //startActivity(intent);
                    startActivityForResult(intent, 1001);


//----------Store the Ports in a shared preferences so that MyFirebaseMessagingService class can access
// After obtaining dynamic port values, store them in SharedPreferences
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("audioPort", audioPort);
                    editor.putInt("ackPort", ackPort);
                    editor.putInt("rrPort", rrPort);
                    editor.putString("room_name", roomName);
                    editor.apply();
//----------Store the Ports in a shared preferences so that MyFirebaseMessagingService class can access

                }catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

        });
        thread.start();


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data2) {
        super.onActivityResult(requestCode, resultCode, data2);

        if (requestCode == 1001) {
            if (resultCode == RESULT_OK) {
                // This code will execute when the called activity returns successfully
                String str = String.join(" ", "Endcall", roomName);
                byte[] request_bytes = str.getBytes();
                byte[] len = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(str.length()).array();
                byte[] data = new byte[4+request_bytes.length];
                System.arraycopy(len, 0, data, 0, 4);
                System.arraycopy(request_bytes, 0, data, 4, request_bytes.length);



                Thread end = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //                            dataOutputStreamInstance.write(data);
//                            dataOutputStreamInstance.flush();
                        send(data);
                        Log.d("CreateRoom", "Activity Over1");
                    }
                });
                end.start();
                Log.d("CreateRoom", "Activity Over2");
            }
        }
    }



}