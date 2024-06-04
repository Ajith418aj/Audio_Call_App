package com.example.audio_call;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audio_call.models.User;
import com.example.audio_call.utils.FirestoreUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    FirebaseAuth auth;
    Button logout_button;
    //TextView textView;
    FirebaseUser user;
    FirebaseFirestore db;
    FirestoreUtils firestoreUtils;

    private Button create, join, more_users;
    private String button_clicked;
    private String own_phoneNumber;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(this.getClass().getSimpleName(), "In main Activity");

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        //textView = findViewById(R.id.user_details);
        user = auth.getCurrentUser();
        firestoreUtils = new FirestoreUtils();

        /*if user is null register the user using LoginPhoneNumberActivity class
        * if Not null get local phone contacts, Firebase contacts and show only matched contacts to user*/
        if(user == null) {
            Intent intent = new Intent(getApplicationContext(), LoginPhoneNumberActivity.class);
            startActivity(intent);
            finish();
        } else {
            //textView.setText(user.getPhoneNumber());
            own_phoneNumber = user.getPhoneNumber();
            checkPermissionToGetContacts();
        }

        create = findViewById(R.id.create_btn);
        join = findViewById(R.id.join_btn);
        more_users = findViewById(R.id.more_users);
        logout_button = findViewById(R.id.logout);

        create.setOnClickListener(this);
        join.setOnClickListener(this);
        more_users.setOnClickListener(this);
        logout_button.setOnClickListener(this);

    }

    private void checkPermissionToGetContacts() {
        // Check permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("Permission", "Asking for Permission");
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS}, 0);
        } else {
            // Permission has already been granted
            ArrayList<User> phoneContacts = getPhoneLocalContacts();
            fetchAndDisplayRegisteredContacts(phoneContacts);
        }
    }

    @Override
    public void onClick(View view) {
        if(view.getId() == R.id.create_btn) {
            button_clicked = "C"; // Create button clicked
            openCreateActivity(button_clicked);
        } else if (view.getId() == R.id.join_btn) {
            button_clicked = "J"; // Join button clicked
            openCreateActivity(button_clicked);
        } else if (view.getId() == R.id.more_users) {
             // More users button clicked
            Intent intent = new Intent(this, AllContactsActivity.class);
            startActivity(intent);
        } else if (view.getId() == R.id.logout) {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(getApplicationContext(), LoginPhoneNumberActivity.class);
            startActivity(intent);
            finish();
        }
    }

    /*This method is invoked when user clicks on Create button and it will be redirected to CreateRoom class*/
    public void openCreateActivity(String button_clicked) {
        Intent intent = new Intent(this, CreateRoom.class);
        intent.putExtra("ButtonClicked", button_clicked);
        startActivity(intent);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d("onRequestPermissionsResult", "On request permission callback");
        if (requestCode == 0) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted
                ArrayList<User>  phoneContacts = getPhoneLocalContacts();
                fetchAndDisplayRegisteredContacts(phoneContacts);
            } else {
                // Permission denied
                Toast.makeText(this, "Permission denied. Can't display contacts.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /*
        This method will get contacts from local phone
    * */
    private ArrayList<User>  getPhoneLocalContacts() {
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
        return contactsList;
    }

    private void fetchAndDisplayRegisteredContacts(ArrayList<User> phoneContacts) {
        firestoreUtils.getContactsFromDB(new FirestoreUtils.OnContactsLoadedListener() {
            @Override
            public void onContactsLoaded(ArrayList<String> registeredContacts) {
                if (registeredContacts != null) {
                    Log.d("Contacts", "Total Phone contacts = " + phoneContacts.size());
                    Log.d("Contacts", "Total Registered contacts = " + registeredContacts.size());
                    ArrayList<User> commonContacts = new ArrayList<>();
                    for (User phoneContact : phoneContacts) {
                        String trimmedPhoneContact = phoneContact.getPhone_number().replaceAll("\\s", "");
                        for (String registeredContact : registeredContacts) {
                            String trimmedRegisteredContact = registeredContact.replaceAll("\\s", "");
                            if (trimmedRegisteredContact.equals(trimmedPhoneContact)) {
                                commonContacts.add(phoneContact);
                                break;
                            }
                        }
                    }
                    HashSet<String> phoneNumbersSet = new HashSet<>();
                    ArrayList<User> uniqueContacts = new ArrayList<>();

                    for (User user : commonContacts) {
                        String phoneNumberWithoutSpaces = user.getPhone_number().replaceAll("\\s+", ""); // Remove spaces
                        if (phoneNumbersSet.add(phoneNumberWithoutSpaces)) {
                            uniqueContacts.add(user);
                        }
                    }

                    Log.d("Contacts", "Total Common contacts = " + uniqueContacts.size());
                    List<User> tempList = new ArrayList<>(uniqueContacts).subList(0, Math.min(uniqueContacts.size(), 6));
                    ArrayList<User> limitedContacts = new ArrayList<>(tempList);
                    // Iterate over the limitedContacts list
                    Iterator<User> iterator = limitedContacts.iterator();

                    //Removing own phone number from the contacts
                    while (iterator.hasNext()) {
                        User user = iterator.next();
                        if (user.getPhone_number().replaceAll("\\s+","").equals(own_phoneNumber.replaceAll("\\s+",""))) {
                            iterator.remove();
                        }
                    }

                    Log.d("Contacts", "Total Limited contacts = " + limitedContacts.size());

                    RecyclerView recyclerView = findViewById(R.id.search_contact_recycler_view);
                    recyclerView.setLayoutManager(new GridLayoutManager(getApplicationContext(), 3));
                    recyclerView.setAdapter(new MainAdapter(getApplicationContext(), limitedContacts));

                } else {
                    Toast.makeText(MainActivity.this, "Failed to retrieve contacts from Firestore.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}