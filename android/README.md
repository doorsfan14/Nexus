# Nexus Android

Native Kotlin and Jetpack Compose client. It does not use a WebView and does not embed the web app.

Create android/local.properties (do not commit it) with:
    sdk.dir=/path/to/Android/Sdk
    SUPABASE_URL=https://YOUR_PROJECT.supabase.co
    SUPABASE_ANON_KEY=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY

Use the public anon/publishable key, never the service-role key.

Open this folder in Android Studio and build the app module. The debug APK is generated at app/build/outputs/apk/debug/app-debug.apk.
