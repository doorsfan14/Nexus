package app.nexus.social

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Locale

private val Ink = Color(0xFF171717)
private val Blue = Color(0xFF0095F6)
private val Soft = Color(0xFFF5F5F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexusApp(NexusApi(applicationContext)) }
    }
}

@Composable
private fun NexusApp(api: NexusApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(api.isSignedIn) }
    var profile by remember { mutableStateOf<NexusProfile?>(null) }
    var feed by remember { mutableStateOf(emptyList<NexusPost>()) }
    var stories by remember { mutableStateOf(emptyList<NexusStory>()) }
    var activeStory by remember { mutableStateOf<NexusStory?>(null) }
    var createStoryOpen by remember { mutableStateOf(false) }
    var selectedStoryUri by remember { mutableStateOf<Uri?>(null) }
    var savedPosts by remember { mutableStateOf(emptyList<NexusPost>()) }
    var savedIds by remember { mutableStateOf(setOf<String>()) }
    var followerCount by remember { mutableStateOf(0) }
    var followingCount by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf("Home") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf(emptyList<NexusProfile>()) }
    var viewedProfile by remember { mutableStateOf<NexusProfile?>(null) }
    var viewedPosts by remember { mutableStateOf(emptyList<NexusPost>()) }
    var following by remember { mutableStateOf(false) }
    var createOpen by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var commentsPost by remember { mutableStateOf<NexusPost?>(null) }
    var comments by remember { mutableStateOf(emptyList<NexusComment>()) }
    var commentDraft by remember { mutableStateOf("") }
    var conversations by remember { mutableStateOf(emptyList<NexusConversation>()) }
    var activeConversation by remember { mutableStateOf<NexusConversation?>(null) }
    var messages by remember { mutableStateOf(emptyList<NexusMessage>()) }
    var messageDraft by remember { mutableStateOf("") }
    var notifications by remember { mutableStateOf(emptyList<String>()) }
    var profileEditOpen by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editUsername by remember { mutableStateOf("") }
    var deviceRows by remember { mutableStateOf(emptyList<NexusDevice>()) }
    var securityOpen by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedImage = it }
    val storyPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedStoryUri = it }

    fun report(t: Throwable) { error = t.message ?: "Something went wrong." }
    fun refreshFeed() {
        scope.launch {
            loading = true
            try { feed = api.feed() } catch (t: Throwable) { report(t) }
            loading = false
        }
    }
    fun openComments(post: NexusPost) {
        commentsPost = post
        scope.launch { try { comments = api.comments(post.id) } catch (t: Throwable) { report(t) } }
    }
    fun openConversation(conversation: NexusConversation) {
        activeConversation = conversation
        scope.launch { try { messages = api.messages(conversation.id) } catch (t: Throwable) { report(t) } }
    }
    fun openProfile(person: NexusProfile) {
        viewedProfile = person
        screen = "Profile"
        scope.launch {
            try {
                viewedPosts = api.postsForUser(person.id)
                val counts = api.profileCounts(person.id)
                followerCount = counts.first
                followingCount = counts.second
                following = if (person.id == api.userId) false else api.followStatus(person.id)
            } catch (t: Throwable) { report(t) }
        }
    }

    LaunchedEffect(signedIn) {
        if (signedIn) {
            try {
                profile = api.profile()
                editName = profile?.fullName.orEmpty()
                editUsername = profile?.username.orEmpty()
                api.registerDevice()
                editBio = profile?.bio.orEmpty()
                feed = api.feed()
                savedPosts = api.savedPosts()
                savedIds = savedPosts.map { it.id }.toSet()
                stories = api.stories()
            } catch (t: Throwable) { report(t) }
        } else { profile = null; feed = emptyList() }
    }
    LaunchedEffect(signedIn) {
        while (signedIn) {
            delay(30000)
            try { if (api.isDeviceRevoked()) { api.signOut(); signedIn = false } else api.registerDevice() } catch (_: Throwable) {}
        }
    }
    LaunchedEffect(screen, signedIn) {
        if (!signedIn) return@LaunchedEffect
        if (screen == "Messages") try { conversations = api.conversations() } catch (t: Throwable) { report(t) }
        if (screen == "Notifications") try { notifications = api.notifications() } catch (t: Throwable) { report(t) }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White, background = Color.White, surface = Color.White),
        typography = Typography()
    ) {
        if (!api.isConfigured) {
            ConfigurationScreen()
        } else if (!signedIn) {
            AuthScreen(api, { signedIn = true }, { error = it })
        } else {
            Scaffold(
                containerColor = Color.White,
                topBar = {
                    Box(Modifier.fillMaxWidth().height(54.dp).background(Color.White)) {
                        Row(Modifier.widthIn(max = 700.dp).fillMaxWidth().align(Alignment.Center).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("nexus", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp, color = Ink, modifier = Modifier.weight(1f))
                            IconButton(onClick = { screen = "Notifications" }) { Icon(Icons.Default.FavoriteBorder, "Notifications", tint = Ink) }
                            IconButton(onClick = { screen = "Messages"; activeConversation = null }) { Icon(Icons.Default.Send, "Messages", tint = Ink) }
                        }
                    }
                },
                bottomBar = {
                    Box(Modifier.fillMaxWidth().background(Color.White)) {
                        NavigationBar(Modifier.widthIn(max = 700.dp).fillMaxWidth().align(Alignment.Center), containerColor = Color.White, tonalElevation = 0.dp) {
                            val items = listOf(Icons.Default.Home to "Home", Icons.Default.Search to "Explore", Icons.Default.AddBox to "Create", Icons.Default.FavoriteBorder to "Notifications", Icons.Default.PersonOutline to "Profile")
                            items.forEach { pair ->
                                val icon = pair.first
                                val label = pair.second
                                NavigationBarItem(
                                    selected = screen == label,
                                    onClick = { if (label == "Create") createOpen = true else { screen = label; viewedProfile = null; activeConversation = null } },
                                    icon = { Icon(icon, label) }, label = null,
                                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, unselectedIconColor = Ink, indicatorColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                    val contentMaxWidth = if (maxWidth >= 600.dp) 700.dp else maxWidth
                    Box(Modifier.widthIn(max = contentMaxWidth).fillMaxSize().align(Alignment.TopCenter)) {
                        when (screen) {
                    "Home" -> FeedScreen(feed, api.userId, loading, onRefresh = { refreshFeed() }, onLike = { post ->
                        val liked = post.likes.contains(api.userId)
                        feed = feed.map { if (it.id == post.id) it.copy(likes = if (liked) it.likes - api.userId else it.likes + api.userId) else it }
                        scope.launch { try { api.toggleLike(post.id, liked) } catch (t: Throwable) { report(t); refreshFeed() } }
                    }, onComment = { openComments(it) }, onProfile = { openProfile(it) }, onSave = { post ->
                        val already = savedIds.contains(post.id)
                        savedIds = if (already) savedIds - post.id else savedIds + post.id
                        savedPosts = if (already) savedPosts.filterNot { it.id == post.id } else savedPosts + post
                        scope.launch { try { api.toggleSaved(post.id, already) } catch (t: Throwable) { report(t) } }
                    }, savedIds = savedIds, modifier = Modifier.fillMaxSize(), stories = stories, onCreateStory = { selectedStoryUri = null; createStoryOpen = true }, onOpenStory = { activeStory = it })
                    "Explore" -> ExploreScreen(query, people, onQuery = { value ->
                        query = value
                        scope.launch { try { people = if (value.isBlank()) emptyList() else api.searchPeople(value) } catch (t: Throwable) { report(t) } }
                    }, onProfile = { openProfile(it) }, modifier = Modifier.fillMaxSize())
                    "Messages" -> MessagesScreen(
                        conversations, activeConversation, messages, api.userId, messageDraft, { messageDraft = it },
                        { openConversation(it) }, { activeConversation = null },
                        {
                            val active = activeConversation
                            if (active != null && messageDraft.isNotBlank()) {
                                val body = messageDraft.trim(); messageDraft = ""
                                scope.launch { try { api.sendMessage(active.id, body); messages = api.messages(active.id) } catch (t: Throwable) { report(t) } }
                            }
                        }, Modifier.fillMaxSize()
                    )
                    "Notifications" -> SimpleListScreen("Notifications", notifications, Modifier.fillMaxSize())
                    "Saved" -> FeedScreen(savedPosts, api.userId, false, onRefresh = {
                        scope.launch { try { savedPosts = api.savedPosts(); savedIds = savedPosts.map { it.id }.toSet() } catch (t: Throwable) { report(t) } }
                    }, onLike = { post ->
                        val liked = post.likes.contains(api.userId)
                        savedPosts = savedPosts.map { if (it.id == post.id) it.copy(likes = if (liked) it.likes - api.userId else it.likes + api.userId) else it }
                        scope.launch { try { api.toggleLike(post.id, liked) } catch (t: Throwable) { report(t) } }
                    }, onComment = { openComments(it) }, onProfile = { openProfile(it) }, onSave = { post ->
                        val already = savedIds.contains(post.id)
                        savedIds = if (already) savedIds - post.id else savedIds + post.id
                        savedPosts = if (already) savedPosts.filterNot { it.id == post.id } else savedPosts + post
                        scope.launch { try { api.toggleSaved(post.id, already) } catch (t: Throwable) { report(t) } }
                    }, savedIds = savedIds, modifier = Modifier.fillMaxSize())
                    "Profile" -> {
                        val person = viewedProfile ?: profile
                        if (person != null) ProfileScreen(
                            person, if (viewedProfile == null) feed.filter { it.userId == api.userId } else viewedPosts,
                            person.id == api.userId, following, followerCount, followingCount, { profileEditOpen = true }, { screen = "Saved" }, { securityOpen = true; scope.launch { try { deviceRows = api.deviceSessions() } catch (t: Throwable) { report(t) } } },
                            { scope.launch { try { api.follow(person.id, following); following = !following; followerCount += if (following) 1 else -1 } catch (t: Throwable) { report(t) } } },
                            {
                                scope.launch { try {
                                    val id = api.startConversation(person.id)
                                    val conv = NexusConversation(id, person.username, person.avatarUrl)
                                    conversations = listOf(conv) + conversations.filterNot { it.id == id }
                                    screen = "Messages"; openConversation(conv)
                                } catch (t: Throwable) { report(t) } }
                            }, { openComments(it) }, Modifier.fillMaxSize()
                        )
                    }
                    else -> SimpleListScreen(screen, emptyList(), Modifier.fillMaxSize())
                        }
                    }
                }
            }
            if (createOpen) CreatePostDialog(
                selectedImage, { imagePicker.launch("*/*") }, { createOpen = false; selectedImage = null },
                { caption, location ->
                    scope.launch {
                        busy = true
                        try {
                            val bytes = selectedImage?.let { uri -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                            val mime = selectedImage?.let { context.contentResolver.getType(it) } ?: "image/jpeg"
                            if (!mime.startsWith("image/") && !mime.startsWith("video/")) throw IllegalArgumentException("Choose an image or video.")
                            val ext = when { mime.contains("png") -> "png"; mime.contains("webp") -> "webp"; mime.contains("gif") -> "gif"; mime.startsWith("video/") -> "mp4"; else -> "jpg" }
                            api.publishPost(caption, location, bytes, mime, ext)
                            createOpen = false; selectedImage = null; refreshFeed()
                        } catch (t: Throwable) { report(t) }
                        busy = false
                    }
                }, busy
            )
            if (createStoryOpen) CreateStoryDialog(
                selectedStoryUri, { storyPicker.launch("*/*") }, { createStoryOpen = false; selectedStoryUri = null }, { 
                    val uri = selectedStoryUri
                    if (uri != null) scope.launch {
                        busy = true
                        try {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Unable to read selected media.")
                            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                            val extension = when { mime.contains("png") -> "png"; mime.contains("webp") -> "webp"; mime.startsWith("video/") -> "mp4"; else -> "jpg" }
                            api.publishStory(bytes, mime, extension)
                            stories = api.stories()
                            createStoryOpen = false; selectedStoryUri = null
                        } catch (t: Throwable) { report(t) }
                        busy = false
                    }
                }, busy
            )
            if (activeStory != null) StoryViewerDialog(activeStory!!, { activeStory = null })
            if (commentsPost != null) CommentDialog(
                commentsPost!!, comments, commentDraft, { commentDraft = it }, { commentsPost = null },
                {
                    val post = commentsPost
                    if (post != null && commentDraft.isNotBlank()) {
                        val body = commentDraft.trim(); commentDraft = ""
                        scope.launch { try { api.addComment(post.id, body); comments = api.comments(post.id); refreshFeed() } catch (t: Throwable) { report(t) } }
                    }
                }
            )
            if (profileEditOpen) EditProfileDialog(editUsername, editName, editBio, { editUsername = it }, { editName = it }, { editBio = it }, { profileEditOpen = false }, {
                scope.launch { try { busy = true; api.updateProfile(editUsername.trim().lowercase(Locale.US), editName, editBio); profile = api.profile(); profileEditOpen = false } catch (t: Throwable) { report(t) } finally { busy = false } }
            }, busy)
            if (securityOpen) AccountSecurityDialog(deviceRows, api.currentDeviceId, newPassword, { newPassword = it }, { securityOpen = false }, { id ->
                scope.launch { try { api.revokeDevice(id); deviceRows = api.deviceSessions() } catch (t: Throwable) { report(t) } }
            }, {
                scope.launch { try { busy = true; api.updatePassword(newPassword); newPassword = ""; securityOpen = false } catch (t: Throwable) { report(t) } finally { busy = false } }
            }, busy)
            if (error.isNotBlank()) AlertDialog(onDismissRequest = { error = "" }, title = { Text("Nexus") }, text = { Text(error) }, confirmButton = { TextButton(onClick = { error = "" }) { Text("OK") } })
        }
    }
}

@Composable
private fun ConfigurationScreen() {
    Column(Modifier.fillMaxSize().background(Color.White).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("nexus", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Spacer(Modifier.height(15.dp)); Text("Connect this native client to your Supabase project.", color = Color.Gray)
        Spacer(Modifier.height(18.dp)); Text("Add SUPABASE_URL and SUPABASE_ANON_KEY to android/local.properties, then rebuild.", color = Ink)
    }
}

@Composable
private fun AuthScreen(api: NexusApi, onSignedIn: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var signup by remember { mutableStateOf(false) }; var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("nexus", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp, color = Ink)
        Text("Share what moves you.", color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
        if (signup) OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email address") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, color = Color(0xFF777777), fontSize = 12.sp, modifier = Modifier.padding(10.dp))
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            scope.launch {
                busy = true; message = ""
                try {
                    if (signup) {
                        val authenticated = api.signUp(email.trim(), password, username.trim().lowercase(Locale.US))
                        if (authenticated) onSignedIn() else message = "Account created. Confirm your email, then log in."
                    } else { api.signIn(email.trim(), password); onSignedIn() }
                } catch (t: Throwable) { onError(t.message ?: "Authentication failed.") }
                busy = false
            }
        }, enabled = !busy && email.contains("@") && password.length >= 6 && (!signup || username.length >= 3), modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(7.dp)) {
            Text(if (busy) "Please wait…" else if (signup) "Create account" else "Log in", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { signup = !signup; message = "" }) { Text(if (signup) "Already have an account? Log in" else "Don't have an account? Sign up") }
        if (!signup) TextButton(onClick = {
            scope.launch {
                if (!email.contains("@")) { message = "Enter your email address first."; return@launch }
                busy = true
                try { api.requestPasswordReset(email.trim()); message = "Password reset email sent." }
                catch (t: Throwable) { message = t.message ?: "Could not send reset email." }
                busy = false
            }
        }, enabled = !busy) { Text("Forgot password?") }
    }
}

@Composable
private fun FeedScreen(posts: List<NexusPost>, currentUser: String, loading: Boolean, onRefresh: () -> Unit, onLike: (NexusPost) -> Unit, onComment: (NexusPost) -> Unit, onProfile: (NexusProfile) -> Unit, onSave: (NexusPost) -> Unit, savedIds: Set<String>, modifier: Modifier = Modifier, stories: List<NexusStory> = emptyList(), onCreateStory: () -> Unit = {}, onOpenStory: (NexusStory) -> Unit = {}) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                item { StoryBubble("Your story", null, onCreateStory) }
                items(stories.distinctBy { it.userId }.filter { it.userId != currentUser }) { story -> StoryBubble(story.username, story.avatarUrl) { onOpenStory(story) } }
            }
            HorizontalDivider(color = Color(0xFFEFEFEF))
        }
        if (loading && posts.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(50.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue) } }
        if (!loading && posts.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, null, tint = Color.Gray, modifier = Modifier.size(38.dp))
                Text("Your feed starts here", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
                Text("Follow people or share your first post.", color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        items(posts, key = { it.id }) { post -> AndroidPostCard(post, currentUser, { onLike(post) }, { onComment(post) }, { onProfile(NexusProfile(post.userId, post.username, avatarUrl = post.avatarUrl)) }, { onSave(post) }, savedIds.contains(post.id)) }
    }
}

@Composable
private fun StoryBubble(name: String, avatar: String?, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(62.dp).clip(CircleShape).background(Color(0xFFE65A8D)), contentAlignment = Alignment.Center) {
            if (avatar.isNullOrBlank()) Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFEEEEEE)), contentAlignment = Alignment.Center) { Text("+", fontSize = 28.sp, color = Ink) }
            else AsyncImage(avatar, null, Modifier.size(56.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        }
        Text(name, fontSize = 10.sp, maxLines = 1, color = Ink, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun AndroidPostCard(post: NexusPost, currentUser: String, onLike: () -> Unit, onComment: () -> Unit, onProfile: () -> Unit, onSave: () -> Unit, saved: Boolean) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(post.avatarUrl, 36)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(post.username, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.clickable { onProfile() })
                if (post.location.isNotBlank()) Text(post.location, fontSize = 10.sp, color = Color.DarkGray)
            }
            Icon(Icons.Default.MoreHoriz, contentDescription = "More", tint = Ink)
        }
        if (post.mediaUrl.isNotBlank() && post.mediaType == "video") AndroidView(
            factory = { viewContext -> VideoView(viewContext).apply {
                setVideoURI(Uri.parse(post.mediaUrl))
                setOnPreparedListener { player -> player.isLooping = true; start() }
            } },
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        )
        else if (post.mediaUrl.isNotBlank()) AsyncImage(post.mediaUrl, contentDescription = post.caption, modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Soft).padding(24.dp), contentAlignment = Alignment.Center) { Text(post.caption, color = Ink, fontSize = 22.sp) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            val liked = post.likes.contains(currentUser)
            IconButton(onClick = onLike) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (liked) Color(0xFFED4956) else Ink) }
            IconButton(onClick = onComment) { Icon(Icons.Default.ChatBubbleOutline, "Comment", tint = Ink) }
            IconButton(onClick = {}) { Icon(Icons.Default.Send, "Share", tint = Ink) }
            Spacer(Modifier.weight(1f)); IconButton(onClick = onSave) { Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Save", tint = Ink) }
        }
        Text(post.likes.size.toString() + " likes", Modifier.padding(horizontal = 12.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (post.caption.isNotBlank()) Text(post.username + "  " + post.caption, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 12.sp, color = Ink)
        TextButton(onClick = onComment, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("View all " + post.comments + " comments", color = Color.Gray, fontSize = 12.sp) }
        HorizontalDivider(color = Color(0xFFF0F0F0))
    }
}

