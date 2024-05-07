package com.example.audio_call;

import static java.nio.ByteOrder.BIG_ENDIAN;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

public class Packetizer {
    public byte[] addRtpHeaders(byte[] audioData, int sequenceNumber, long timestamp, int ssrc) {
        int payloadSize = audioData.length;
        int headerSize = 12; // RTP header size is fixed at 12 bytes

        // Create the RTP header
        byte[] rtpHeader = new byte[headerSize];

        // Version (2 bits), P (1 bit), X (1 bit), CC (4 bits)
        rtpHeader[0] = (byte) 0x80; // Version = 2, Padding = 0, Extension = 0, CC = 0

        // M (1 bit), PT (7 bits)
        rtpHeader[1] = (byte) 0x00; // Assuming payload type 128 (you can change it as needed)

        // Sequence Number (16 bits)
        rtpHeader[2] = (byte) (sequenceNumber >> 8);
        rtpHeader[3] = (byte) (sequenceNumber & 0xFF);

        // Timestamp (32 bits)
        rtpHeader[4] = (byte) ((timestamp >> 24) & 0xFF);
        rtpHeader[5] = (byte) ((timestamp >> 16) & 0xFF);
        rtpHeader[6] = (byte) ((timestamp >> 8) & 0xFF);
        rtpHeader[7] = (byte) (timestamp & 0xFF);

        // SSRC (32 bits)
        rtpHeader[8] = (byte) ((ssrc >> 24) & 0xFF);
        rtpHeader[9] = (byte) ((ssrc >> 16) & 0xFF);
        rtpHeader[10] = (byte) ((ssrc >> 8) & 0xFF);
        rtpHeader[11] = (byte) (ssrc & 0xFF);

        // Create the final RTP packet with headers and audio data
        byte[] rtpPacket = new byte[headerSize + payloadSize];
        System.arraycopy(rtpHeader, 0, rtpPacket, 0, headerSize);
        System.arraycopy(audioData, 0, rtpPacket, headerSize, payloadSize);

        return rtpPacket;
    }

    public double getPacketHeight(byte[] buffer) {
        int n = buffer.length;
        float[] samples = new float[n / 2];
        int index = 0;

        for (int i = 0; i < buffer.length; ) {
            int currentSample = 0;
            currentSample = currentSample | buffer[i++] & 0xFF;
            currentSample = currentSample | buffer[i++] << 8;

            samples[index++] = currentSample / 32768f;
        }

        float rootMeanSquare = 0f;
        for (float sample : samples)
            rootMeanSquare += sample * sample;

        rootMeanSquare = (float) Math.sqrt(rootMeanSquare / samples.length);
        return (int) (1000 * rootMeanSquare) + 10;
    }

    // Function to generate a random 32-bit SSRC value
    public int generateRandomSSRC() {
        Random random = new Random();
        return random.nextInt();
    }

    // Function to remove RTP headers from incoming audio packets
    private byte[] removeRtpHeaders(byte[] rtpPacket) {
        int headerSize = 12; // RTP header size is fixed at 12 bytes
        int payloadSize = rtpPacket.length - headerSize;

        // Extract the audio data (payload) from the RTP packet
        byte[] audioData = new byte[payloadSize];
        System.arraycopy(rtpPacket, headerSize, audioData, 0, payloadSize);

        return audioData;
    }

