package com.example.audio_call;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class MyViewHolder extends RecyclerView.ViewHolder {

    ImageView imageView;
    TextView nameView, phoneView;

    public MyViewHolder(@NonNull View itemView) {
        super(itemView);
        imageView = itemView.findViewById(R.id.profile_pic_view);
        nameView = itemView.findViewById(R.id.username_text);
        phoneView = itemView.findViewById(R.id.phone_text);
    }

}