@Composable
private fun AvatarImage(url: String, size: Int) {
    if (url.isNotBlank()) AsyncImage(url, null, Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    else Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFE9E9E9)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.Gray) }
}

@Composable
private fun ExploreScreen(query: String, people: List<NexusProfile>, onQuery: (String) -> Unit, onProfile: (NexusProfile) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(14.dp)) {
        Text("Explore", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Ink, modifier = Modifier.padding(bottom = 16.dp))
        OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search people") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        LazyColumn {
            items(people, key = { it.id }) { person ->
                Row(Modifier.fillMaxWidth().clickable { onProfile(person) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(person.avatarUrl, 42)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(person.username, fontWeight = FontWeight.SemiBold); if (person.fullName.isNotBlank()) Text(person.fullName, fontSize = 12.sp, color = Color.Gray) }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                }
                HorizontalDivider(color = Soft)
            }
        }
    }
}

@Composable
private fun ProfileScreen(person: NexusProfile, posts: List<NexusPost>, isOwn: Boolean, following: Boolean, followerCount: Int, followingCount: Int, onEdit: () -> Unit, onSaved: () -> Unit, onSecurity: () -> Unit, onFollow: () -> Unit, onMessage: () -> Unit, onComment: (NexusPost) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(person.avatarUrl, 88)
                    Column(Modifier.weight(1f).padding(start = 24.dp)) {
                        Text(person.username, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Spacer(Modifier.height(13.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Column { Text(posts.size.toString(), fontWeight = FontWeight.Bold); Text("posts", fontSize = 11.sp) }
                            Column { Text(followerCount.toString(), fontWeight = FontWeight.Bold); Text("followers", fontSize = 11.sp) }
                            Column { Text(followingCount.toString(), fontWeight = FontWeight.Bold); Text("following", fontSize = 11.sp) }
                        }
                    }
                }
                if (person.fullName.isNotBlank()) Text(person.fullName, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                if (person.bio.isNotBlank()) Text(person.bio, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(14.dp))
                if (isOwn) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit profile") }
                    TextButton(onClick = onSaved, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.BookmarkBorder, null); Spacer(Modifier.width(7.dp)); Text("Saved posts") }
                }
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onFollow, modifier = Modifier.weight(1f)) { Text(if (following) "Following" else "Follow") }
                    OutlinedButton(onClick = onMessage, modifier = Modifier.weight(1f)) { Text("Message") }
                }
            }
            HorizontalDivider(color = Soft)
        }
        items(posts, key = { it.id }) { post -> AndroidPostCard(post, "", {}, { onComment(post) }, {}, {}, false) }
    }
}

