-- Device registry for Nexus sessions across web and native clients.
create table if not exists public.device_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  device_id text not null,
  device_name text not null,
  platform text not null,
  last_seen timestamptz not null default now(),
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  unique(user_id, device_id)
);
create index if not exists device_sessions_user_seen_idx on public.device_sessions(user_id, last_seen desc);
alter table public.device_sessions enable row level security;
drop policy if exists "Users view own device sessions" on public.device_sessions;
create policy "Users view own device sessions" on public.device_sessions for select to authenticated using(auth.uid() = user_id);
drop policy if exists "Users register own devices" on public.device_sessions;
create policy "Users register own devices" on public.device_sessions for insert to authenticated with check(auth.uid() = user_id);
drop policy if exists "Users update own device sessions" on public.device_sessions;
create policy "Users update own device sessions" on public.device_sessions for update to authenticated using(auth.uid() = user_id) with check(auth.uid() = user_id);
drop policy if exists "Users delete own device sessions" on public.device_sessions;
create policy "Users delete own device sessions" on public.device_sessions for delete to authenticated using(auth.uid() = user_id);
