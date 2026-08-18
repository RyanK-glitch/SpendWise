package com.spendwise.notification;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.spendwise.BuildConfig;

import java.util.Map;

/**
 * Receives Firebase Cloud Messaging pushes, which is the one part of the app a server
 * starts rather than the user. It handles both payload shapes, because a notification
 * payload is drawn by the system while the app is in the background and delivered here
 * instead when it is in the foreground. Handling only one of those is the most common
 * push bug there is.
 */
public class SpendWiseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "SpendWiseFCM";

    public static final String TOPIC_BUDGET_ALERTS = "budget_alerts";

    private static final String KEY_TITLE = "title";
    private static final String KEY_BODY = "body";
    private static final String KEY_DESTINATION = "destination";
    private static final String KEY_CHANNEL = "channel";

    /** Subscribes this device to the budget alert topic. */
    public static void registerForPush() {
        FirebaseMessaging messaging = FirebaseMessaging.getInstance();

        messaging.subscribeToTopic(TOPIC_BUDGET_ALERTS).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.i(TAG, "Subscribed to FCM topic '" + TOPIC_BUDGET_ALERTS + "'");
            } else {
                Log.w(TAG, "FCM topic subscription failed", task.getException());
            }
        });

        if (BuildConfig.DEBUG) {
            messaging.getToken().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    logToken(task.getResult());
                } else {
                    Log.w(TAG, "FCM registration token unavailable", task.getException());
                }
            });
        }
    }

    /** Log token. */
    private static void logToken(String token) {
        if (token == null) {
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "FCM registration token: " + token);
        } else {
            Log.i(TAG, "FCM registration token refreshed (length=" + token.length() + ")");
        }
    }

    /**
     * Called when a push arrives while the app is running. Both payload shapes are handled
     * here because the system only draws a notification payload itself when the app is in
     * the background.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        String title = null;
        String body = null;
        String destination = NotificationHelper.DESTINATION_TRANSACTIONS;
        String channel = NotificationHelper.CHANNEL_BUDGET_ALERTS;

        Map<String, String> data = message.getData();
        if (!data.isEmpty()) {
            title = data.get(KEY_TITLE);
            body = data.get(KEY_BODY);
            if (data.get(KEY_DESTINATION) != null) {
                destination = data.get(KEY_DESTINATION);
            }
            String requestedChannel = data.get(KEY_CHANNEL);
            if (requestedChannel != null) {
                if (NotificationHelper.isKnownChannel(requestedChannel)) {
                    channel = requestedChannel;
                } else {
                    Log.w(TAG, "Push named unknown channel '" + requestedChannel
                            + "'; falling back to " + channel);
                }
            }
        }

        RemoteMessage.Notification payload = message.getNotification();
        if (payload != null) {
            if (title == null) {
                title = payload.getTitle();
            }
            if (body == null) {
                body = payload.getBody();
            }
        }

        if (title == null || title.trim().isEmpty()
                || body == null || body.trim().isEmpty()) {
            Log.w(TAG, "Discarded a push message with no usable title or body");
            return;
        }

        NotificationHelper.createChannels(getApplicationContext());
        boolean posted = NotificationHelper.post(getApplicationContext(), channel,
                (int) (System.currentTimeMillis() % Integer.MAX_VALUE),
                title, body, destination);
        if (!posted) {
            Log.w(TAG, "Push arrived but notifications are blocked for channel " + channel);
        }
    }

    /** The device token rotates. A server that kept the old one would be talking to nobody. */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        logToken(token);
    }
}
