import { useEffect, useState } from "react";
import { Heart, Home, MessageCircle, PlusSquare, Search, Compass, UserRound, Send, Bookmark, MoreHorizontal, ImagePlus } from "lucide-react";
import { supabase, isSupabaseConfigured } from "./lib/supabase";

type Post = { id: string; username: string; avatar: string; image: string; caption: string; likes: number; location?: string };
const demoPosts: Post[] = [
  { id:"1", username:"mila.jpg", avatar:"https://i.pravatar.cc/100?img=47", image:"https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1100", caption:"Somewhere between the mountains and the sky.", likes:1284, location:"Dolomites, Italy" },
  { id:"2", username:"noahcreates", avatar:"https://i.pravatar.cc/100?img=12", image:"https://images.unsplash.com/photo-1493246507139-91e8fad9978e?w=1100", caption:"A slower kind of morning.", likes:862, location:"Lake house" },
  { id:"3", username:"daily.frames", avatar:"https://i.pravatar.cc/100?img=32", image:"https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=1100", caption:"Blue on blue.", likes:2401 }
];
const stories = ["Your story","mila.jpg","noahcreates","daily.frames","sophia","alex.jpg","theo.png"];

export default function App() {
  const [posts, setPosts] = useState(demoPosts);
  const [liked, setLiked] = useState<string[]>([]);
  const [saved, setSaved] = useState<string[]>([]);
  const [activeTab, setActiveTab] = useState("Home");
  const [caption, setCaption] = useState("");
  const [notice, setNotice] = useState("");
  useEffect(() => {
    if (!supabase) return;
    supabase.from("posts").select("id, caption, created_at, profiles(username, avatar_url), post_media(url), likes(count)").order("created_at", { ascending:false }).limit(20)
      .then(({ data, error }) => {
        if (error || !data?.length) return;
        setPosts(data.map((p: any) => ({ id:p.id, username:p.profiles?.username ?? "user", avatar:p.profiles?.avatar_url ?? "https://i.pravatar.cc/100", image:p.post_media?.[0]?.url ?? "", caption:p.caption ?? "", likes:p.likes?.[0]?.count ?? 0 })));
      });
  }, []);
  const toggleLike = (id: string) => setLiked(v => v.includes(id) ? v.filter(x => x !== id) : [...v,id]);
  const createPost = async () => {
    if (!caption.trim()) return;
    if (!supabase) { setNotice("Connect Supabase to publish posts."); return; }
    const { data: { user } } = await supabase.auth.getUser();
    if (!user) { setNotice("Sign in to publish a post."); return; }
    const { error } = await supabase.from("posts").insert({ user_id:user.id, caption:caption.trim() });
    setNotice(error ? error.message : "Post published.");
    if (!error) setCaption("");
  };
  return <div className="app-shell">
    <aside className="sidebar">
      <div className="wordmark">nexus</div>
      <nav>{[[Home,"Home"],[Search,"Search"],[Compass,"Explore"],[MessageCircle,"Messages"],[Heart,"Notifications"],[PlusSquare,"Create"],[UserRound,"Profile"]].map(([Icon,label]:any) => <button className={activeTab===label?"nav-item active":"nav-item"} onClick={()=>setActiveTab(label)} key={label}><Icon size={24}/><span>{label}</span></button>)}</nav>
      <button className="nav-item more"><MoreHorizontal size={24}/><span>More</span></button>
    </aside>
    <main className="feed-column">
      <header className="mobile-header"><span className="wordmark">nexus</span><Heart/><MessageCircle/></header>
      <section className="stories">{stories.map((s,i)=><button className="story" key={s}><span className={i===0?"story-ring own":"story-ring"}><img src={`https://i.pravatar.cc/100?img=${[5,47,12,32,44,15,21][i]}`} /></span><span>{s}</span></button>)}</section>
      <section className="posts">{posts.map(post=><article className="post" key={post.id}>
        <div className="post-head"><img className="avatar" src={post.avatar}/><div className="post-user"><strong>{post.username}</strong>{post.location&&<small>{post.location}</small>}</div><button className="icon-button"><MoreHorizontal/></button></div>
        <img className="post-image" src={post.image} />
        <div className="post-actions"><div><button className={liked.includes(post.id)?"icon-button liked":"icon-button"} onClick={()=>toggleLike(post.id)}><Heart fill={liked.includes(post.id)?"currentColor":"none"}/></button><button className="icon-button"><MessageCircle/></button><button className="icon-button"><Send/></button></div><button className="icon-button" onClick={()=>setSaved(v=>v.includes(post.id)?v.filter(x=>x!==post.id):[...v,post.id])}><Bookmark fill={saved.includes(post.id)?"currentColor":"none"}/></button></div>
        <div className="post-meta"><strong>{post.likes+(liked.includes(post.id)?1:0).toLocaleString()} likes</strong><p><strong>{post.username}</strong> {post.caption}</p><button className="comments">View all comments</button></div>
      </article>)}</section>
      {notice&&<p className="notice">{notice}</p>}
    </main>
    <aside className="right-rail"><div className="account-row"><img src="https://i.pravatar.cc/100?img=5"/><div><strong>you</strong><small>Your account</small></div><button>Switch</button></div><div className="suggestion-heading"><strong>Suggested for you</strong><span>See All</span></div>{["sophia","alex.jpg","theo.png","jules"].map((u,i)=><div className="suggestion" key={u}><img src={`https://i.pravatar.cc/100?img=${[44,15,21,36][i]}`}/><div><strong>{u}</strong><small>Suggested for you</small></div><button>Follow</button></div>)}<div className="footer-links">About · Help · Press · API · Jobs · Privacy · Terms<br/><br/>© 2026 NEXUS</div>
      <div className="composer"><strong>Create a post</strong><textarea value={caption} onChange={e=>setCaption(e.target.value)} placeholder="Write a caption..." /><button onClick={createPost}><ImagePlus size={16}/> Publish</button></div>
      {!isSupabaseConfigured&&<p className="setup-hint">Demo mode · Add Supabase environment variables to enable live data.</p>}
    </aside>
    <nav className="bottom-nav"><Home/><Search/><PlusSquare/><MessageCircle/><UserRound/></nav>
  </div>;
}