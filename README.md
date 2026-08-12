# Piggy Buzzer Android

Android companion app for the Piggy Buzzer. It receives Firebase Cloud Messaging (FCM) data messages and opens a loud, full-screen alarm with vibration and a STOP button.

## Firebase setup

The Firebase Android package is `com.a8kernrh33.buzzer`.

1. Download `google-services.json` from Firebase.
2. Put it at `app/google-services.json` locally.
3. Never commit that file to GitHub.
4. Open this project in Android Studio and sync Gradle.
5. Build and install the debug APK.
6. Open the app and allow notifications.
7. Copy the displayed FCM device token. The Render server will use this token as its push destination.

## Alarm behavior

A high-priority FCM data message containing `name` triggers a high-importance alarm notification and a full-screen alarm activity. The activity uses the phone's default alarm ringtone, loops it, vibrates, wakes the screen, and provides a STOP ALARM button.

## Expected FCM data payload

```json
{
  "name": "Ankit K."
}
```

The server-side FCM integration should send this as a data-only, high-priority message to the device token.
