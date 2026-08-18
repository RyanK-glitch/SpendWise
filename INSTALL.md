# SpendWise — installation manual

Submitted for **CN6035 Mobile and Distributed Systems**, Task C.

The same application is also the subject of a CN6008 submission; permission to use it
in both modules was obtained from both module leaders. `README.md` in this directory
is the CN6008-era developer readme and is kept for completeness. This file is the
installation manual the CN6035 brief asks for.

---

## 1. What you need

| | |
|---|---|
| JDK | 17 — Android Studio's bundled JBR is fine |
| Android Studio | Ladybug or newer |
| Android SDK | platform 36 (compileSdk / targetSdk 36) |
| Device | anything on API 24 or above, emulator or physical |
| Gradle | 8.14, supplied by the wrapper — do not install it separately |

Nothing else. No account, no API key, no server.

---

## 2. Build and run

```bash
# from this directory
gradlew.bat assembleDebug            # Windows
./gradlew assembleDebug              # macOS / Linux
```

Or open this folder in Android Studio, wait for the Gradle sync to finish, and press Run.

`local.properties` is machine-specific and is not in the repository. Android Studio
writes it on first open. To create it by hand:

```properties
sdk.dir=C\:/Users/YOUR_NAME/AppData/Local/Android/Sdk
```

Use forward slashes. In a Java `.properties` file a single backslash begins an escape
sequence, so `C:\Users\name` is read as `C:Usersname`.

---

## 3. Run the tests

```bash
gradlew.bat testDebugUnitTest             # 185 JVM unit tests, about 2 seconds, no device
gradlew.bat connectedDebugAndroidTest     # 57 instrumented tests, needs a running device
```

The unit tests need no emulator because the `domain` package contains no Android
imports. That constraint is deliberate and worth preserving.

---

## 4. First run

1. Tap **Sign up**.
2. Register any email, and a password of at least 8 characters containing a letter and
   a number.
3. The ledger opens empty. Add transactions with the **+** button.

The app creates no data of its own. For a demonstration, enter twenty or thirty
transactions across several categories first — search, filtering and the reports have
nothing to show on an empty ledger.

---

## 5. Running it without Firebase, which is the default

`google-services.json` is not in this repository. It carries a live API key, so it is
excluded by `.gitignore`. The build detects its absence and adapts:

```groovy
def firebaseConfigured = file('google-services.json').exists()
if (firebaseConfigured) {
    apply plugin: 'com.google.gms.google-services'
}
buildConfigField "boolean", "FIREBASE_CONFIGURED", "${firebaseConfigured}"
```

The Firebase libraries still compile in — the auth and messaging code is real, not
stubbed. Only the Gradle plugin is gated, because it hard-fails a build when its
configuration file is missing.

| Feature | Without Firebase | With Firebase |
|---|---|---|
| Sign up / sign in | Local PBKDF2 accounts | Local, optionally mirrored |
| Password reset | Request recorded, no email sent | Reset email delivered |
| Google Sign-In | Button shows a setup message | Full federated sign-in |
| Budget alerts | **Works** — WorkManager, entirely offline | Works |
| Cloud sync | **Off** | Firestore mirror and sign-in restore |
| Remote push | Not available | Full FCM delivery |

**For the distributed-systems behaviour discussed in the report you need section 6.**
Without Firebase there is no replication to observe.

---

## 6. Enabling Firebase, to see the sync

### Step 1 — get your debug SHA-1

```bash
gradlew.bat signingReport
```

Copy the `SHA1` value from the **debug** variant.

### Step 2 — create the project

1. Go to <https://console.firebase.google.com> and create a project.
2. **Add app → Android**, package name `com.spendwise`.
3. Paste the SHA-1 from step 1.
4. Download `google-services.json` into **`app/`** — that is `SpendWise/app/google-services.json`.
5. Rebuild. `BuildConfig.FIREBASE_CONFIGURED` is now `true`.

### Step 3 — enable the providers

Firebase console → **Authentication → Sign-in method** → enable **Email/Password** and
**Google**.

If Google Sign-In returns error code 10 (`DEVELOPER_ERROR`), the OAuth client does not
match: check the package name is exactly `com.spendwise`, and that you registered the
*debug* SHA-1 rather than a release one. The app detects this status and shows a setup
message instead of crashing.

### Step 4 — watch the replication

1. Sign in. Add a transaction.
2. Firebase console → **Firestore Database**. The document appears under
   `users/{uid}/transactions/{id}`, keyed on the local SQLite row id.
3. Put the device into aeroplane mode and add another. It appears in the ledger
   immediately and is held in the SDK's persistent write queue.
4. Restore the network. The queued document arrives with no action from the app.
5. Sign out and back in to trigger the one and only pull —
   `LoginActivity.restoreFromCloud`.

Step 5 is where the behaviour analysed in section 2.3 of the report becomes visible:
the reconcile is insert-if-absent, so anything edited elsewhere will not come back, and
anything deleted elsewhere will.

### Step 5 — test push, optional

Use an emulator image **with Google Play services** (`google_apis_playstore`) or a
physical device; a plain AOSP image fails silently. Firebase console → **Messaging** →
send to the `budget_alerts` topic. The registration token is logged on first launch but
never uploaded, so topic broadcast is the only addressing mode that works.

---

## 7. Troubleshooting

| Symptom | Cause |
|---|---|
| `SDK location not found` | `local.properties` missing — see section 2 |
| Gradle sync fails on first open | Let Android Studio download the SDK 36 platform, then retry |
| Google Sign-In error 10 | SHA-1 or package name mismatch — see section 6, step 3 |
| No push received | AOSP emulator without Play services, or notifications denied |
| Instrumented tests fail to start | No device attached, or the emulator has too little RAM |

---

## 8. Layout

```
SpendWise/
├── app/src/main/java/com/spendwise/
│   ├── domain/          pure Java, no Android imports
│   ├── data/            Room, DAOs, repositories, sync/FirestoreSync
│   ├── ui/              activities, fragments, adapters
│   ├── notification/    FCM service, WorkManager alert worker
│   └── util/            CurrencyFormatter
├── app/src/test/        185 JVM unit tests
├── app/src/androidTest/ 57 instrumented tests
└── app/schemas/         exported Room schema, version 1
```

The files discussed in the report are `data/sync/FirestoreSync.java`,
`data/repository/TransactionRepository.java` and `ui/auth/LoginActivity.java`.
