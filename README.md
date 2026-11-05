# Nexus

Nexus is a photo and video sharing social app with two separate clients.

- Web: React, TypeScript and Vite in web/.
- Android: native Kotlin and Jetpack Compose in android/. It does not embed the web client or use a WebView.
- Backend: shared Supabase Auth, Postgres, Row Level Security and Storage schema in supabase/migrations/.

## Backend setup

1. Create a Supabase project.
2. Run supabase/migrations/202611050001_initial_schema.sql in the Supabase SQL Editor.
3. Copy web/.env.example to web/.env.local and fill in the project URL and public anon/publishable key.
4. Add the same credentials to android/local.properties as documented in android/README.md.

Never use a service-role key in either client. Both apps authenticate individually and rely on RLS for authorization.

## Web

From web/: npm install, npm run dev. Run npm run build for the production bundle.

## Android

Open android/ in Android Studio, configure local.properties, and build the app module. Debug APK output: android/app/build/outputs/apk/debug/app-debug.apk.

## Features

Email/password auth, profiles, media posts, likes, comments, follows, search, notifications and direct conversations. The migration and valid Supabase credentials are required.
