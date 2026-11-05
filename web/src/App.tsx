import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Bell, Bookmark, Camera, ChevronDown, Compass, Heart, Home, ImagePlus, LogOut, MessageCircle, MoreHorizontal, PlusSquare, Search, Send, Settings, UserRound, X, LoaderCircle } from "lucide-react";
import type { Session, User } from "@supabase/supabase-js";
import { supabase, isSupabaseConfigured } from "./lib/supabase";

type Profile = { id: string; username: string; full_name: string | null; avatar_url: string | null; bio: string | null; website: string | null };
type Media = { id: string; media_url: string; media_type: string };
type Post = { id: string; user_id: string; caption: string; location: string | null; created_at: string; profiles: Profile; post_media: Media[]; post_likes: { user_id: string }[]; comments?: { count: number }[] };
type Comment = { id: string; content: string; created_at: string; profiles: Profile };
type Story = { id: string; user_id: string; media_url: string; media_type: string; expires_at: string; profiles: Profile };
type Screen = "home" | "explore" | "messages" | "notifications" | "profile" | "saved" | "settings";
const fallbackAvatar = "https://www.gravatar.com/avatar/00000000000000000000000000000000?d=mp&f=y";
const dateLabel = (value: string) => { const seconds = Math.max(1, Math.floor((Date.now() - new Date(value).getTime()) / 1000)); if (seconds < 60) return seconds + "s"; if (seconds < 3600) return Math.floor(seconds / 60) + "m"; if (seconds < 86400) return Math.floor(seconds / 3600) + "h"; return Math.floor(seconds / 86400) + "d"; };

function Avatar({ profile, size = 38 }: { profile?: Partial<Profile> | null; size?: number }) {
  return <img className="avatar" style={{ width: size, height: size }} src={profile?.avatar_url || fallbackAvatar} alt="" />;
}
function AuthScreen() {
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [username, setUsername] = useState("");
  const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError(""); if (!supabase) return; setBusy(true);
    try {
      if (mode === "signup") {
        const { error } = await supabase.auth.signUp({ email, password, options: { data: { username: username.trim().toLowerCase() } } });
        if (error) throw error; setError("Check your email to confirm your account, then sign in."); setMode("login");
      } else { const { error } = await supabase.auth.signInWithPassword({ email, password }); if (error) throw error; }
    } catch (e) { setError(e instanceof Error ? e.message : "Something went wrong."); } finally { setBusy(false); }
  };
  return <main className="auth-page"><section className="auth-card"><div className="brand auth-brand">nexus</div><p className="auth-tagline">Share what moves you.</p>
    {!isSupabaseConfigured && <div className="notice error">Add VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY to web/.env.local, then restart the app.</div>}
    <form onSubmit={submit} className="auth-form">
      {mode === "signup" && <input required minLength={3} maxLength={24} value={username} onChange={e => setUsername(e.target.value)} placeholder="Username" autoComplete="username"/>}
      <input required type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="Email address" autoComplete="email"/>
      <input required minLength={6} type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="Password" autoComplete={mode === "login" ? "current-password" : "new-password"}/>
      <button className="primary-button" disabled={busy || !isSupabaseConfigured}>{busy ? "Please wait…" : mode === "login" ? "Log in" : "Create account"}</button>
    </form>
    {error && <p className="form-message">{error}</p>}
    <div className="auth-switch">{mode === "login" ? "Don't have an account?" : "Already have an account?"} <button onClick={() => { setMode(mode === "login" ? "signup" : "login"); setError(""); }}>{mode === "login" ? "Sign up" : "Log in"}</button></div>
  </section><footer className="auth-footer">About · Help · Terms · Privacy · © 2026 NEXUS</footer></main>;
}
function PostCard({ post, currentUser, onLike, onComment, onProfile, onSave, saved }: { post: Post; currentUser: User; onLike: (post: Post) => void; onComment: (post: Post) => void; onProfile: (profile: Profile) => void; onSave: (post: Post) => void; saved: boolean }) {
  const liked = post.post_likes?.some(item => item.user_id === currentUser.id) || false;
  const media = post.post_media?.[0];
  return <article className="post-card">
    <header className="post-header"><button className="avatar-button" onClick={() => onProfile(post.profiles)}><Avatar profile={post.profiles}/></button>
      <div className="post-author"><button onClick={() => onProfile(post.profiles)}>{post.profiles?.username || "user"}</button>{post.location && <span>{post.location}</span>}</div>
      <span className="post-time">{dateLabel(post.created_at)}</span><button className="icon-button subtle"><MoreHorizontal size={21}/></button>
    </header>
    {media?.media_type === "video" ? <video className="post-media" src={media.media_url} controls playsInline preload="metadata"/> : media ? <img className="post-media" src={media.media_url} alt={post.caption || "Post"} loading="lazy"/> : <div className="text-post-media">{post.caption}</div>}
    <div className="post-controls"><div className="post-control-group">
      <button className={"icon-button " + (liked ? "liked" : "")} aria-label={liked ? "Unlike" : "Like"} onClick={() => onLike(post)}><Heart size={25} fill={liked ? "currentColor" : "none"}/></button>
      <button className="icon-button" aria-label="Comment" onClick={() => onComment(post)}><MessageCircle size={24}/></button>
      <button className="icon-button" aria-label="Share"><Send size={23}/></button>
    </div><button className={"icon-button " + (saved ? "saved" : "")} aria-label="Save post" onClick={() => onSave(post)}><Bookmark size={24} fill={saved ? "currentColor" : "none"}/></button></div>
    <div className="post-copy"><button className="like-count" onClick={() => onComment(post)}>{(post.post_likes?.length || 0).toLocaleString()} likes</button>
      <p><button className="inline-username" onClick={() => onProfile(post.profiles)}>{post.profiles?.username}</button> {post.caption}</p>
      <button className="view-comments" onClick={() => onComment(post)}>View all {post.comments?.[0]?.count || 0} comments</button>
    </div>
  </article>;
}
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return <div className="modal-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}><section className="modal">
    <header className="modal-header"><strong>{title}</strong><button className="icon-button" onClick={onClose}><X/></button></header>{children}
  </section></div>;
}

