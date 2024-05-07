package com.example.audio_call;

import static com.example.audio_call.utils.AudioCallConstants.IP_ADDRESS;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.audio_call.views.CustomView;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class Call extends AppCompatActivity implements View.OnClickListener {

    private int audioPort;

    private int ackPort;

    private int rrPort;

    private String room_name;

    private ImageView mic;
    private ImageView endCall;
    private ImageView loudspeaker;
    private boolean endCallClicked = false;

    private boolean onMic = false;

    private boolean animation = false;
    Boolean micc = false;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = 882;
    private static int AUD_BUF_SIZE = 1920;
    private CustomView customView;

    private float amplificationFactor = 1.5f;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;

    private Packetizer packetizer;
    Button animationSwitch;
    DatagramSocket audio_socket = null;
    DatagramSocket ack_socket = null;
    DatagramSocket rr_socket = null;
    InetAddress data_forwarder_server = null;
    private boolean loudspeakerClicked = false;
    /////////////////////////////////////////////////////////////////////////////////////////////
    private Context mContext;
    private Packetizer packetizerObject;

    private DataOutputStream dataOutputStreamInstanceRR = null;
    private int received_packets_in_last_interval = 0;
    private int last_packet_sent = 0;
    private int maximum_sequence_number_received = 0;
    private int  reception_report_seq = 0;
    private TextView lastPacketSentView, lastAckReceivedView, lostPacketView, rrTime, delayView;
    //For calculating Loss Percentage
    Timer timerRRSent = new Timer();
    TimerTask sentRRTask = new TimerTask() {
        @Override
        public void run() {
            Log.d("RR Socket", "In Sent RR");
            //byte[] bluff_buff = new byte[AUD_BUF_SIZE]; //changed from AUD_BUF_SIZE
            //byte[] receptionReportPac = packetizerObject.packetize(bluff_buff, 2, last_packet_sent, maximum_sequence_number_received, received_packets_in_last_interval);

            try {
                /*
                byte[] receptionReportPacket = new byte[4 + receptionReportPac.length];
                byte[] len = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(receptionReportPac.length).array();    //recheck this, if not works!

                System.arraycopy(len, 0, receptionReportPacket, 0, 4);
                System.arraycopy(receptionReportPac, 0, receptionReportPacket, 4, receptionReportPac.length);*/


                Log.d("RR Socket", "Before RR sent");
                byte[] receptionReportPacket = packetizerObject.encodePacket(last_packet_sent, maximum_sequence_number_received, received_packets_in_last_interval, reception_report_seq);

                DatagramPacket packet = new DatagramPacket(receptionReportPacket, receptionReportPacket.length, data_forwarder_server, rrPort);
                rr_socket.send(packet);
                Log.d("RR Socket", "After RR Sent");

            } catch (IOException e) {
                e.printStackTrace();
            }
            received_packets_in_last_interval = 0;
        }
    };

    private int rrTimerCounter = 0;
    private Timer timerRR = new Timer();
    TimerTask rrTimer = new TimerTask() {
        @Override
        public void run() {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    rrTime.setText(String.valueOf(rrTimerCounter) + " ms");
                    rrTimerCounter += 100;
                }
            });
        }
    };

    /////////////////////////////////////////////////////////////////////////////////////////////
    public void startAudio(Boolean mic) {
        this.micc = mic;
    }

    public void animationSwitchFunc(Button animationSwitch) {
        if (animation) {
            animationSwitch.setText("Animation ON");
            animation = false;
        } else {
            animationSwitch.setText("Animation OFF");
            animation = true;
        }
    }

    private OutputStream outRR = null;
    private BufferedOutputStream buff_outRR = null;
    private HashMap<Integer, Long> timestampMap = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        //to avoid phone going to sleep during call
        //starVoipAudioCall();

        mic = findViewById(R.id.mic);
        endCall = findViewById(R.id.end_call);
        loudspeaker = findViewById(R.id.loudspeaker);
        ///////////////////////////////////////////////
        lastPacketSentView = findViewById(R.id.lastPacketSent);
        lastAckReceivedView = findViewById(R.id.lastAckReceived);
        lostPacketView = findViewById(R.id.lostPackets2);
        rrTime = findViewById(R.id.rrTimer);
        delayView = findViewById(R.id.delayView);
        debugParameterThread();
        timestampMap = new HashMap<>();
        rttQueue = new ArrayDeque<>();
        ////////////////////////////////////////////////
        customView = ((Activity) this).findViewById(R.id.customView);

        mic.setOnClickListener(this);
        endCall.setOnClickListener(this);
        loudspeaker.setOnClickListener(this);

        audioPort = getIntent().getIntExtra("audioPort", 0);
        ackPort = getIntent().getIntExtra("ackPort", 0);
        rrPort = getIntent().getIntExtra("rrPort", 0);
        room_name = getIntent().getStringExtra("room_name");

        startReceivingData();

        animationSwitch = (Button) findViewById(R.id.animation_switch);
        animationSwitch.setOnClickListener(this);

        ////////////////////////////////////////////////////////////////
        packetizerObject = new Packetizer();
        timerRRSent.schedule(sentRRTask, 4000, 4000);
        timerRR.schedule(rrTimer, 4000, 100);

        showLossTask();
        showRTTTask();
        /////////////////////////////////////////////////////////////////
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            System.out.println("Asking for Request");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    1234);
        } else {
            System.out.println("Request granted");
            // Permission already granted
            // Proceed with your logic
        }
    }

    public void debugParameterThread()
    {
        Thread debugThread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                while(true)
                {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            lastPacketSentView.setText(String.valueOf(last_packet_sent));
                            lastAckReceivedView.setText(String.valueOf(last_ack_received));
                        }
                    });

                    try
                    {
                        TimeUnit.MILLISECONDS.sleep(300);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        });
        debugThread.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        System.out.println("Entered onRequest");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1234) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                System.out.println("Permission granted");
                // Permission granted
                // Proceed with your logic

            } else {
                System.out.println("Permission denied");
                // Permission denied
                // Handle the denial case
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.mic) {
            Log.d("Button", "Mic button pressed");
            ImageView mic_image = findViewById(R.id.mic);
            String tag = (String) mic_image.getTag();
            if (tag.equals("mic_off") && checkPermissions()) {
                startAudio(true);
                sendAudio();
                mic_image.setImageResource(R.drawable.unmute);
                mic_image.setTag("mic_on");
            } else {
                startAudio(false);
                mic_image.setImageResource(R.drawable.mute);
                mic_image.setTag("mic_off");
            }
        } else if (view.getId() == R.id.end_call) {
            Log.d("Button", "End call button pressed");
            endCallClicked = true;
            audio_socket.close();
            ack_socket.close();
            rr_socket.close();
            //to undo the "no sleep" during call
            endVoIPAudioCall();
            Intent resultIntent = new Intent();
            // Set any result data here if needed
            setResult(RESULT_OK, resultIntent);
            finish();
        } else if (view.getId() == R.id.animation_switch) {
            Log.d("Button", "Animation switch pressed");
            animationSwitchFunc(animationSwitch);
        } else if (view.getId() == R.id.loudspeaker) {
            Log.d("Button", "Loudspeaker button pressed");
            loudspeakerClicked = true;
            ImageView loudspeaker_image = findViewById(R.id.loudspeaker);
            String tag = (String) loudspeaker_image.getTag();
            if (tag.equals("loudspeaker_off") && checkPermissions()) {
                //startAudio(true);
                //sendAudio();
                loudspeakerClicked = true;
                loudspeaker_image.setImageResource(R.drawable.loud_speaker_on);
                loudspeaker_image.setTag("loudspeaker_on");
            } else {
                //startAudio(false);
                loudspeakerClicked = false;
                loudspeaker_image.setImageResource(R.drawable.loud_speaker_off);
                loudspeaker_image.setTag("loudspeaker_off");
            }
        }

    }

    private boolean checkPermissions() {
        int permissionRecordAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        int permissionInternet = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET);

        if (permissionRecordAudio != PackageManager.PERMISSION_GRANTED
                || permissionInternet != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET}, 0);
            return false;
        }

        return true;
    }

    public void startReceivingData() {
        Log.d("Receive Audio", "Audio Receiving Started");

        Thread receiveAudioThread = new Thread(this::receiveAudio);
        //Thread receiveAckThread = new Thread(this::receiveAck);
        //Thread receiveRRThread = new Thread(this::receiveRR);

        receiveAudioThread.start();
        //receiveAckThread.start();
        //receiveRRThread.start();
    }

    private void receiveRR() {
        Log.d("RR Thread", "receiveRR Thread Started");
        try {

            while (true) {

                /*byte[] buffRR = new byte[22];

                DatagramPacket lenPacket = new DatagramPacket(buffRR, buffRR.length);
                rr_socket.receive(lenPacket);
                int packetSize = ByteBuffer.wrap(lenPacket.getData(), 0, 4).getInt();*/

                byte[] receptionReport = new byte[BUFFER_SIZE];
                //byte[] receptionReport = new byte[BUFFER_SIZE];
                DatagramPacket rrPacket = new DatagramPacket(receptionReport, receptionReport.length);

                Log.d("RR Thread", "Waiting for RR");
                rr_socket.receive(rrPacket);
                Log.d("RR Thread", "RR packet received");

                // Extract data from DatagramPacket
                byte[] receivedData = rrPacket.getData();

                // Add the modified data to the queue
                receptionReports.add(receivedData);
                rrTimerCounter = 0;
            }
        } catch (IOException e) {
            Log.d("RR Thread", "Exception Occurred in RR Thread");
            e.printStackTrace();
        }
    }


    public void sendAudio() {
        if (micc) {
            Log.d("Mic", "Mic on");
            packetizer = new Packetizer();
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 200);
                    return;
                }
            }
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, 8820);

            byte[] buffer = new byte[BUFFER_SIZE];
            // Generate a random SSRC value
            int ssrc = packetizer.generateRandomSSRC();

            Thread sendAudioThread = new Thread(() -> {

                try {
                    audioRecord.startRecording();
                    //String path = "/data/data/com.example.audio_call/sent_audio.pcm";
                    //FileOutputStream audio_data = new FileOutputStream(path);
                    int sequenceNumber = 0;
                    while (micc) {
                        int bytesRead = audioRecord.read(buffer, 0, BUFFER_SIZE);
                        int packetHeight = (int) packetizer.getPacketHeight(buffer);

                        // Get the current timestamp
                        long timestamp = System.currentTimeMillis();

                        // Create the RTP packet with headers and audio data

                        //byte[] rtpPacket = packetizer.addRtpHeaders(buffer, sequenceNumber, timestamp, ssrc);
                        byte[] rtpPacket = packetizer.addRtpHeaders(buffer, last_packet_sent, timestamp, ssrc);

                        // Send the RTP packet over the network
                        DatagramPacket packet = new DatagramPacket(rtpPacket, rtpPacket.length, data_forwarder_server, audioPort);
                        //DatagramPacket packet = new DatagramPacket(buffer, bytesRead, data_forwarder_server, audioPort);
                        //timestampMap.put(sequenceNumber, System.currentTimeMillis());
                        timestampMap.put(last_packet_sent, System.currentTimeMillis());
                        //audio_data.write(buffer);
                        audio_socket.send(packet);


                        if (animation) {
                            //customView.addPacket(sequenceNumber, packetHeight, buffer);
                            customView.addPacket(last_packet_sent, packetHeight, buffer);
                        }
                        sequenceNumber++;
                        last_packet_sent++;
                    }
                    Log.d("Socket", "Send Socket Closed");
                } catch (SocketException e) {
                    e.printStackTrace();
                    Log.d("Socket", "Socket error while sending");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d("Socket", "Socket error while sending");
                }

            });
            sendAudioThread.start();
        }
    }

    private void receiveAudio() {
        try {
            audio_socket = new DatagramSocket();
            ack_socket = new DatagramSocket();
            rr_socket = new DatagramSocket();
            data_forwarder_server = InetAddress.getByName(IP_ADDRESS);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        String audio_dummy = "audio_dummy";
        String ack_dummy = "ack_dummy";
        String rr_dummy = "rr_dummy";

        DatagramPacket dummy_audio_packet = new DatagramPacket(audio_dummy.getBytes(), audio_dummy.length(), data_forwarder_server, audioPort);
        DatagramPacket dummy_ack_packet = new DatagramPacket(ack_dummy.getBytes(), ack_dummy.length(), data_forwarder_server, ackPort);
        DatagramPacket dummy_rr_packet = new DatagramPacket(rr_dummy.getBytes(), rr_dummy.length(), data_forwarder_server, rrPort);

        try {
            audio_socket.send(dummy_audio_packet);
            ack_socket.send(dummy_ack_packet);
            Thread receiveAckThread = new Thread(this::receiveAck);
            receiveAckThread.start();
            rr_socket.send(dummy_rr_packet);
            Thread receiveRRThread = new Thread(this::receiveRR);
            receiveRRThread.start();
            Log.d("Dummy", "Dummy data sent successfully");
        } catch (IOException e) {
            Log.d("Dummy", "Error while sending dummy data");
            throw new RuntimeException(e);
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, AUD_BUF_SIZE, AudioTrack.MODE_STREAM);
            audioTrack.play();
//            AudioTrack audioTrack = new AudioTrack(AudioManager.MODE_IN_CALL, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
//                    AudioFormat.ENCODING_PCM_16BIT, AUD_BUF_SIZE, AudioTrack.MODE_STREAM);
//            audioTrack.play();
//            AudioTrack loudspeaker_Track = new AudioTrack(AudioManager.MODE_IN_COMMUNICATION, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
//                    AudioFormat.ENCODING_PCM_16BIT, AUD_BUF_SIZE, AudioTrack.MODE_STREAM);
//            AudioTrack loudspeaker_Track = new AudioTrack(AudioManager.MODE_IN_CALL, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
//                    AudioFormat.ENCODING_PCM_16BIT, AUD_BUF_SIZE, AudioTrack.MODE_STREAM);
//            loudspeaker_Track.play();
//            audioManager.setSpeakerphoneOn(false);

            while (!endCallClicked) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    Log.d("Audio Socket", "Waiting for Audio");
                    audio_socket.receive(packet);
                    Log.d("Audio Socket", "Received Audio");

                    int sequenceNumber = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                    Log.d("Debug", "sequenceNumber: " + sequenceNumber); //new
                    maximum_sequence_number_received = Math.max(sequenceNumber, maximum_sequence_number_received);
                    received_packets_in_last_interval++;

                    byte[] audioDataWithHeaders = packet.getData();
                    int audioDataLength = packet.getLength(); // Length of the received data

                    // Remove RTP headers by skipping the first 12 bytes (assuming 12 bytes of RTP header)
                    byte[] audioData = new byte[audioDataLength - 12];
                    System.arraycopy(audioDataWithHeaders, 12, audioData, 0, audioDataLength - 12);

                    for(int i=0; i<audioData.length; i++) {
                        audioData[i] = (byte) (audioData[i] * (byte)amplificationFactor);
                    }
                    // Write the modified audioData to the AudioTrack
                    if (!loudspeakerClicked) {
                        Log.d("Audio", "Playing via microphone");
                        //audioTrack.write(amplifiedAudioData, 0, amplifiedAudioData.length);
                        audioTrack.write(audioData, 0, audioData.length);
                    } else {
                        Log.d("Audio", "Playing via Loudspeaker");
                        //loudspeaker_Track.write(amplifiedAudioData, 0, amplifiedAudioData.length);
                        //loudspeaker_Track.write(audioData, 0, audioData.length);
                    }
                    Log.d("Socket", "Received audio");

                    //Now send back the sequence number received by appending ack as string
                    String combinedData = "1" + sequenceNumber;
                    byte[] sendData = combinedData.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, data_forwarder_server, ackPort);
                    Log.d("ACK", "Sending Ack for received packet");
                    ack_socket.send(sendPacket);
                    Log.d("ACK", "Ack sent successfully");

                } catch (SocketTimeoutException e) {
                    // Timeout occurred, check the endCallClicked flag
                    if (endCallClicked) {
                        break;
                    }
                }

            }
            Log.d("Socket", "Receive Socket Closed");
        } catch (SocketException e) {
            e.printStackTrace();
            Log.d("Socket", "Socket error while receiving");
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("Socket", "Socket error while receiving");
        }
    }
    private int last_ack_received = 0;
    private int no_ack_received = 0;
    private void receiveAck() {
        Log.d("Ack Thread", "receiveAck Thread Started");
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            while (!endCallClicked) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    Log.d("Ack Socket", "Waiting for Ack");
                    //Log.d("Socket", "IP: " + audio_socket.getLocalAddress() + ", Port: " + audio_socket.getLocalPort());
                    ack_socket.receive(packet);
                    no_ack_received++;
                    Log.d("Ack Socket", "Ack received");
                    String receivedData = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    receivedData = receivedData.trim().substring(1);
                    int intValue = Integer.parseInt(receivedData);
                    ///////////////////////////////
                    last_ack_received = intValue;
                    int ackReceived = intValue;
                    long ackReceivedTimestamp = System.currentTimeMillis();
                    if(timestampMap.containsKey(ackReceived))
                    {
                        long currentRTT = ackReceivedTimestamp - timestampMap.get(ackReceived);
                        rttQueue.add(currentRTT);
                        timestampMap.remove(ackReceived);
                    }
                    //////////////////////////////
                    customView.popPacket(intValue);

                } catch (Exception e) {
                    // Timeout occurred, check the endCallClicked flag
                    if (endCallClicked) {
                        break;
                    }
                }

            }
            Log.d("Socket", "Receive Socket Closed");
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("Socket", "Socket error while receiving");
        }
    }

    private void starVoipAudioCall() {
        wakeLock.acquire();
    }

    private void endVoIPAudioCall() {
        // Release the wake lock when the call ends
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private DataInputStream inputStreamRR = null;
    private Queue<byte[]> receptionReports = new ArrayDeque<>();

    private int packets_received_by_receiver = 0;
    private int maximum_seq_received_by_receiver = 0;
    private int min_seq_no_report = 0;

    private int total_packets_sent = 0;
    private int  prev_sent_packets = 0;
    private int  prev_RR_packet = 0;
    boolean reception_report_lost = false;
    double loss_percentage2 = 0;
    public void showLossTask()
    {
        Thread showLossTask = new Thread() {
            @Override
            public void run()
            {
                while (true)
                {
                    if(receptionReports.size() == 0 )
                    {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run()
                            {
                                lostPacketView.setText(String.format( "%.2f", 0.f) + "%");
                                //Log.d("Packet Loss 1", String.format( "%.2f", 0.f) + "%");
                            }
                        });
                    }

                    else
                    {
                        int queueSize = receptionReports.size();

                        int itemsInQueue = queueSize;
                        double averageLoss = 0;

                        while(itemsInQueue > 0)
                        {
                            byte[] receptionReport = receptionReports.poll();
                            //Object[] receptionReportDepack = packetizerObject.depacketize(receptionReport);
                            int[] receptionReportDepack = packetizerObject.decodePacket(receptionReport);
                            maximum_seq_received_by_receiver = (int)receptionReportDepack[1];
                            packets_received_by_receiver = (int)receptionReportDepack[2];
                            int reception_report_seq_recv = receptionReportDepack[3];

                            if(reception_report_seq_recv != (prev_RR_packet+1)) reception_report_lost = true;
                            prev_RR_packet = reception_report_seq_recv;

                            total_packets_sent = maximum_seq_received_by_receiver - min_seq_no_report;
                            double loss_percentage = ((double) (maximum_seq_received_by_receiver - min_seq_no_report + 1 - packets_received_by_receiver)) / (maximum_seq_received_by_receiver - min_seq_no_report + 1);


                            Log.d("Packet Loss", reception_report_lost+" " + total_packets_sent+1 +" ("+ maximum_seq_received_by_receiver+ "-" + min_seq_no_report + ") "+ packets_received_by_receiver);
                            min_seq_no_report = maximum_seq_received_by_receiver + 1;

                            averageLoss += loss_percentage;
                            itemsInQueue--;

                        }


                        averageLoss = averageLoss/queueSize;

                        if(averageLoss < 0)
                            averageLoss = 0.f;

                        double finalLoss_percentage = averageLoss;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run()
                            {
                                //lostPacketView.setText(String.format( "%.2f", finalLoss_percentage * 100) + "%");
                                if(total_packets_sent > 0 )
                                    lostPacketView.setText(String.format( "%.2f", finalLoss_percentage * 100) + "%");
                                    //lostPacketView.setText(String.format( "%.5f", loss_percentage2 + "%"));
                                else if (reception_report_lost) {
                                    lostPacketView.setText(String.format( "%.2f", 0.f) + "%");
                                    reception_report_lost = false;
                                } else
                                    lostPacketView.setText(String.format( "%.2f", 0.f) + "%");
                            }
                        });
                    }

                    try
                    {
                        TimeUnit.MILLISECONDS.sleep(1000);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        };
        showLossTask.start();
    }


    private ArrayDeque<Long> rttQueue = null;
    public void showRTTTask()
    {
        Thread showRTTTask = new Thread() {
            @Override
            public void run()
            {
                while (true)
                {
                    if(rttQueue.size() == 0)
                    {
                        //send packets to see the delay.

                    }

                    else
                    {
                        int queueSize = rttQueue.size();

                        int itemsInQueue = queueSize;
                        long averageRTT = 0;

                        while(itemsInQueue > 0)
                        {
                            long currentRTT = rttQueue.poll();
                            averageRTT += currentRTT;
                            itemsInQueue--;
                        }

                        averageRTT = averageRTT/queueSize;

                        double finalAverageRTT = averageRTT;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run()
                            {
                                delayView.setText("Avg. RTT : " + finalAverageRTT  + " ms");
                            }
                        });
//                        System.out.println("Average Delay " + averageDelay);
                    }

                    try
                    {
                        TimeUnit.MILLISECONDS.sleep(1000);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        };
        showRTTTask.start();
    }
}