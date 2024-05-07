package com.example.audio_call;

import static com.example.audio_call.utils.AudioCallConstants.IP_ADDRESS;
import static com.example.audio_call.utils.AudioCallConstants.PORT_NUMBER;
import static java.nio.ByteOrder.BIG_ENDIAN;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.audio_call.utils.AndroidUtil;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class LoginOtpActivity extends AppCompatActivity {

    String phoneNumber;
    Long timeoutSeconds = 60L;
    String verificationCode;
    PhoneAuthProvider.ForceResendingToken resendingToken;

    EditText otpInput;
    Button nextBtn;
    ProgressBar progressBar;
    TextView resendOtpTextView;
    FirebaseAuth mAuth = FirebaseAuth.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_otp);

        otpInput = findViewById(R.id.login_otp);
        nextBtn = findViewById(R.id.login_next_btn);
        progressBar = findViewById(R.id.login_progress_bar);
        resendOtpTextView = findViewById(R.id.resend_otp_textview);

        phoneNumber = getIntent().getExtras().getString("phone");

        sendOtp(phoneNumber, false);

        nextBtn.setOnClickListener(v -> {
            String enteredOtp = otpInput.getText().toString();
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationCode, enteredOtp);
            signIn(credential);
        });

        resendOtpTextView.setOnClickListener((v) -> {
            sendOtp(phoneNumber, true);
        });

    }

    void sendOtp(String phoneNumber, boolean isResend) {
        Log.d("OTP", "Send OTP method called ");
        startResendTimer();
        setInProgress(true);
        Log.d("OTP1", "Send OTP method called ");
        PhoneAuthOptions.Builder builder = PhoneAuthOptions.newBuilder(mAuth).setPhoneNumber(phoneNumber).setTimeout(timeoutSeconds, TimeUnit.SECONDS).setActivity(this).setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential phoneAuthCredential) {
                Log.d("OTP2", "Send OTP method called ");
                signIn(phoneAuthCredential);
                setInProgress(false);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                Log.d("OTP3", "Send OTP method called ");
                AndroidUtil.showToast(getApplicationContext(), "OTP verification failed");
                setInProgress(false);
            }

            @Override
            public void onCodeSent(@NonNull String s, @NonNull PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                Log.d("OTP4", "Send OTP method called ");
                super.onCodeSent(s, forceResendingToken);
                verificationCode = s;
                resendingToken = forceResendingToken;
                AndroidUtil.showToast(getApplicationContext(), "OTP sent successfully");
                setInProgress(false);
            }
        });
        if (isResend) {
            PhoneAuthProvider.verifyPhoneNumber(builder.setForceResendingToken(resendingToken).build());
        } else {
            PhoneAuthProvider.verifyPhoneNumber(builder.build());
        }

    }

    void setInProgress(boolean inProgress) {
        if (inProgress) {
            progressBar.setVisibility(View.VISIBLE);
            nextBtn.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
            nextBtn.setVisibility(View.VISIBLE);
        }
    }

    void signIn(PhoneAuthCredential phoneAuthCredential) {
        Log.d("DB", "Store phone number in DB");
        //login and go to next activity
        //Store phone number in Firestore Database
        setInProgress(true);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference usersRef = db.collection("users");

        usersRef.whereEqualTo("phone", phoneNumber).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                QuerySnapshot querySnapshot = task.getResult();
                if (querySnapshot != null && !querySnapshot.isEmpty()) {
                    // Phone number already exists in Firestore
                    //signInUser(phoneAuthCredential);
                } else {
                    // Phone number doesn't exist in Firestore, insert it
                    Map<String, Object> user = new HashMap<>();
                    user.put("phone", phoneNumber);

                    // Insert the phone number into Firestore
                    usersRef.document().set(user).addOnSuccessListener(aVoid -> {
                        // Phone number inserted successfully, sign in the user
                        //signInUser(phoneAuthCredential);
                    }).addOnFailureListener(e -> {
                        // Failed to insert phone number
                        setInProgress(false);
                        AndroidUtil.showToast(getApplicationContext(), "Failed to insert phone number");
                    });
                }
            } else {
                // Error getting documents
                setInProgress(false);
                AndroidUtil.showToast(getApplicationContext(), "Error getting user documents");
            }
        });


        mAuth.signInWithCredential(phoneAuthCredential).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                setInProgress(false);
                if (task.isSuccessful()) {
                    //get fcm token
                    getFCMtoken(phoneNumber);
                    Intent intent = new Intent(LoginOtpActivity.this, MainActivity.class);
                    intent.putExtra("phone", phoneNumber);
                    startActivity(intent);
                } else {
                    AndroidUtil.showToast(getApplicationContext(), "OTP verification failed");
                }
            }
        });

    }

    void startResendTimer() {
        resendOtpTextView.setEnabled(false);
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                timeoutSeconds--;
                resendOtpTextView.setText("Resend OTP in " + timeoutSeconds + " seconds");
                if (timeoutSeconds <= 0) {
                    timeoutSeconds = 60L;
                    timer.cancel();
                    runOnUiThread(() -> {
                        resendOtpTextView.setEnabled(true);
                    });
                }
            }
        }, 0, 1000);
    }

    final String[] fcmTokenHolder = {null};
    //byte[] data = new byte[0];
    Socket socket = null;
    void getFCMtoken(String phoneNumber) {
        Log.d("FCMToken", "Token: " + "Waiting for FCM token");
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if(task.isSuccessful() && task.getResult() != null) {
                fcmTokenHolder[0] = task.getResult();
                
                String roomPass = CreateRandomPassword();
                String str = String.join(" ", "Store", phoneNumber, roomPass);
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
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        send(combinedData);

                    }

                });
                thread.start();

                // Wait for the thread to complete
            try {
                thread.join();
                // Thread has completed, continue with further actions here
                // You can put your code that needs to execute after the thread completion here.
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

    } });
    }


    private String CreateRandomPassword() {
        // Define the characters that can be used in the password (only digits)
        String allowedChars = "0123456789";

        // Create an instance of Random
        Random random = new Random();

        // StringBuilder to store the password
        StringBuilder password = new StringBuilder();

        // Generate 8 random digits
        for (int i = 0; i < 8; i++) {
            // Get a random index within the range of allowedChars length
            int randomIndex = random.nextInt(allowedChars.length());

            // Append the digit at the random index to the password
            password.append(allowedChars.charAt(randomIndex));
        }

        // Convert StringBuilder to String and return the password
        return password.toString();
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













