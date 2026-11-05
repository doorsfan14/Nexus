# Nexus Web

React + TypeScript + Vite client using Supabase Auth, Postgres, Row Level Security and Storage. Android is a separate native Kotlin/Jetpack Compose app.

## Local setup
1. Create a Supabase project.
2. Run the SQL migration at supabase/migrations/202611050001_initial_schema.sql in the Supabase SQL Editor.
3. Copy .env.example to .env.local and set VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY.
4. Run npm install and npm run dev in this folder.

Never put a Supabase service-role key in a client app. The clients use the public anon/publishable key and the signed-in user's access token.

Implemented flows include email/password authentication, profile creation, feed, image/video posts, likes, comments, profile editing, follows, saved posts, search, notifications and one-to-one conversations.
