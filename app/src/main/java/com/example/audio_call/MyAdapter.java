package com.example.audio_call;

import static java.nio.ByteOrder.BIG_ENDIAN;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audio_call.models.User;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class MyAdapter extends RecyclerView.Adapter<MyViewHolder> {

    Context context;
    ArrayList<User> items;

    public MyAdapter(Context context, ArrayList<User> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MyViewHolder(LayoutInflater.from(context).inflate(R.layout.search_contact_recycler_row,parent,false));
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        holder.nameView.setText(items.get(position).getName());
        holder.phoneView.setText(items.get(position).getPhone_number());

        holder.itemView.setOnClickListener(view -> {
            Toast.makeText(context, "Adapter item clicked create object for control server", Toast.LENGTH_SHORT).show();
            String phoneNumber = items.get(position).getPhone_number();
            String userName = items.get(position).getName();
            phoneNumber = phoneNumber.replaceAll("\\s", "");

            Intent intent = new Intent(context, CallWaiting.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("phoneNumber", phoneNumber);
            intent.putExtra("userName", userName);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        Log.d("Items", "Total Items = " + items.size());
        return items.size();
    }

}
