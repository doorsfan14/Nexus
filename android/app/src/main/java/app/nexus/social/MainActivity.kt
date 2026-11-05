package app.nexus.social

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF171717)
private data class FeedPost(val user: String, val place: String, val caption: String, val likes: Int, val image: String)
private val demo = listOf(
    FeedPost("mila.jpg", "Dolomites, Italy", "Somewhere between the mountains and the sky.", 1284, "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1000"),
    FeedPost("noahcreates", "Lake house", "A slower kind of morning.", 862, "https://images.unsplash.com/photo-1493246507139-91e8fad9978e?w=1000"),
    FeedPost("daily.frames", "", "Blue on blue.", 2401, "https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=1000")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexusApp() }
    }
}

@Composable
private fun NexusApp() {
    var selected by remember { mutableStateOf("Home") }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0095F6), onBackground = Ink)) {
        Scaffold(
            topBar = {
                Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("nexus", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selected = "Notifications" }) { Icon(Icons.Default.FavoriteBorder, "Notifications", tint = Ink) }
                    IconButton(onClick = { selected = "Messages" }) { Icon(Icons.Default.Send, "Messages", tint = Ink) }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf(Icons.Default.Home to "Home", Icons.Default.Search to "Search", Icons.Default.AddBox to "Create", Icons.Default.FavoriteBorder to "Activity", Icons.Default.PersonOutline to "Profile").forEach { (icon, label) ->
                        NavigationBarItem(selected = selected == label, onClick = { selected = label }, icon = { Icon(icon, label) }, label = null)
                    }
                }
            },
            containerColor = Color.White
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    LazyRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(listOf("Your story", "mila.jpg", "noahcreates", "daily.frames", "sophia", "alex.jpg")) { name ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
                                Box(Modifier.size(60.dp).clip(CircleShape).background(Color(0xFFE9A34A)), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFFECECEC)), contentAlignment = Alignment.Center) {
                                        Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Ink)
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(name, fontSize = 10.sp, maxLines = 1, color = Ink)
                            }
                        }
                    }
                }
                items(demo) { post ->
                    var liked by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFE7E7E7)), contentAlignment = Alignment.Center) { Text(post.user.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(post.user, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Ink)
                                if (post.place.isNotBlank()) Text(post.place, fontSize = 10.sp, color = Color.DarkGray)
                            }
                            Icon(Icons.Default.MoreHoriz, "More", tint = Ink)
                        }
                        Box(Modifier.fillMaxWidth().height(390.dp).background(Color(0xFFF1F1F1)), contentAlignment = Alignment.Center) {
                            Text("Post media loads from the connected feed", color = Color.Gray, fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { liked = !liked }) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (liked) Color.Red else Ink) }
                            IconButton(onClick = { selected = "Comments" }) { Icon(Icons.Default.ChatBubbleOutline, "Comment", tint = Ink) }
                            IconButton(onClick = { selected = "Messages" }) { Icon(Icons.Default.Send, "Share", tint = Ink) }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { selected = "Saved" }) { Icon(Icons.Default.BookmarkBorder, "Save", tint = Ink) }
                        }
                        Text("${post.likes + if (liked) 1 else 0} likes", Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Ink)
                        Text(post.caption, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 12.sp, color = Ink)
                    }
                }
            }
        }
    }
}