    public Object[] depacketize(byte[] packet){
        byte[] packType = Arrays.copyOfRange(packet, 0, 2);
        int pack_type = ByteBuffer.wrap(packType).getShort();
        byte[] senderId = Arrays.copyOfRange(packet, 2, 6);
        int sender_id = ByteBuffer.wrap(senderId).getInt();

        if(pack_type == 1){ //If packet type is data, remove all the required data and send data + Ack packet to be sent.

            byte[] packetNumReceived = Arrays.copyOfRange(packet, 6, 10);
            int packet_received = ByteBuffer.wrap(packetNumReceived).getInt();

            byte[] TimeStamp = Arrays.copyOfRange(packet, 10, 18);
            long packetGeneratedTimeStamp = ByteBuffer.wrap(TimeStamp).getLong();
            byte[] data = Arrays.copyOfRange(packet, 18, packet.length); //Should be ID, should be UPDATED.

            long packetReceivedTimestamp = System.currentTimeMillis();
            int delay = (int)(packetReceivedTimestamp - packetGeneratedTimeStamp);



            byte[] new_ack_packet = packetize(data, 0, packet_received, 0, 0);

            return new Object[]{pack_type, data, new_ack_packet, packet_received, packetGeneratedTimeStamp};
        }
        else if (pack_type == 0)
        { //If the packet received is ACK, get the data and update the stats.
            //ID should also be checked and updated accordingly.
            byte[] packetNumRec = Arrays.copyOfRange(packet, 6, 10);
            int packet_ack_received = ByteBuffer.wrap(packetNumRec).getInt();
            packet_ack = packet_ack_received;

//            System.out.println("Delay " + delaySeconds);

            return new Object[]{pack_type, packet_ack_received};
        }
        // if the packet type is RECEPTION REPORT
        else
        {
            byte[] maximumSequenceNumberRecieved = Arrays.copyOfRange(packet, 6, 10);
            int maxSeqReceived = ByteBuffer.wrap(maximumSequenceNumberRecieved).getInt();

            byte[] receivePacketInLastInterval = Arrays.copyOfRange(packet, 10, 14);
            int recvPackLastInterval = ByteBuffer.wrap(receivePacketInLastInterval).getInt();

            return new Object[]{pack_type, maxSeqReceived, recvPackLastInterval};
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    private int packet_num_sent;
    private int packets_lost; //Should be an array or map, for a group call, should be UPDATED
    private int id;
    private int journey_time;
    private int packet_ack;//Should be an array or map for a group call, should be UPDATED
    private int our_packs_lost; //Should also be an array or map in a group call, should be UPDATED

    public Packetizer(int id){
        packet_num_sent = 0;
        packets_lost = 0;
        this.id = id;
        journey_time =0;
        packet_ack = 0;
        our_packs_lost = 0;
    }
    public Packetizer(){

    }
    public byte[] packetize( byte[] data, int type, int pack_num, int maximum_sequence_number_received, int received_packets_in_last_interval){

        //For Both audio packet and ACK.
        byte[] pack_type = ByteBuffer.allocate(2).order(BIG_ENDIAN).putShort((short) type).array(); // why not putInt??????????
        byte[] id = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(this.id).array();

        //For Data Packet. (Time is present in ACK also)
        byte[] psent = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(pack_num).array();
        long timeStamp = System.currentTimeMillis();
        byte[] time = ByteBuffer.allocate(8).putLong(timeStamp).array();

        // For ACK.
        byte[] pack = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(pack_num).array();
        byte[] num_packets_lost = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(packets_lost).array();

        // For Reception Report
        byte[] maximumSequenceNumberReceived = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(maximum_sequence_number_received).array();
        byte[] receivedPacketsInLastInterval = ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(received_packets_in_last_interval).array();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );

        // if the packet type is data
        if(type == 1){
            try {
                outputStream.write(pack_type); //Packet type
                outputStream.write(id);        //ID, is not implemented yet
                outputStream.write(psent);     //Packet number
                outputStream.write(time);      //Time spent
                outputStream.write(data);      //Data(Audio)
                byte packetizedData[] = outputStream.toByteArray( );
                packet_num_sent += 1;

                return packetizedData;
            } catch (IOException e) {
                e.printStackTrace();
                byte c[] = new byte[0];
                return c;
            }
        }

        //if the packet type is ACK
        else if(type == 0)
        {
            try {
                outputStream.write(pack_type); //Packet type
                outputStream.write(id);        //ID, is not implemented yet
                outputStream.write(pack);      //The packet number for which this ack is addressed t


                byte packetizedACK[] = outputStream.toByteArray( );
                return packetizedACK;
            } catch (IOException e) {
                e.printStackTrace();
                byte c[] = new byte[0];
                return c;
            }

        }
        // if the packet type is RECEPTION REPORT
        else
        {
            try {
                outputStream.write(pack_type); //Packet type
                outputStream.write(id);        //ID, is not implemented yet
                outputStream.write(maximumSequenceNumberReceived);     // the maximum sequence number received by the receiver
                outputStream.write(receivedPacketsInLastInterval);    // total packets received since last sent reception report.
                outputStream.write(maximumSequenceNumberReceived);      //dummy data

                byte packetizedReceptionReport[] = outputStream.toByteArray( );

                return packetizedReceptionReport;
            } catch (IOException e) {
                e.printStackTrace();
                byte c[] = new byte[0];
                return c;
            }
        }

    }
///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static byte[] encodePacket(int last_packet_sent, int maximum_sequence_number_received, int received_packets_in_last_interval, int reception_report_seq) {
        // Encode integers into a byte array
        ByteBuffer buffer = ByteBuffer.allocate(16); // Allocate a buffer of size 12 bytes (3 integers * 4 bytes each)
        buffer.putInt(last_packet_sent);
        buffer.putInt(maximum_sequence_number_received);
        buffer.putInt(received_packets_in_last_interval);
        buffer.putInt(reception_report_seq);
        byte[] encodedIntegers = buffer.array();

        // Concatenate "2" string with the encoded integers at the start
        byte[] encodedPacket = new byte[encodedIntegers.length + 1]; // Increase the length by 1 for the string "2"
        encodedPacket[0] = (byte) '2'; // Convert '2' character to byte
        System.arraycopy(encodedIntegers, 0, encodedPacket, 1, encodedIntegers.length);

        return encodedPacket;
    }

    public static int[] decodePacket(byte[] packet) {
        ByteBuffer buffer = ByteBuffer.wrap(packet, 1, packet.length - 1); // Skip the first byte ('2') when decoding
        int last_packet_sent = buffer.getInt();
        int maximum_sequence_number_received = buffer.getInt();
        int received_packets_in_last_interval = buffer.getInt();
        int reception_report_sequence = buffer.getInt();
        return new int[]{last_packet_sent, maximum_sequence_number_received, received_packets_in_last_interval, reception_report_sequence};
    }
}
