package com.example.audio_call.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

public class FirestoreUtils {

    private FirebaseFirestore db;

    public FirestoreUtils() {
        this.db = FirebaseFirestore.getInstance();
    }

    public interface OnContactsLoadedListener {
        void onContactsLoaded(ArrayList<String> registeredContacts);
    }

    public void getContactsFromDB(final OnContactsLoadedListener listener) {
        CollectionReference usersRef = db.collection("users");
        usersRef.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    ArrayList<String> phoneNumbers = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult()) {
                        if (document.exists()) {
                            String phoneNumber = document.getString("phone");
                            phoneNumbers.add(phoneNumber);
                        }
                    }
                    // Invoke the callback with the retrieved phone numbers
                    listener.onContactsLoaded(phoneNumbers);
                } else {
                    // Handle errors
                    Log.e("Firestore", "Error getting documents: ", task.getException());
                    // Invoke the callback with null in case of error
                    listener.onContactsLoaded(null);
                }
            }
        });
    }
}