@Composable
private fun MessagesScreen(
    conversations: List<NexusConversation>, active: NexusConversation?, messages: List<NexusMessage>, currentUser: String,
    draft: String, onDraft: (String) -> Unit, onConversation: (NexusConversation) -> Unit, onBack: () -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Text("Messages", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp), color = Ink)
        if (active == null) {
            LazyColumn {
                items(conversations, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { onConversation(c) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(c.avatarUrl, 44); Text(c.username, Modifier.padding(start = 12.dp), fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = Soft)
                }
                if (conversations.isEmpty()) item { Text("Search for a person and tap Message to start a conversation.", color = Color.Gray, modifier = Modifier.padding(18.dp)) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                AvatarImage(active.avatarUrl, 32); Text(active.username, Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                items(messages, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.senderId == currentUser) Arrangement.End else Arrangement.Start) {
                        Text(m.body, color = if (m.senderId == currentUser) Color.White else Ink,
                            modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(if (m.senderId == currentUser) Blue else Soft).padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, onDraft, modifier = Modifier.weight(1f), placeholder = { Text("Message…") }, singleLine = true)
                IconButton(onClick = onSend, enabled = draft.isNotBlank()) { Icon(Icons.Default.Send, "Send", tint = Blue) }
            }
        }
    }
}

@Composable
private fun SimpleListScreen(title: String, rows: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(bottom = 16.dp))
        if (rows.isEmpty()) Text("You're all caught up.", color = Color.Gray)
        else rows.forEach { Text(it, modifier = Modifier.padding(vertical = 12.dp), color = Ink); HorizontalDivider(color = Soft) }
    }
}

