package com.spendwise.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.Task;
import com.spendwise.BuildConfig;
import com.spendwise.R;

/**
 * Google Sign-In, the federated identity route. It asks for the email and basic profile
 * scopes only, which is least privilege applied to OAuth.
 */
public final class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";

    private static final String WEB_CLIENT_ID_RESOURCE = "default_web_client_id";

    private final GoogleSignInClient client;

    public GoogleSignInHelper(Context context) {
        Context appContext = context.getApplicationContext();

        GoogleSignInOptions.Builder builder =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestProfile();

        String webClientId = resolveWebClientId(appContext);
        if (webClientId != null) {
            builder.requestIdToken(webClientId);
        } else {
            Log.i(TAG, "No web client id; Google Sign-In will not produce a Firebase session");
        }

        this.client = GoogleSignIn.getClient(appContext, builder.build());
    }

    /** True when configured. */
    public static boolean isConfigured(Context context) {
        return resolveWebClientId(context) != null;
    }

    /** Returns the sign in intent. */
    public Intent getSignInIntent() {
        return client.getSignInIntent();
    }

    /** Extract account. */
    @Nullable
    public static GoogleSignInAccount extractAccount(@Nullable Intent data) throws ApiException {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        return task.getResult(ApiException.class);
    }

    /** Signs out of Google, so the next sign in offers the account chooser again. */
    public void signOut() {
        client.signOut();
    }

    /** Describe failure. */
    public static String describeFailure(Context context, ApiException exception) {
        int statusCode = exception.getStatusCode();
        switch (statusCode) {
            case CommonStatusCodes.DEVELOPER_ERROR:

                return context.getString(R.string.auth_google_not_configured);
            case GoogleSignInStatusCodes.SIGN_IN_CANCELLED:
            case CommonStatusCodes.SIGN_IN_REQUIRED:
            case CommonStatusCodes.CANCELED:
                return context.getString(R.string.auth_google_cancelled);
            case GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS:

                return context.getString(R.string.fb_auth_google_in_progress);
            case CommonStatusCodes.NETWORK_ERROR:
                return context.getString(R.string.error_generic);
            case GoogleSignInStatusCodes.SIGN_IN_FAILED:
            default:
                return context.getString(R.string.auth_google_failed);
        }
    }

    /** Resolve web client id. */
    @Nullable
    private static String resolveWebClientId(@NonNull Context context) {
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            return null;
        }
        try {
            int id = context.getResources().getIdentifier(
                    WEB_CLIENT_ID_RESOURCE, "string", context.getPackageName());
            if (id == 0) {
                return null;
            }
            String value = context.getString(id);
            return value.trim().isEmpty() ? null : value;
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }
}