export default function App() {
  const [session, setSession] = useState<Session | null>(null); const [user, setUser] = useState<User | null>(null); const [profile, setProfile] = useState<Profile | null>(null);
  const [stories, setStories] = useState<Story[]>([]);
  const [activeStory, setActiveStory] = useState<Story | null>(null);
  const [storyComposerOpen, setStoryComposerOpen] = useState(false);
  const [storyFile, setStoryFile] = useState<File | null>(null);
  const [publishingStory, setPublishingStory] = useState(false);
  const [screen, setScreen] = useState<Screen>("home"); const [posts, setPosts] = useState<Post[]>([]); const [loading, setLoading] = useState(false); const [notice, setNotice] = useState("");
  const [query, setQuery] = useState(""); const [people, setPeople] = useState<Profile[]>([]); const [targetProfile, setTargetProfile] = useState<Profile | null>(null); const [targetPosts, setTargetPosts] = useState<Post[]>([]);
  const [followers, setFollowers] = useState(0); const [following, setFollowing] = useState(0); const [isFollowing, setIsFollowing] = useState(false);
  const [composerOpen, setComposerOpen] = useState(false); const [caption, setCaption] = useState(""); const [location, setLocation] = useState(""); const [file, setFile] = useState<File | null>(null); const [publishing, setPublishing] = useState(false);
  const [commentPost, setCommentPost] = useState<Post | null>(null); const [comments, setComments] = useState<Comment[]>([]); const [commentText, setCommentText] = useState("");
  const [savedIds, setSavedIds] = useState<string[]>([]); const [conversations, setConversations] = useState<any[]>([]); const [activeConversation, setActiveConversation] = useState<any | null>(null); const [messages, setMessages] = useState<any[]>([]); const [messageText, setMessageText] = useState("");
  const [notifications, setNotifications] = useState<any[]>([]); const [settingsName, setSettingsName] = useState(""); const [settingsBio, setSettingsBio] = useState(""); const [settingsWebsite, setSettingsWebsite] = useState(""); const [savingProfile, setSavingProfile] = useState(false);

  useEffect(() => {
    if (!supabase) return;
    supabase.auth.getSession().then(({ data }) => setSession(data.session));
    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, next) => setSession(next));
    return () => subscription.unsubscribe();
  }, []);
  useEffect(() => { setUser(session?.user || null); }, [session]);
  useEffect(() => {
    if (!supabase || !user) { setProfile(null); return; }
    supabase.from("profiles").select("id,username,full_name,avatar_url,bio,website").eq("id", user.id).maybeSingle()
      .then(({ data }) => { if (data) { setProfile(data as Profile); setSettingsName(data.full_name || ""); setSettingsBio(data.bio || ""); setSettingsWebsite(data.website || ""); } });
  }, [user?.id]);
  const loadFeed = useCallback(async () => {
    if (!supabase || !user) return; setLoading(true);
    const { data, error } = await supabase.from("posts").select("id,user_id,caption,location,created_at,profiles!posts_user_id_fkey(id,username,full_name,avatar_url,bio,website),post_media(id,media_url,media_type),post_likes(user_id),comments(count)").order("created_at", { ascending: false }).limit(40);
    if (error) setNotice(error.message); else setPosts((data || []) as unknown as Post[]); setLoading(false);
  }, [user?.id]);
  useEffect(() => { loadFeed(); }, [loadFeed]);
  useEffect(() => {
    if (!supabase || !user) return;
    supabase.from("saved_posts").select("post_id").eq("user_id", user.id).then(({ data }) => setSavedIds((data || []).map(x => x.post_id)));
  }, [user?.id]);
  const loadStories = useCallback(async () => {
    if (!supabase || !user) return;
    const { data, error } = await supabase.from("stories")
      .select("id,user_id,media_url,media_type,expires_at,profiles!stories_user_id_fkey(id,username,full_name,avatar_url,bio,website)")
      .gt("expires_at", new Date().toISOString()).order("created_at", { ascending: true }).limit(100);
    if (error) setNotice(error.message); else setStories((data || []) as unknown as Story[]);
  }, [user?.id]);
  useEffect(() => { loadStories(); }, [loadStories]);

  const openProfile = async (person: Profile) => {
    if (!supabase) return; setTargetProfile(person); setScreen("profile"); setTargetPosts([]);
    const [postResult, followerResult, followingResult, followResult] = await Promise.all([
      supabase.from("posts").select("id,user_id,caption,location,created_at,profiles!posts_user_id_fkey(id,username,full_name,avatar_url),post_media(id,media_url,media_type),post_likes(user_id),comments(count)").eq("user_id", person.id).order("created_at", { ascending: false }),
      supabase.from("follows").select("follower_id", { count: "exact", head: true }).eq("following_id", person.id),
      supabase.from("follows").select("following_id", { count: "exact", head: true }).eq("follower_id", person.id),
      user && user.id !== person.id ? supabase.from("follows").select("follower_id").eq("follower_id", user.id).eq("following_id", person.id).maybeSingle() : Promise.resolve({ data: null } as any)
    ]);
    setTargetPosts((postResult.data || []) as unknown as Post[]); setFollowers(followerResult.count || 0); setFollowing(followingResult.count || 0); setIsFollowing(Boolean(followResult.data));
  };
  const searchPeople = async (value: string) => {
    setQuery(value); if (!supabase || value.trim().length < 1) { setPeople([]); return; }
    const { data } = await supabase.from("profiles").select("id,username,full_name,avatar_url,bio,website").ilike("username", "%" + value.trim() + "%").limit(25); setPeople((data || []) as Profile[]);
  };
  const toggleLike = async (post: Post) => {
    if (!supabase || !user) return; const already = post.post_likes.some(x => x.user_id === user.id);
    const mutate = (items: Post[]) => items.map(p => p.id === post.id ? { ...p, post_likes: already ? p.post_likes.filter(x => x.user_id !== user.id) : [...p.post_likes, { user_id: user.id }] } : p);
    setPosts(mutate); setTargetPosts(mutate);
    const result = already ? await supabase.from("post_likes").delete().eq("post_id", post.id).eq("user_id", user.id) : await supabase.from("post_likes").insert({ post_id: post.id, user_id: user.id });
    if (result.error) { setNotice(result.error.message); loadFeed(); }
  };
  const toggleSave = async (post: Post) => {
    if (!supabase || !user) return; const already = savedIds.includes(post.id); setSavedIds(v => already ? v.filter(x => x !== post.id) : [...v, post.id]);
    const result = already ? await supabase.from("saved_posts").delete().eq("post_id", post.id).eq("user_id", user.id) : await supabase.from("saved_posts").insert({ post_id: post.id, user_id: user.id });
    if (result.error) setNotice(result.error.message);
  };
  const toggleFollow = async (person: Profile) => {
    if (!supabase || !user) return; const next = !isFollowing; setIsFollowing(next); setFollowers(v => v + (next ? 1 : -1));
    const result = next ? await supabase.from("follows").insert({ follower_id: user.id, following_id: person.id }) : await supabase.from("follows").delete().eq("follower_id", user.id).eq("following_id", person.id);
    if (result.error) { setNotice(result.error.message); setIsFollowing(!next); setFollowers(v => v + (next ? -1 : 1)); }
  };
  const publishPost = async (event: FormEvent) => {
    event.preventDefault(); if (!supabase || !user) return; if (!caption.trim() && !file) { setNotice("Add a photo or write something first."); return; }
    setPublishing(true); setNotice("");
    try {
      const { data: post, error } = await supabase.from("posts").insert({ user_id: user.id, caption: caption.trim(), location: location.trim() || null }).select("id").single();
      if (error) throw error;
      if (file) {
        const extension = file.name.split(".").pop()?.toLowerCase() || "jpg"; const path = user.id + "/" + crypto.randomUUID() + "." + extension;
        const { error: uploadError } = await supabase.storage.from("post-media").upload(path, file, { contentType: file.type, upsert: false });
        if (uploadError) { await supabase.from("posts").delete().eq("id", post.id); throw uploadError; }
        const mediaUrl = supabase.storage.from("post-media").getPublicUrl(path).data.publicUrl;
        const { error: mediaError } = await supabase.from("post_media").insert({ post_id: post.id, user_id: user.id, storage_path: path, media_url: mediaUrl, media_type: file.type.startsWith("video/") ? "video" : "image" });
        if (mediaError) { await supabase.from("posts").delete().eq("id", post.id); throw mediaError; }
      }
      setCaption(""); setLocation(""); setFile(null); setComposerOpen(false); await loadFeed();
    } catch (e) { setNotice(e instanceof Error ? e.message : "Post could not be published."); } finally { setPublishing(false); }
  };
  const publishStory = async (event: FormEvent) => {
    event.preventDefault();
    if (!supabase || !user || !storyFile) return;
    setPublishingStory(true); setNotice("");
    try {
      const extension = storyFile.name.split(".").pop()?.toLowerCase() || "jpg";
      const path = user.id + "/" + crypto.randomUUID() + "." + extension;
      const { error: uploadError } = await supabase.storage.from("post-media").upload(path, storyFile, { contentType: storyFile.type, upsert: false });
      if (uploadError) throw uploadError;
      const mediaUrl = supabase.storage.from("post-media").getPublicUrl(path).data.publicUrl;
      const { error } = await supabase.from("stories").insert({
        user_id: user.id, storage_path: path, media_url: mediaUrl, media_type: storyFile.type.startsWith('video/') ? 'video' : 'image'
      });
      if (error) throw error;
      setStoryFile(null); setStoryComposerOpen(false); await loadStories();
    } catch (e) { setNotice(e instanceof Error ? e.message : "Story could not be published."); }
    finally { setPublishingStory(false); }
  };
  const openComments = async (post: Post) => {
    setCommentPost(post); setComments([]); setCommentText(""); if (!supabase) return;
    const { data, error } = await supabase.from("comments").select("id,content,created_at,profiles!comments_user_id_fkey(id,username,full_name,avatar_url)").eq("post_id", post.id).order("created_at", { ascending: true }).limit(100);
    if (error) setNotice(error.message); else setComments((data || []) as unknown as Comment[]);
  };
  const sendComment = async (event: FormEvent) => {
    event.preventDefault(); if (!supabase || !user || !commentPost || !commentText.trim()) return;
    const { error } = await supabase.from("comments").insert({ post_id: commentPost.id, user_id: user.id, content: commentText.trim() });
    if (error) setNotice(error.message); else { setCommentText(""); await openComments(commentPost); await loadFeed(); }
  };
  const loadMessages = async (conversation: any) => {
    if (!supabase) return; setActiveConversation(conversation);
    const { data } = await supabase.from("messages").select("id,body,sender_id,created_at").eq("conversation_id", conversation.id).order("created_at", { ascending: true }).limit(200); setMessages(data || []);
  };
  const loadConversations = async () => {
    if (!supabase || !user) return;
    const { data } = await supabase.from("conversation_members").select("conversation_id,conversations(id,updated_at,conversation_members(user_id,profiles!conversation_members_user_id_fkey(id,username,avatar_url)))").eq("user_id", user.id);
    setConversations((data || []).map((row: any) => { const conv = row.conversations; const other = conv?.conversation_members?.find((m: any) => m.user_id !== user.id)?.profiles; return conv ? { ...conv, other: other || { username: "Conversation" } } : null; }).filter(Boolean));
  };
  const startMessage = async (person: Profile) => {
    if (!supabase) return; const { data, error } = await supabase.rpc("start_conversation", { other_user_id: person.id });
    if (error) { setNotice(error.message); return; } const conv = { id: data, other: person };
    setScreen("messages"); setConversations(old => [conv, ...old.filter(c => c.id !== data)]); await loadMessages(conv);
  };
  const sendMessage = async (event: FormEvent) => {
    event.preventDefault(); if (!supabase || !user || !activeConversation || !messageText.trim()) return; const body = messageText.trim(); setMessageText("");
    const { data, error } = await supabase.from("messages").insert({ conversation_id: activeConversation.id, sender_id: user.id, body }).select("id,body,sender_id,created_at").single();
    if (error) setNotice(error.message); else setMessages(old => [...old, data]);
  };
  const loadNotifications = async () => {
    if (!supabase || !user) return;
    const { data } = await supabase.from("notifications").select("id,kind,entity_id,is_read,created_at,profiles!notifications_actor_id_fkey(id,username,avatar_url)").eq("recipient_id", user.id).order("created_at", { ascending: false }).limit(60);
    setNotifications(data || []); await supabase.from("notifications").update({ is_read: true }).eq("recipient_id", user.id).eq("is_read", false);
  };
  const saveProfile = async (event: FormEvent) => {
    event.preventDefault(); if (!supabase || !user) return; setSavingProfile(true);
    const { error } = await supabase.from("profiles").update({ full_name: settingsName.trim(), bio: settingsBio.trim(), website: settingsWebsite.trim() }).eq("id", user.id);
    if (error) setNotice(error.message); else setProfile(p => p ? { ...p, full_name: settingsName, bio: settingsBio, website: settingsWebsite } : p); setSavingProfile(false);
  };
  const go = (next: Screen) => {
    setScreen(next); setTargetProfile(null); setActiveConversation(null);
    if (next === "messages") loadConversations(); if (next === "notifications") loadNotifications();
    if (next === "profile" && profile) openProfile(profile);
  };
  const ownProfile: Profile = profile || { id: user?.id || "", username: user?.email?.split("@")[0] || "you", full_name: "", avatar_url: null, bio: null, website: null };
  const displayedProfile = targetProfile || (screen === "profile" ? ownProfile : null);
  const visibleSavedPosts = useMemo(() => posts.filter(p => savedIds.includes(p.id)), [posts, savedIds]);

  if (!isSupabaseConfigured) return <><AuthScreen/><div className="config-footer">Nexus needs your Supabase project URL and anon key before sign-in can work.</div></>;
  if (!session || !user) return <AuthScreen/>;
  const navItems = [
    { key: "home" as Screen, label: "Home", icon: Home }, { key: "explore" as Screen, label: "Explore", icon: Compass },
    { key: "messages" as Screen, label: "Messages", icon: Send }, { key: "notifications" as Screen, label: "Notifications", icon: Heart },
    { key: "saved" as Screen, label: "Saved", icon: Bookmark }, { key: "profile" as Screen, label: "Profile", icon: UserRound }
  ];
  return <div className="app-shell">
    <aside className="sidebar"><button className="brand sidebar-brand" onClick={() => go("home")}>nexus</button><nav className="primary-nav">
      {navItems.map(item => <button key={item.key} className={"nav-item " + (screen === item.key ? "active" : "")} onClick={() => go(item.key)}><item.icon size={23}/><span>{item.label}</span></button>)}
      <button className="nav-item" onClick={() => setComposerOpen(true)}><PlusSquare size={23}/><span>Create</span></button>
    </nav><div className="sidebar-bottom"><button className="nav-item" onClick={() => go("settings")}><Settings size={22}/><span>Settings</span></button><button className="nav-item" onClick={() => supabase?.auth.signOut()}><LogOut size={22}/><span>Log out</span></button></div></aside>
    <main className="main-column">
      <div className="mobile-topbar"><button className="brand" onClick={() => go("home")}>nexus</button><div><button className="icon-button" onClick={() => go("notifications")}><Heart/></button><button className="icon-button" onClick={() => go("messages")}><Send/></button></div></div>
      {screen === "home" && <>
        <div className="stories-strip"><button className="story" onClick={() => setStoryComposerOpen(true)}><span className="story-ring own"><Avatar profile={profile} size={58}/><i>+</i></span><span>Your story</span></button>
          {Array.from(new Map(stories.map(story => [story.user_id, story])).values()).filter(story => story.user_id !== user.id).map(story => <button className="story" key={story.user_id} onClick={() => setActiveStory(story)}><span className="story-ring"><Avatar profile={story.profiles} size={58}/></span><span>{story.profiles?.username}</span></button>)}
          {stories.filter(story => story.user_id === user.id).slice(-1).map(story => <button className="story" key={story.id} onClick={() => setActiveStory(story)}><span className="story-ring"><Avatar profile={profile} size={58}/></span><span>Your story</span></button>)}
        </div>
        <section className="feed-list">{loading && posts.length === 0 && <div className="loading-state"><LoaderCircle className="spin"/> Loading your feed</div>}
          {!loading && posts.length === 0 && <div className="empty-state"><Camera size={34}/><h2>Your feed starts here</h2><p>Follow people or share your first post to get things moving.</p><button className="primary-button" onClick={() => setComposerOpen(true)}>Create your first post</button></div>}
          {posts.map(post => <PostCard key={post.id} post={post} currentUser={user} onLike={toggleLike} onComment={openComments} onProfile={openProfile} onSave={toggleSave} saved={savedIds.includes(post.id)}/>)}
        </section>
      </>}
      {screen === "explore" && <section className="page-content"><h1>Explore</h1><div className="search-field"><Search/><input value={query} onChange={e => searchPeople(e.target.value)} placeholder="Search people"/></div>
        {query && <div className="people-results">{people.map(person => <button className="person-row" key={person.id} onClick={() => openProfile(person)}><Avatar profile={person}/><span><strong>{person.username}</strong><small>{person.full_name}</small></span><ChevronDown size={16}/></button>)}{people.length === 0 && <p className="muted">No accounts found.</p>}</div>}
        <div className="explore-grid">{posts.filter(p => p.post_media?.[0]?.media_url).map(post => <button key={post.id} onClick={() => openComments(post)}><img src={post.post_media[0].media_url} alt={post.caption}/></button>)}</div>
      </section>}
      {screen === "messages" && <section className="page-content messages-page"><h1>Messages</h1><div className="messages-layout"><div className="conversation-list"><div className="conversation-search"><strong>{profile?.username}</strong><button className="icon-button" onClick={() => go("explore")}><PlusSquare/></button></div>
        {conversations.map(c => <button className={"conversation-row " + (activeConversation?.id === c.id ? "selected" : "")} key={c.id} onClick={() => loadMessages(c)}><Avatar profile={c.other}/><span><strong>{c.other?.username}</strong><small>Open conversation</small></span></button>)}
        {conversations.length === 0 && <p className="muted empty-inbox">Search for someone to start a conversation.</p>}</div>
        <div className="chat-panel">{activeConversation ? <><div className="chat-heading"><Avatar profile={activeConversation.other}/><strong>{activeConversation.other?.username}</strong></div><div className="chat-messages">{messages.map(m => <div key={m.id} className={"message-bubble " + (m.sender_id === user.id ? "mine" : "")}>{m.body}</div>)}</div><form className="message-form" onSubmit={sendMessage}><input value={messageText} onChange={e => setMessageText(e.target.value)} placeholder="Message…"/><button className="send-button" disabled={!messageText.trim()}><Send size={18}/></button></form></> : <div className="chat-empty"><Send size={42}/><h2>Your messages</h2><p>Send a message to start a conversation.</p><button className="primary-button" onClick={() => go("explore")}>New message</button></div>}</div>
      </div></section>}
      {screen === "notifications" && <section className="page-content"><h1>Notifications</h1><div className="notification-list">{notifications.map(n => <div className="notification-row" key={n.id}><Avatar profile={n.profiles}/><span><strong>{n.profiles?.username || "Someone"}</strong> {n.kind === "like" ? "liked your post" : n.kind === "comment" ? "commented on your post" : n.kind === "follow" ? "started following you" : "interacted with you"} <small>{dateLabel(n.created_at)}</small></span>{n.kind === "like" && <Heart size={18}/>}</div>)}{notifications.length === 0 && <p className="muted">You're all caught up.</p>}</div></section>}
      {screen === "profile" && displayedProfile && <section className="page-content profile-page"><div className="profile-heading"><Avatar profile={displayedProfile} size={92}/><div className="profile-summary"><div className="profile-title"><h1>{displayedProfile.username}</h1>{displayedProfile.id === user.id ? <button className="secondary-button" onClick={() => go("settings")}>Edit profile</button> : <><button className="primary-button compact" onClick={() => toggleFollow(displayedProfile)}>{isFollowing ? "Following" : "Follow"}</button><button className="secondary-button" onClick={() => startMessage(displayedProfile)}>Message</button></>}</div>
        <div className="profile-stats"><span><b>{displayedProfile.id === user.id ? posts.filter(p => p.user_id === user.id).length : targetPosts.length}</b> posts</span><span><b>{followers}</b> followers</span><span><b>{following}</b> following</span></div><strong>{displayedProfile.full_name}</strong><p>{displayedProfile.bio}</p>{displayedProfile.website && <a href={displayedProfile.website} target="_blank" rel="noreferrer">{displayedProfile.website}</a>}</div></div>
        <div className="profile-tabs"><span><ImagePlus size={17}/> POSTS</span></div><div className="profile-grid">{(displayedProfile.id === user.id ? posts.filter(p => p.user_id === user.id) : targetPosts).map(post => post.post_media?.[0] ? <button key={post.id} onClick={() => openComments(post)}><img src={post.post_media[0].media_url} alt={post.caption}/></button> : <div key={post.id} className="profile-text-post">{post.caption}</div>)}</div>
      </section>}
      {screen === "saved" && <section className="page-content"><h1>Saved</h1><div className="profile-grid">{visibleSavedPosts.map(post => post.post_media?.[0] && <button key={post.id} onClick={() => openComments(post)}><img src={post.post_media[0].media_url} alt={post.caption}/></button>)}</div>{savedIds.length === 0 && <p className="muted">Posts you save will appear here.</p>}</section>}
      {screen === "settings" && <section className="page-content settings-page"><h1>Edit profile</h1><form onSubmit={saveProfile}><label>Full name<input value={settingsName} onChange={e => setSettingsName(e.target.value)} maxLength={60}/></label><label>Bio<textarea value={settingsBio} onChange={e => setSettingsBio(e.target.value)} maxLength={150}/></label><label>Website<input value={settingsWebsite} onChange={e => setSettingsWebsite(e.target.value)} placeholder="https://"/></label><button className="primary-button" disabled={savingProfile}>{savingProfile ? "Saving…" : "Save changes"}</button></form><button className="secondary-button logout-mobile" onClick={() => supabase?.auth.signOut()}>Log out</button></section>}
      {notice && <button className="toast" onClick={() => setNotice("")}>{notice}<X size={14}/></button>}
    </main>
    <aside className="right-rail"><div className="account-switch"><Avatar profile={profile} size={44}/><div><strong>{profile?.username}</strong><small>{profile?.full_name || user.email}</small></div><button onClick={() => go("profile")}>Profile</button></div>
      <div className="suggestion-head"><strong>Suggested for you</strong><button onClick={() => go("explore")}>See all</button></div><p className="right-hint">Discover accounts and people to follow on Nexus.</p><div className="right-links">About · Help · Privacy · Terms · Locations · Language<br/><br/>© 2026 NEXUS</div>
    </aside>
    <nav className="mobile-nav">{navItems.filter(n => ["home","explore","messages","notifications","profile"].includes(n.key)).map(item => <button key={item.key} className={screen === item.key ? "active" : ""} onClick={() => go(item.key)}><item.icon/></button>)}</nav>
    {composerOpen && <Modal title="Create new post" onClose={() => setComposerOpen(false)}><form className="composer-form" onSubmit={publishPost}>
      <label className="upload-area">{file ? <img src={URL.createObjectURL(file)} alt="Preview"/> : <><ImagePlus size={40}/><strong>Choose a photo or video</strong><span>JPG, PNG, GIF or MP4</span></>}<input type="file" accept="image/*,video/mp4" onChange={e => setFile(e.target.files?.[0] || null)}/></label>
      <textarea value={caption} onChange={e => setCaption(e.target.value)} placeholder="Write a caption…" maxLength={2200}/><input value={location} onChange={e => setLocation(e.target.value)} placeholder="Add location (optional)"/>
      <button className="primary-button" disabled={publishing}>{publishing ? "Sharing…" : "Share"}</button>
    </form></Modal>}
    {storyComposerOpen && <Modal title="Add to your story" onClose={() => { setStoryComposerOpen(false); setStoryFile(null); }}><form className="composer-form" onSubmit={publishStory}>
      <label className="upload-area">{storyFile ? <>{storyFile.type.startsWith("video/") ? <video src={URL.createObjectURL(storyFile)} controls playsInline/> : <img src={URL.createObjectURL(storyFile)} alt="Story preview"/>}</> : <><ImagePlus size={40}/><strong>Choose a photo or video</strong><span>Stories disappear after 24 hours</span></>}<input type="file" accept="image/*,video/mp4" onChange={e => setStoryFile(e.target.files?.[0] || null)}/></label>
      <button className="primary-button" disabled={!storyFile || publishingStory}>{publishingStory ? "Sharing…" : "Share to story"}</button>
    </form></Modal>}
    {activeStory && <Modal title={activeStory.profiles?.username || "Story"} onClose={() => setActiveStory(null)}><div className="story-viewer">{activeStory.media_type === "video" ? <video src={activeStory.media_url} controls autoPlay playsInline/> : <img src={activeStory.media_url} alt="Story"/>}</div></Modal>}
    {commentPost && <Modal title="Comments" onClose={() => setCommentPost(null)}><div className="comments-list">{comments.map(c => <div className="comment-row" key={c.id}><Avatar profile={c.profiles} size={30}/><p><strong>{c.profiles?.username}</strong> {c.content}<small>{dateLabel(c.created_at)}</small></p></div>)}{comments.length === 0 && <p className="muted">Be the first to comment.</p>}</div>
      <form className="comment-form" onSubmit={sendComment}><input value={commentText} onChange={e => setCommentText(e.target.value)} placeholder="Add a comment…"/><button disabled={!commentText.trim()}>Post</button></form>
    </Modal>}
  </div>;
}
