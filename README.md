# SpendWise

A personal expense tracker for Android, written in Java.


---

## Quick start

```bash
# From the SpendWise/ directory
gradlew.bat assembleDebug          # build the APK
gradlew.bat testDebugUnitTest      # 185 unit tests, ~2 seconds, no emulator needed
gradlew.bat connectedDebugAndroidTest   # 57 instrumented tests, needs a device/emulator
```

The project builds and its unit tests pass **out of the box**, no Firebase account,
no API keys, no configuration. See *Enabling Firebase and Google Sign-In* below for
how to switch on the third-party features.

### Requirements

| | |
|---|---|
| JDK | 17 (Android Studio's bundled JBR works) |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.14 (wrapper included) |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |

`local.properties` is machine-specific and is **not** included in the submission zip.
Android Studio writes it on first open; to create it by hand:

```properties
sdk.dir=C\:/Users/YOUR_NAME/AppData/Local/Android/Sdk
```

> Use **forward slashes**. In a Java `.properties` file a single backslash starts an
> escape sequence, so `C:\Users\rizla\AppData` is silently read as `C:Usersrizla` with
> a carriage return where `\r` appeared. This cost real debugging time, see DEF-01 in
> the report's defect log.

---

## First run

1. Launch the app and tap **Sign up**.
2. Register any email and a password of at least 8 characters containing a letter and a number.
3. The ledger opens **empty**. Record transactions with the **+** button.

The app deliberately creates no data of its own. A personal finance application that
invents transactions on its owner's behalf is worse than one that starts blank, because
the user cannot tell which figures are theirs.

Search and filtering still have to be *verified* at a scale nobody types by hand, so a
deterministic generator, `LedgerFixture`, produces a twelve-month ledger of ~450
transactions across all twelve categories. It lives in `app/src/testFixtures/java`, a
directory added to the `test` and `androidTest` source sets and to neither production
one, so it cannot reach a shipped APK. The fixed random seed makes the same ledger
every time, which is what lets test assertions about it stay stable.

> **For a demo or a screencast**, enter 20–30 transactions across several categories
> first. Search and filtering have nothing to show on an empty ledger.

---

## Project layout

```
SpendWise/
├── app/src/main/java/com/spendwise/
│   ├── domain/          Pure Java. NO Android imports, this is deliberate.
│   │                    TransactionFilter, BudgetCalculator, Validators,
│   │                    PasswordHasher, LoginAttemptTracker, Guard, Currency
│   ├── data/            Room database, DAOs, entities, repositories,
│   │                    SessionManager (encrypted), sync/FirestoreSync
│   ├── ui/              Activities, Fragments, ViewModels, adapters,
│   │                    ThemeManager and CurrencyManager (both preference-backed)
│   ├── notification/    NotificationHelper, BudgetAlertWorker, FCM service
│   └── util/            CurrencyFormatter
├── app/src/testFixtures/ LedgerFixture, shared by both test source sets, never shipped
├── app/src/test/        185 JVM unit tests
├── app/src/androidTest/ 57 instrumented tests
└── app/schemas/         Exported Room schema (keep this in source control)
```

**The `domain` package contains no Android imports at all.** That single constraint is
why 185 tests run on the JVM in 1.78 seconds with no emulator, and why the formal
reasoning in the report applies to code that actually ships rather than to a paper
model. Please keep it that way.

---

## Enabling Firebase and Google Sign-In

Both features are **fully implemented in code**. Neither is active until you supply
credentials, because the Google Services Gradle plugin hard-fails a build when its
configuration file is missing, applying it unconditionally would mean nobody could
compile or test this project without first creating a Firebase account.

The plugin is therefore applied conditionally:

```groovy
def firebaseConfigured = file('google-services.json').exists()
if (firebaseConfigured) {
    apply plugin: 'com.google.gms.google-services'
}
buildConfigField "boolean", "FIREBASE_CONFIGURED", "${firebaseConfigured}"
```

The Firebase libraries still compile in, so `SpendWiseMessagingService` and the auth
code are real, not stubbed. Only the *plugin* is gated.

### Step 1, get your debug SHA-1

```bash
gradlew.bat signingReport
```

Copy the **SHA1** value from the `debug` variant.

### Step 2, create the Firebase project

1. Go to <https://console.firebase.google.com> and create a project.
2. **Add app → Android**, package name `com.spendwise`.
3. Paste the SHA-1 from step 1.
4. Download `google-services.json` and drop it into the **`app/`** directory
   (i.e. `SpendWise/app/google-services.json`).
5. Rebuild. `BuildConfig.FIREBASE_CONFIGURED` is now `true`.

### Step 3, enable the sign-in providers

In the Firebase console under **Authentication → Sign-in method**, enable:

- **Email/Password**, activates emailed password reset
- **Google**, activates the "Continue with Google" button

> **If Google Sign-In returns error code 10 (`DEVELOPER_ERROR`)** the OAuth client does
> not match. Check that the package name is exactly `com.spendwise` and that the SHA-1
> you registered is the *debug* one from step 1, not a release fingerprint. The app
> detects this specific status and shows a setup message rather than crashing, see
> `GoogleSignInHelper.describeFailure`.

### Step 4, test push notifications

1. Run the app on an emulator image **with Google Play services**
   (`google_apis_playstore`) or on a physical device. A plain AOSP emulator will fail
   silently.
2. Firebase console → **Messaging → Create campaign → Firebase Notification messages**.
3. Send a test message to the device token (logged at info level on first launch).
4. Verify delivery in **all three** app states: foreground, background and killed.
   FCM handles these differently, and handling only one is the most common
   push-notification bug.

### Without Firebase

The app remains fully functional:

| Feature | Without Firebase | With Firebase |
|---|---|---|
| Sign up / sign in | Local PBKDF2 accounts | Local, optionally mirrored |
| Password reset | Request recorded, no email sent | Reset email delivered |
| Google Sign-In | Button shows a setup message | Full federated sign-in |
| Budget alerts | **Works**, WorkManager, offline | Works |
| Remote push | Not available | Full FCM delivery |

Budget alerts are the local half of the notification requirement and need no server at
all, which is what makes that feature demonstrable and testable out of the box.

---

## Testing

```bash
gradlew.bat testDebugUnitTest
# report: app/build/reports/tests/testDebugUnitTest/index.html
```

| Suite | Tests | Runtime |
|---|---|---|
| `TransactionFilterTest` | 32 | filter algebra: totality, identity, monotonicity, commutativity |
| `ValidatorsTest` | 28 | equivalence classes and both length boundaries |
| `BudgetCalculatorTest` | 26 | balance invariant, thresholds, overflow saturation |
| `CurrencyFormatterTest` | 26 | parsing, round-trip, floating-point drift demonstration |
| `PasswordHasherTest` | 22 | salt uniqueness, constant-time compare, fail-closed |
| `GuardTest` | 21 | contract guards and entity preconditions |
| `LoginAttemptTrackerTest` | 15 | lockout state machine, expiry via injected clock |
| `LedgerFixtureTest` | 15 | determinism, coverage, no future-dated rows |
| **Total** | **185** | **1.78 s** |

Instrumented tests need a device or emulator:

```bash
gradlew.bat connectedDebugAndroidTest
# report: app/build/reports/androidTests/connected/index.html
```

The most valuable one is `TransactionDaoTest.sqlFilterAgreesWithTheSpecificationPredicate…`
, a **differential test** that runs 20 filter configurations through both the SQL query
and the pure Java predicate and asserts they agree row for row. The filter rule
necessarily exists twice (once as the specification, once as indexed SQL), and this is
what stops the two copies drifting apart.

> **Note:** the instrumented suite was written and compiles into a test APK, but had
> not been executed at the time the report was compiled, the emulator would not start
> for lack of disk space. Run it and record the results before submitting.

---

## Notes for the marker

- **Money is never a `double`.** Amounts are a non-negative `long` count of cents, with
  income/expense direction stored separately. This is what makes the balance invariant
  provable, see report §4.2.1.
- **Java's `assert` keyword is deliberately unused.** Android's ART runtime strips
  assertion evaluation by default, so `assert` in a shipped APK is dead code. Contract
  checks use explicit `if`/`throw` in `Guard`, which is active in every build variant.
- **The display currency does not change what is stored.** Profile offers 15 currencies,
  but every amount in the database remains a count of LKR minor units. `Currency` converts
  only for display and for parsing what the user types, so the schema, the DAOs and the
  balance invariant are all untouched by the setting. The rates are fixed constants, not a
  live feed.
- **`app/schemas/` is committed on purpose.** It is what makes a future Room migration
  reviewable and testable.
- `local.properties`, `build/` and `.gradle/` are excluded from the submission zip.

---

## Licence

Submitted as coursework for CN6008 and CN6035 at the University of East London / LSBF.
Not licensed for redistribution.
