package com.example.audio_call.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class CustomView extends View {


    class Packet
    {
        int sequenceNumber;
        Rect rectangle;
        boolean gotAck;
        int packetWidth;
        int packetHeight;
        boolean isDropped;      // for color red of dropped packet

        Packet(int sequenceNumber, Rect rectangle, boolean gotAck, int packetWidth, int packetHeight)
        {
            this.sequenceNumber = sequenceNumber;
            this.rectangle = rectangle;
            this.gotAck = gotAck;
            this.packetWidth = packetWidth;
            this.packetHeight = packetHeight;
        }

    }

    private static int maxSequenceNumberReceived = 0;
    private static final int interpacketSpace = 1;

    int[] colors = new int[7];
    private Paint paint;

    List<Packet> packetQueue = Collections.synchronizedList(new ArrayList<Packet>()); //this is an array(queue) containing the audio pkts
    Map<Integer, Packet> packetAddress = Collections.synchronizedMap(new HashMap<>());


    //Debugging on screen
    private static int lossPacketsDebug = 0;

    public CustomView(Context context) {
        super(context);

        init(null);

    }

    public CustomView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        init(attrs);
    }

    public CustomView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(attrs);
    }

    public CustomView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init(attrs);
    }

    private void init(@Nullable AttributeSet set) //setting the colors of packets in the animation
    {
        shiftPacketThread();
        postInValidateThread();
        dropPacketThread();
        paint = new Paint();
        for(int i = 0; i < 5; i++)
            colors[i] = Color.argb(100 - i * 10, 0, 0, 255 - i * 40);

        colors[5] = Color.argb(255, 255, 0, 0);
        colors[6] = Color.argb(255, 0, 150, 0);

    }
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.WHITE); //fills the entire canvas (the area where the view is drawn) with a white color

        int size = packetQueue.size();
        for(int i = 0; i < size; i++)
        {
            try
            {
                Packet currentPacket = packetQueue.get(i);
                int currentColor = colors[currentPacket.sequenceNumber % 5];

                if(currentPacket.isDropped)
                    currentColor = colors[5];
                if(currentPacket.gotAck)
                    currentColor = colors[6];

                paint.setColor(currentColor);
                canvas.drawRect(currentPacket.rectangle, paint);

            }
            catch (Exception e)
            {
//                e.printStackTrace();
            }
        }
    }

    public void shiftPacketThread() //to move the packet?
    {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                while(true)
                {
                    for(int i = 0; i < packetQueue.size(); i++)
                    {
                        try
                        {
                            Packet currentPacket = packetQueue.get(i);

                            currentPacket.rectangle.left += currentPacket.packetWidth + interpacketSpace;
                            currentPacket.rectangle.right += currentPacket.packetWidth + interpacketSpace;
                        }
                        catch (Exception e)
                        {
//                               e.printStackTrace();
                        }
                    }

                    try {

                        TimeUnit.MILLISECONDS.sleep(40);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
    }

    //this function is called after shifting the packet, which means the view of the animation has to be updated...
    // so we need to invalidate the current view and replace(using onDraw) with the updated view
    public void postInValidateThread() //this is to update the animation ? with the help of onDraw
    {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                while(true)
                {
                    postInvalidate(); //this function invalidates (marks the "view" invalid)
                    try {
                        TimeUnit.MILLISECONDS.sleep(3);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
    }

    public void dropPacketThread() // defines when to remove the packet from the screen, if no ack comes
    {
        int threshold = 5;
        Thread thread_forward = new Thread(new Runnable() {
            @Override
            public void run() {

                while (true)
                {
                    Packet currentPacket;
                    int size = packetQueue.size();
                    for (int i = 0; i < size; i++)
                    {
                        try {

                            currentPacket = packetQueue.get(i);
                            if(currentPacket.sequenceNumber < (maxSequenceNumberReceived - threshold) && !currentPacket.gotAck)
                            {
                                dropPacket(currentPacket); //drop the packet if it reaches certain threshold
                                lossPacketsDebug++;
                            }
                        }
                        catch (Exception e)
                        {
//                            e.printStackTrace();
                        }
                    }
                }

            }
        });
        thread_forward.start();
    }

    private AtomicInteger newPacketCounter = new AtomicInteger(0); //by sam

    public void addPacket(int sequenceNumber, int packetHeight, byte[] buf)
    {

        //first shift everything to right.
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                int packetWidth = getPacketWidth(buf);
                Rect newPacketRectangle = new Rect(10, 180 - packetHeight, 15 + packetWidth, 180); //increase the right value to increase the pkt size
                Packet newPacket = new Packet(sequenceNumber, newPacketRectangle, false, packetWidth, packetHeight);

                packetAddress.put(sequenceNumber, newPacket);
                int currentCounter = newPacketCounter.incrementAndGet(); //by sam from here
                if (currentCounter == 5) {
                    packetQueue.add(newPacket);
                    newPacketCounter.set(0);
                }    //by sam till here

            }
        });
        thread.start();
    }



    private int getPacketWidth(byte[] buf) {
        return 5;
    }

    public void popPacket(int sequenceNumber){

        maxSequenceNumberReceived = Math.max(maxSequenceNumberReceived, sequenceNumber);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                Packet poppingPacket = packetAddress.get(sequenceNumber);

                try {

                    poppingPacket.gotAck = true;
                    while(poppingPacket.rectangle.left < 800)
                    {
                        poppingPacket.rectangle.left += poppingPacket.packetWidth + 20;
                        poppingPacket.rectangle.right += poppingPacket.packetWidth + 20;

                        try {
                            TimeUnit.MILLISECONDS.sleep(3);
                        } catch (InterruptedException e) {
//                            e.printStackTrace();
                        }

                    }
                    poppingPacket.rectangle.top = 0;
                    poppingPacket.rectangle.left = 0;
                    poppingPacket.rectangle.right = 0;
                    poppingPacket.rectangle.bottom = 0;

                    //remove the packet from the queue && remove it's entry from the map;

//                  System.out.println("Out : " + sequenceNumber + " : " + System.currentTimeMillis());

                    packetQueue.remove(poppingPacket);
                    packetAddress.remove(sequenceNumber);

                }
                catch (Exception e)
                {
//                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    public void dropPacket(Packet packet) {

        Packet droppingPacket = packet;

        packet.isDropped = true;
        while(droppingPacket.rectangle.top < 150)
        {
            droppingPacket.rectangle.top += 10;
            droppingPacket.rectangle.bottom += 10;
//            postInvalidate();
            try {
                TimeUnit.MILLISECONDS.sleep(15);
            } catch (InterruptedException e) {
//                e.printStackTrace();
            }
        }

        droppingPacket.rectangle.top = 0;
        droppingPacket.rectangle.left = 0;
        droppingPacket.rectangle.right = 0;
        droppingPacket.rectangle.bottom = 0;

        try {

            //remove the packet from the queue && remove it's entry from the map;
            packetQueue.remove(packet);
            packetAddress.remove(packet.sequenceNumber);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

}