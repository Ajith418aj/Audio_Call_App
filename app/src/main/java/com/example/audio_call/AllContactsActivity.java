package com.example.audio_call;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.Manifest;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.Toast;

import com.example.audio_call.models.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class AllContactsActivity extends AppCompatActivity {

    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_contacts);

        db = FirebaseFirestore.getInstance();
        RecyclerView recyclerView = findViewById(R.id.search_contact_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Check permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS}, 0);
        } else {
            // Permission has already been granted
            ArrayList<User> phoneContacts = displayContacts();
            Log.d("Contacts", "Total phone contacts = " + phoneContacts.size());

            // Get contacts from Firestore
            getContactsFromDB(new OnContactsLoadedListener() {
                @Override
                public void onContactsLoaded(ArrayList<String> registeredContacts) {
                    if (registeredContacts != null) {
                        Log.d("Contacts", "Total registered contacts = " + registeredContacts.size());
                        // Find common contacts
                        ArrayList<User> commonContacts = new ArrayList<>();
                        for (User phoneContact : phoneContacts) {
                            // Remove all spaces (leading, trailing, and within the string)
                            String trimmedPhoneContact = phoneContact.getPhone_number().replaceAll("\\s", "");
                            for (String registeredContact : registeredContacts) {
                                // Remove all spaces (leading, trailing, and within the string)
                                String trimmedRegisteredContact = registeredContact.replaceAll("\\s", "");
                                if (trimmedRegisteredContact.equals(trimmedPhoneContact)) {
                                    commonContacts.add(phoneContact);
                                    // Break the inner loop if a common contact is found
                                    break;
                                }
                            }
                        }
                        HashSet<String> phoneNumbersSet = new HashSet<>();
                        ArrayList<User> uniqueContacts = new ArrayList<>();

                        for (User user : commonContacts) {
                            String phoneNumberWithoutSpaces = user.getPhone_number().replaceAll("\\s+", ""); // Remove spaces
                            // Check if the phone number (after removing spaces) is already in the HashSet
                            if (phoneNumbersSet.add(phoneNumberWithoutSpaces)) {
                                // If not, add the User object to the uniqueContacts list
                                uniqueContacts.add(user);
                            }
                        }
                        recyclerView.setAdapter(new MyAdapter(getApplicationContext(), uniqueContacts));
                    } else {
                        // Handle null result
                        Toast.makeText(AllContactsActivity.this, "Failed to retrieve contacts from Firestore.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    // Define an interface for the callback
    public interface OnContactsLoadedListener {
        void onContactsLoaded(ArrayList<String> phoneNumbers);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted
                ArrayList<User>  phoneContacts = displayContacts();
            } else {
                // Permission denied
                Toast.makeText(this, "Permission denied. Can't display contacts.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private ArrayList<User>  displayContacts() {
        ArrayList<User> contactsList = new ArrayList<>();

        Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null);

        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") String contactNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                @SuppressLint("Range") String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));

                contactsList.add(new User(contactName, contactNumber));
            }
            cursor.close();
        }

        /*ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, contactsList);
        contactsListView.setAdapter(adapter);*/
        return contactsList;
    }

    private void getContactsFromDB(final OnContactsLoadedListener listener) {
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
                    Log.d("Contacts", "Total registered contacts = " + phoneNumbers.size());
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