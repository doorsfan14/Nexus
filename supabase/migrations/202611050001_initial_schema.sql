-- Nexus shared backend schema. Run in the Supabase SQL editor.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique check (char_length(username) between 3 and 24),
  full_name text, bio text check (char_length(coalesce(bio,'')) <= 150), avatar_url text, website text,
  is_private boolean not null default false, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists public.posts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  caption text not null default '' check (char_length(caption) <= 2200), location text,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists public.post_media (
  id uuid primary key default gen_random_uuid(), post_id uuid not null references public.posts(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade, storage_path text not null, media_url text not null,
  media_type text not null default 'image' check (media_type in ('image','video')), position integer not null default 0,
  created_at timestamptz not null default now()
);
create table if not exists public.post_likes (
  post_id uuid not null references public.posts(id) on delete cascade, user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(), primary key(post_id,user_id)
);
create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(), post_id uuid not null references public.posts(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade, parent_id uuid references public.comments(id) on delete cascade,
  content text not null check(char_length(content) between 1 and 2200), created_at timestamptz not null default now()
);
create table if not exists public.follows (
  follower_id uuid not null references public.profiles(id) on delete cascade,
  following_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(), primary key(follower_id,following_id), check(follower_id <> following_id)
);
create table if not exists public.saved_posts (
  user_id uuid not null references public.profiles(id) on delete cascade, post_id uuid not null references public.posts(id) on delete cascade,
  created_at timestamptz not null default now(), primary key(user_id,post_id)
);
create table if not exists public.stories (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id) on delete cascade,
  storage_path text not null, media_url text not null, created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '24 hours'
);
create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(), created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists public.conversation_members (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade, joined_at timestamptz not null default now(),
  primary key(conversation_id,user_id)
);
create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(), conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade, body text not null check(char_length(body) between 1 and 5000),
  created_at timestamptz not null default now(), read_at timestamptz
);
create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(), recipient_id uuid not null references public.profiles(id) on delete cascade,
  actor_id uuid not null references public.profiles(id) on delete cascade, kind text not null check(kind in ('like','comment','follow')),
  entity_id uuid, is_read boolean not null default false, created_at timestamptz not null default now()
);

create index if not exists posts_user_created_idx on public.posts(user_id,created_at desc);
create index if not exists media_post_idx on public.post_media(post_id,position);
create index if not exists comments_post_created_idx on public.comments(post_id,created_at);
create index if not exists follows_following_idx on public.follows(following_id);
create index if not exists messages_conversation_created_idx on public.messages(conversation_id,created_at);
create index if not exists notifications_recipient_created_idx on public.notifications(recipient_id,created_at desc);

create or replace function public.set_updated_at() returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end; $$;
drop trigger if exists profiles_updated_at on public.profiles;
create trigger profiles_updated_at before update on public.profiles for each row execute function public.set_updated_at();
drop trigger if exists posts_updated_at on public.posts;
create trigger posts_updated_at before update on public.posts for each row execute function public.set_updated_at();
drop trigger if exists conversations_updated_at on public.conversations;
create trigger conversations_updated_at before update on public.conversations for each row execute function public.set_updated_at();

create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
declare wanted text; candidate text;
begin
  wanted := lower(regexp_replace(coalesce(new.raw_user_meta_data->>'username',split_part(coalesce(new.email,'nexus_user'),'@',1),'nexus_user'),'[^a-z0-9._]','','g'));
  if char_length(wanted) < 3 then wanted := 'user_' || substr(new.id::text,1,8); end if;
  candidate := left(wanted,24);
  if exists(select 1 from public.profiles where username = candidate) then candidate := left(wanted,14) || '_' || substr(new.id::text,1,8); end if;
  insert into public.profiles(id,username,full_name,avatar_url)
  values(new.id,candidate,nullif(new.raw_user_meta_data->>'full_name',''),nullif(new.raw_user_meta_data->>'avatar_url',''))
  on conflict(id) do nothing;
  return new;
end; $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute procedure public.handle_new_user();

