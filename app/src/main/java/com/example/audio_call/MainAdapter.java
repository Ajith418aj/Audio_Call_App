package com.example.audio_call;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audio_call.models.User;

import java.util.ArrayList;

public class MainAdapter extends RecyclerView.Adapter<MyViewHolder> {

    Context context;
    ArrayList<User> items;

    public MainAdapter(Context context, ArrayList<User> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MyViewHolder(LayoutInflater.from(context).inflate(R.layout.search_contact_recycler_horizontal,parent,false));
    }


    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {

        int row = position / 3; // Calculate row index (0 or 1)
        int col = position % 3; // Calculate column index (0, 1, or 2)

        // Calculate actual position in your data list based on row and column
        int actualPosition = row * 3 + col;

        // Now bind data to your views using the actualPosition
        if (actualPosition < items.size()) {
            User currentItem = items.get(actualPosition);
            holder.nameView.setText(currentItem.getName());
            holder.phoneView.setText(currentItem.getPhone_number());

            holder.itemView.setOnClickListener(view -> {
                Toast.makeText(context, "Adapter item clicked create object for control server", Toast.LENGTH_SHORT).show();
                String phoneNumber = currentItem.getPhone_number();
                String userName = currentItem.getName();
                phoneNumber = phoneNumber.replaceAll("\\s", "");

                Intent intent = new Intent(context, CallWaiting.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("phoneNumber", phoneNumber);
                intent.putExtra("userName", userName);
                context.startActivity(intent);
            });
        } else {
            // Clear views if the actual position is out of bounds
            holder.nameView.setText("");
            holder.phoneView.setText("");
            holder.itemView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return 6;
    }


}
