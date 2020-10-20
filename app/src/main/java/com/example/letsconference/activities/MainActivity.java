package com.example.letsconference.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letsconference.R;
import com.example.letsconference.adapters.UsersAdapter;
import com.example.letsconference.listeners.UsersListener;
import com.example.letsconference.models.User;
import com.example.letsconference.utilities.Constants;
import com.example.letsconference.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements UsersListener {

    private PreferenceManager preferenceManager;
    private List<User> users;
    private UsersAdapter usersAdapter;
    private TextView textErrorMessage;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView imageConference;

    private int REQUEST_CODE_BATTERY_OPTIMIZATIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(getApplicationContext());

        imageConference = findViewById(R.id.imageConference);

        TextView textTitle = findViewById(R.id.textTitle);
        textTitle.setText(String.format(
                "%s %s",
                preferenceManager.getString(Constants.KEY_FIRST_NAME),
                preferenceManager.getString(Constants.KEY_LAST_NAME)
        ));

        findViewById(R.id.textSignOut).setOnClickListener(v -> signOut());


        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task -> {

            if (task.isSuccessful() && task.getResult() != null) {
                sendFCMTokenToDatabase(task.getResult().getToken());
            }
        });

        RecyclerView userRecyclerView = findViewById(R.id.usersRecyclerView);
        textErrorMessage = findViewById(R.id.textErrorMessage);

        users = new ArrayList<>();

        usersAdapter = new UsersAdapter(users, this);
        userRecyclerView.setAdapter(usersAdapter);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::getUsers);

        getUsers();

        checkForBatteryOptimization();

    }


    private void getUsers() {
        swipeRefreshLayout.setRefreshing(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS).get().addOnCompleteListener(task -> {


            swipeRefreshLayout.setRefreshing(false);
            String myUserID = preferenceManager.getString(Constants.KEY_USER_ID);
            if (task.isSuccessful() && task.getResult() != null) {
                users.clear();
                for (QueryDocumentSnapshot documentSnapshot : task.getResult()) {
                    if (myUserID.equals(documentSnapshot.getId())) {
                        continue;
                    }
                    User user = new User();
                    user.firstName = documentSnapshot.getString(Constants.KEY_FIRST_NAME);
                    user.lastName = documentSnapshot.getString(Constants.KEY_LAST_NAME);
                    user.email = documentSnapshot.getString(Constants.KEY_EMAIL);
                    user.token = documentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                    users.add(user);
                }

                if (users.size() > 0) {
                    usersAdapter.notifyDataSetChanged();
                } else {
                    textErrorMessage.setText(String.format("%s, No Users Available"));
                    textErrorMessage.setVisibility(View.VISIBLE);
                }

            } else {
                textErrorMessage.setText(String.format("%s, No Users Available"));
                textErrorMessage.setVisibility(View.VISIBLE);
            }
        });
    }


    private void sendFCMTokenToDatabase(String token) {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_USERS).document(
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );

        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error in sending token :" + e.getMessage(), Toast.LENGTH_SHORT).show());

    }

    private void signOut() {
        Toast.makeText(this, "Signing Out", Toast.LENGTH_SHORT).show();
        FirebaseFirestore database = FirebaseFirestore.getInstance();

        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_USERS).document(
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates).addOnSuccessListener(aVoid -> {

            preferenceManager.clearPreference();
            startActivity(new Intent(getApplicationContext(), SignInActivity.class));
            finish();

        })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error : " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void initiateVideoMeeting(User user) {

        if (user.token == null || user.token.trim().isEmpty()) {
            Toast.makeText(this, user.firstName +
                    " is not available for the meeting", Toast.LENGTH_SHORT).show();

        } else {
            Intent intent = new Intent(getApplicationContext(), OutgoingMeetingInvitation.class);
            intent.putExtra("name", user);
            intent.putExtra("type", "video");
            startActivity(intent);
        }
    }

    @Override
    public void initiateAudioMeeting(User user) {

        if (user.token == null || user.token.trim().isEmpty()) {
            Toast.makeText(this, user.firstName +
                    " is not available for the meeting", Toast.LENGTH_SHORT).show();

        } else {

            Intent intent = new Intent(getApplicationContext(), OutgoingMeetingInvitation.class);
            intent.putExtra("user", user);
            intent.putExtra("type", "audio");
            startActivity(intent);
        }

    }

    @Override
    public void onMultipleUsersAction(Boolean isMultipleUsersSelected) {
        if (isMultipleUsersSelected) {
            imageConference.setVisibility(View.VISIBLE);
            imageConference.setOnClickListener(v -> {

                Intent intent = new Intent(getApplicationContext(), OutgoingMeetingInvitation.class);
                intent.putExtra("selectedUsers", new Gson().toJson(usersAdapter.getSelectedUsers()));
                intent.putExtra("type", "video");
                intent.putExtra("isMultiple", true);
                startActivity(intent);
            });
        } else {
            imageConference.setVisibility(View.GONE);
        }
    }

    private void checkForBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Warning");
                builder.setMessage("Battery Optimization is Enabled. It can Interrupt Running Background Services");
                builder.setPositiveButton("Disable", (dialog, which) -> {

                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATIONS);
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
                builder.create().show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_BATTERY_OPTIMIZATIONS) {
            checkForBatteryOptimization();
        }
    }
}