@Composable
private fun CreatePostDialog(imageUri: Uri?, onPick: () -> Unit, onDismiss: () -> Unit, onPublish: (String, String) -> Unit, busy: Boolean) {
    var caption by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create new post") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (imageUri != null) AsyncImage(imageUri, null, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            else OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().height(90.dp)) { Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Choose photo") }
            OutlinedTextField(caption, { caption = it }, label = { Text("Write a caption") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            OutlinedTextField(location, { location = it }, label = { Text("Location (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { onPublish(caption, location) }, enabled = !busy && (caption.isNotBlank() || imageUri != null)) { Text(if (busy) "Sharing…" else "Share") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateStoryDialog(uri: Uri?, onPick: () -> Unit, onDismiss: () -> Unit, onPublish: () -> Unit, busy: Boolean) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to your story") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (uri == null) {
                    OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().height(110.dp)) {
                        Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Choose photo or video")
                    }
                } else {
                    AsyncImage(uri, null, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                }
                Text("Stories disappear after 24 hours.", color = Color.Gray, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onPublish, enabled = uri != null && !busy) { Text(if (busy) "Sharing…" else "Share story") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StoryViewerDialog(story: NexusStory, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(story.username) },
        text = {
            if (story.mediaType == "video") {
                AndroidView(factory = { viewContext ->
                    VideoView(viewContext).apply {
                        setVideoURI(Uri.parse(story.mediaUrl))
                        setOnPreparedListener { player -> player.isLooping = true; start() }
                    }
                }, modifier = Modifier.fillMaxWidth().height(420.dp))
            } else {
                AsyncImage(story.mediaUrl, null, Modifier.fillMaxWidth().heightIn(max = 520.dp), contentScale = ContentScale.Fit)
            }
        }
    )
}

@Composable
private fun CommentDialog(post: NexusPost, comments: List<NexusComment>, draft: String, onDraft: (String) -> Unit, onDismiss: () -> Unit, onSend: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Comments") }, text = {
        Column {
            Text(post.username + "  " + post.caption, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(comments, key = { it.id }) { c -> Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(c.username, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(c.content, fontSize = 13.sp) } }
                if (comments.isEmpty()) item { Text("Be the first to comment.", color = Color.Gray) }
            }
            OutlinedTextField(draft, onDraft, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Add a comment…") })
        }
    }, confirmButton = { TextButton(onClick = onSend, enabled = draft.isNotBlank()) { Text("Post") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun EditProfileDialog(username: String, name: String, bio: String, onUsername: (String) -> Unit, onName: (String) -> Unit, onBio: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit, busy: Boolean) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit profile") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(username, onUsername, label = { Text("Username") }, singleLine = true)
            OutlinedTextField(name, onName, label = { Text("Full name") }, singleLine = true)
            OutlinedTextField(bio, onBio, label = { Text("Bio") }, minLines = 2, maxLines = 4)
        }
    }, confirmButton = { TextButton(onClick = onSave, enabled = !busy && username.length >= 3) { Text(if (busy) "Saving…" else "Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable
private fun AccountSecurityDialog(devices: List<NexusDevice>, currentDeviceId: String, password: String, onPassword: (String) -> Unit, onDismiss: () -> Unit, onRevoke: (String) -> Unit, onUpdatePassword: () -> Unit, busy: Boolean) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Account security") }, text = {
        Column(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Logged-in devices", fontWeight = FontWeight.Bold)
            if (devices.isEmpty()) Text("No device records yet.", color = Color.Gray)
            devices.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, fontWeight = FontWeight.SemiBold)
                        Text(device.platform + " · " + device.lastSeen.take(16).replace("T", " "), fontSize = 11.sp, color = Color.Gray)
                        if (device.deviceId == currentDeviceId) Text("Current device", color = Blue, fontSize = 11.sp)
                    }
                    if (device.deviceId != currentDeviceId) TextButton(onClick = { onRevoke(device.id) }) { Text("Sign out") }
                }
                HorizontalDivider(color = Soft)
            }
            Text("Change password", fontWeight = FontWeight.Bold)
            OutlinedTextField(password, onPassword, label = { Text("New password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Button(onClick = onUpdatePassword, enabled = !busy && password.length >= 8) { Text("Update password") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