create or replace function public.notify_social_action() returns trigger
language plpgsql security definer set search_path = public as $$
declare target uuid; actor uuid; kind_value text; entity uuid;
begin
  if tg_table_name = 'post_likes' then
    actor := new.user_id; select user_id into target from public.posts where id = new.post_id;
    kind_value := 'like'; entity := new.post_id;
  elsif tg_table_name = 'comments' then
    actor := new.user_id; select user_id into target from public.posts where id = new.post_id;
    kind_value := 'comment'; entity := new.post_id;
  else
    actor := new.follower_id; target := new.following_id; kind_value := 'follow'; entity := new.follower_id;
  end if;
  if target is not null and target <> actor then
    insert into public.notifications(recipient_id,actor_id,kind,entity_id) values(target,actor,kind_value,entity);
  end if;
  return new;
end; $$;
drop trigger if exists notify_post_like on public.post_likes;
create trigger notify_post_like after insert on public.post_likes for each row execute function public.notify_social_action();
drop trigger if exists notify_post_comment on public.comments;
create trigger notify_post_comment after insert on public.comments for each row execute function public.notify_social_action();
drop trigger if exists notify_follow on public.follows;
create trigger notify_follow after insert on public.follows for each row execute function public.notify_social_action();

create or replace function public.start_conversation(other_user_id uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare current_user_id uuid := auth.uid(); existing_id uuid; new_id uuid;
begin
  if current_user_id is null then raise exception 'Authentication required'; end if;
  if other_user_id is null or other_user_id = current_user_id then raise exception 'Choose another user'; end if;
  select cm.conversation_id into existing_id from public.conversation_members cm
  where cm.user_id = current_user_id
    and exists(select 1 from public.conversation_members o where o.conversation_id = cm.conversation_id and o.user_id = other_user_id)
    and (select count(*) from public.conversation_members m where m.conversation_id = cm.conversation_id) = 2 limit 1;
  if existing_id is not null then return existing_id; end if;
  insert into public.conversations default values returning id into new_id;
  insert into public.conversation_members(conversation_id,user_id) values(new_id,current_user_id),(new_id,other_user_id);
  return new_id;
end; $$;
grant execute on function public.start_conversation(uuid) to authenticated;

alter table public.profiles enable row level security;
alter table public.posts enable row level security;
alter table public.post_media enable row level security;
alter table public.post_likes enable row level security;
alter table public.comments enable row level security;
alter table public.follows enable row level security;
alter table public.saved_posts enable row level security;
alter table public.stories enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;
alter table public.notifications enable row level security;

drop policy if exists "Profiles are visible" on public.profiles;
create policy "Profiles are visible" on public.profiles for select using(true);
drop policy if exists "Users update own profile" on public.profiles;
create policy "Users update own profile" on public.profiles for update using(auth.uid()=id) with check(auth.uid()=id);

drop policy if exists "Posts are visible" on public.posts;
create policy "Posts are visible" on public.posts for select using(true);
drop policy if exists "Users create posts" on public.posts;
create policy "Users create posts" on public.posts for insert with check(auth.uid()=user_id);
drop policy if exists "Users edit posts" on public.posts;
create policy "Users edit posts" on public.posts for update using(auth.uid()=user_id) with check(auth.uid()=user_id);
drop policy if exists "Users delete posts" on public.posts;
create policy "Users delete posts" on public.posts for delete using(auth.uid()=user_id);

drop policy if exists "Post media is visible" on public.post_media;
create policy "Post media is visible" on public.post_media for select using(true);
drop policy if exists "Owners add media" on public.post_media;
create policy "Owners add media" on public.post_media for insert with check(auth.uid()=user_id and exists(select 1 from public.posts p where p.id=post_id and p.user_id=auth.uid()));
drop policy if exists "Owners delete media" on public.post_media;
create policy "Owners delete media" on public.post_media for delete using(auth.uid()=user_id);

drop policy if exists "Likes are visible" on public.post_likes;
create policy "Likes are visible" on public.post_likes for select using(true);
drop policy if exists "Users like as themselves" on public.post_likes;
create policy "Users like as themselves" on public.post_likes for insert with check(auth.uid()=user_id);
drop policy if exists "Users remove own likes" on public.post_likes;
create policy "Users remove own likes" on public.post_likes for delete using(auth.uid()=user_id);

drop policy if exists "Comments are visible" on public.comments;
create policy "Comments are visible" on public.comments for select using(true);
drop policy if exists "Users comment as themselves" on public.comments;
create policy "Users comment as themselves" on public.comments for insert with check(auth.uid()=user_id);
drop policy if exists "Users delete own comments" on public.comments;
create policy "Users delete own comments" on public.comments for delete using(auth.uid()=user_id);

drop policy if exists "Follows are visible" on public.follows;
create policy "Follows are visible" on public.follows for select using(true);
drop policy if exists "Users follow as themselves" on public.follows;
create policy "Users follow as themselves" on public.follows for insert with check(auth.uid()=follower_id);
drop policy if exists "Users unfollow as themselves" on public.follows;
create policy "Users unfollow as themselves" on public.follows for delete using(auth.uid()=follower_id);

drop policy if exists "Saved posts are private" on public.saved_posts;
create policy "Saved posts are private" on public.saved_posts for select using(auth.uid()=user_id);
drop policy if exists "Users save posts" on public.saved_posts;
create policy "Users save posts" on public.saved_posts for insert with check(auth.uid()=user_id);
drop policy if exists "Users remove saved posts" on public.saved_posts;
create policy "Users remove saved posts" on public.saved_posts for delete using(auth.uid()=user_id);

drop policy if exists "Active stories are visible" on public.stories;
create policy "Active stories are visible" on public.stories for select using(expires_at>now());
drop policy if exists "Users publish stories" on public.stories;
create policy "Users publish stories" on public.stories for insert with check(auth.uid()=user_id);
drop policy if exists "Users delete own stories" on public.stories;
create policy "Users delete own stories" on public.stories for delete using(auth.uid()=user_id);

drop policy if exists "Conversation members view conversations" on public.conversations;
create policy "Conversation members view conversations" on public.conversations for select using(exists(select 1 from public.conversation_members cm where cm.conversation_id=id and cm.user_id=auth.uid()));
drop policy if exists "Conversation members view members" on public.conversation_members;
create policy "Conversation members view members" on public.conversation_members for select using(exists(select 1 from public.conversation_members cm where cm.conversation_id=conversation_id and cm.user_id=auth.uid()));
drop policy if exists "Members view messages" on public.messages;
create policy "Members view messages" on public.messages for select using(exists(select 1 from public.conversation_members cm where cm.conversation_id=conversation_id and cm.user_id=auth.uid()));
drop policy if exists "Members send messages" on public.messages;
create policy "Members send messages" on public.messages for insert with check(auth.uid()=sender_id and exists(select 1 from public.conversation_members cm where cm.conversation_id=conversation_id and cm.user_id=auth.uid()));

drop policy if exists "Users see own notifications" on public.notifications;
create policy "Users see own notifications" on public.notifications for select using(auth.uid()=recipient_id);
drop policy if exists "Users mark own notifications" on public.notifications;
create policy "Users mark own notifications" on public.notifications for update using(auth.uid()=recipient_id) with check(auth.uid()=recipient_id);

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('post-media','post-media',true,52428800,array['image/jpeg','image/png','image/webp','image/gif','video/mp4'])
on conflict(id) do update set public=true,file_size_limit=52428800,allowed_mime_types=excluded.allowed_mime_types;
drop policy if exists "Public post media reads" on storage.objects;
create policy "Public post media reads" on storage.objects for select using(bucket_id='post-media');
drop policy if exists "Users upload post media" on storage.objects;
create policy "Users upload post media" on storage.objects for insert to authenticated with check(bucket_id='post-media' and (storage.foldername(name))[1]=auth.uid()::text);
drop policy if exists "Users delete own post media" on storage.objects;
create policy "Users delete own post media" on storage.objects for delete to authenticated using(bucket_id='post-media' and (storage.foldername(name))[1]=auth.uid()::text);
