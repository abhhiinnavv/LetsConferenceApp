package com.example.letsconference.listeners;

import com.example.letsconference.models.User;

public interface UsersListener {


    void initiateVideoMeeting(User user);

    void initiateAudioMeeting(User user);

    void onMultipleUsersAction(Boolean isMultipleUsersSelected);

}

