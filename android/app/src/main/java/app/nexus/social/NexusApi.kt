package app.nexus.social

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NexusProfile(
    val id: String, val username: String, val fullName: String = "", val avatarUrl: String = "", val bio: String = ""
) {
    companion object {
        fun from(json: JSONObject): NexusProfile = NexusProfile(
            id = json.optString("id"), username = json.optString("username", "user"),
            fullName = json.optString("full_name"), avatarUrl = json.optString("avatar_url"), bio = json.optString("bio")
        )
    }
}
data class NexusPost(
    val id: String, val userId: String, val username: String, val avatarUrl: String, val caption: String,
    val location: String, val mediaUrl: String, val mediaType: String, val likes: Set<String>, val comments: Int
) {
    companion object {
        fun from(json: JSONObject): NexusPost {
            val profile = json.optJSONObject("profiles") ?: JSONObject()
            val media = json.optJSONArray("post_media")?.optJSONObject(0) ?: JSONObject()
            val likesJson = json.optJSONArray("post_likes") ?: JSONArray()
            val likes = buildSet { for (i in 0 until likesJson.length()) add(likesJson.optJSONObject(i)?.optString("user_id").orEmpty()) }
            val count = json.optJSONArray("comments")?.optJSONObject(0)?.optInt("count") ?: 0
            return NexusPost(json.optString("id"), json.optString("user_id"), profile.optString("username", "user"),
                profile.optString("avatar_url"), json.optString("caption"), json.optString("location"),
                media.optString("media_url"), media.optString("media_type", "image"), likes, count)
        }
    }
}
data class NexusComment(val id: String, val username: String, val avatarUrl: String, val content: String) {
    companion object {
        fun from(json: JSONObject): NexusComment {
            val p = json.optJSONObject("profiles") ?: JSONObject()
            return NexusComment(json.optString("id"), p.optString("username", "user"), p.optString("avatar_url"), json.optString("content"))
        }
    }
}
data class NexusConversation(val id: String, val username: String, val avatarUrl: String)
data class NexusMessage(val id: String, val senderId: String, val body: String)
data class NexusDevice(val id: String, val deviceId: String, val name: String, val platform: String, val lastSeen: String, val revoked: Boolean)
data class NexusStory(val id: String, val userId: String, val username: String, val avatarUrl: String, val mediaUrl: String, val mediaType: String) {
    companion object {
        fun from(json: JSONObject): NexusStory {
            val p = json.optJSONObject("profiles") ?: JSONObject()
            return NexusStory(json.optString("id"), json.optString("user_id"), p.optString("username", "user"),
                p.optString("avatar_url"), json.optString("media_url"), json.optString("media_type", "image"))
        }
    }
}

class NexusApi(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_auth", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    val isConfigured: Boolean get() = baseUrl.startsWith("https://") && anonKey.isNotBlank()
    val isSignedIn: Boolean get() = !accessToken.isNullOrBlank()
    val userId: String get() = prefs.getString("user_id", "").orEmpty()
    val currentDeviceId: String get() = deviceId
    private val accessToken: String? get() = prefs.getString("access_token", null)

    private suspend fun request(
        method: String, path: String, json: JSONObject? = null, bytes: ByteArray? = null,
        mimeType: String = "application/json", useSession: Boolean = true, prefer: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured) throw IOException("Set SUPABASE_URL and SUPABASE_ANON_KEY in android/local.properties.")
        val builder = Request.Builder().url(baseUrl + path).header("apikey", anonKey)
            .header("Authorization", "Bearer " + if (useSession) (accessToken ?: anonKey) else anonKey)
            .header("Accept", "application/json")
        if (prefer != null) builder.header("Prefer", prefer)
        val body: RequestBody? = when {
            json != null -> json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            bytes != null -> bytes.toRequestBody(mimeType.toMediaType())
            else -> null
        }
        builder.method(method, body)
        fun execute(bearer: String): Pair<Int, String> {
            val requestBuilder = Request.Builder().url(baseUrl + path).header("apikey", anonKey)
                .header("Authorization", "Bearer " + bearer).header("Accept", "application/json")
            if (prefer != null) requestBuilder.header("Prefer", prefer)
            requestBuilder.method(method, body)
            return client.newCall(requestBuilder.build()).execute().use { response ->
                Pair(response.code, response.body?.string().orEmpty())
            }
        }
        var bearer = if (useSession) (accessToken ?: anonKey) else anonKey
        var result = execute(bearer)
        if (result.first == 401 && useSession) {
            val refreshToken = prefs.getString("refresh_token", null)
            if (!refreshToken.isNullOrBlank()) {
                val refreshBody = JSONObject().put("refresh_token", refreshToken).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val refreshRequest = Request.Builder().url(baseUrl + "/auth/v1/token?grant_type=refresh_token")
                    .header("apikey", anonKey).header("Authorization", "Bearer " + anonKey)
                    .post(refreshBody).build()
                val refreshed = client.newCall(refreshRequest).execute().use { response ->
                    Pair(response.code, response.body?.string().orEmpty())
                }
                if (refreshed.first in 200..299) {
                    storeSession(JSONObject(refreshed.second))
                    bearer = accessToken ?: anonKey
                    result = execute(bearer)
                }
            }
        }
        if (result.first !in 200..299) {
            val message = try { JSONObject(result.second).optString("message", JSONObject(result.second).optString("msg", result.second)) } catch (_: Exception) { result.second }
            throw IOException(message.ifBlank { "Supabase request failed: HTTP " + result.first })
        }
        result.second
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    suspend fun signIn(email: String, password: String) {
        val result = JSONObject(request("POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password), useSession = false))
        storeSession(result)
    }
    suspend fun signUp(email: String, password: String, username: String): Boolean {
        val result = JSONObject(request("POST", "/auth/v1/signup",
            JSONObject().put("email", email).put("password", password).put("data", JSONObject().put("username", username)), useSession = false))
        if (result.has("access_token")) storeSession(result)
        return result.has("access_token")
    }
    private fun storeSession(result: JSONObject) {
        val user = result.optJSONObject("user") ?: JSONObject()
        prefs.edit().putString("access_token", result.optString("access_token"))
            .putString("refresh_token", result.optString("refresh_token"))
            .putString("user_id", user.optString("id")).apply()
    }
    suspend fun signOut() {
        try { request("DELETE", "/rest/v1/device_sessions?user_id=eq." + encode(userId) + "&device_id=eq." + encode(deviceId), prefer = "return=minimal") } catch (_: Exception) {}
        try { request("POST", "/auth/v1/logout") } finally { prefs.edit().remove("access_token").remove("refresh_token").remove("user_id").apply() }
    }
    suspend fun profile(): NexusProfile? {
        val rows = JSONArray(request("GET", "/rest/v1/profiles?id=eq." + encode(userId) + "&select=*"))
        return if (rows.length() == 0) null else NexusProfile.from(rows.getJSONObject(0))
    }
    suspend fun updateProfile(username: String, fullName: String, bio: String) {
        request("PATCH", "/rest/v1/profiles?id=eq." + encode(userId), JSONObject().put("username", username).put("full_name", fullName).put("bio", bio), prefer = "return=minimal")
    }
    suspend fun requestPasswordReset(email: String) { request("POST", "/auth/v1/recover", JSONObject().put("email", email), useSession = false) }
    suspend fun updatePassword(password: String) { request("PUT", "/auth/v1/user", JSONObject().put("password", password)) }
    private val deviceId: String get() {
        var id = prefs.getString("device_id", null)
        if (id.isNullOrBlank()) { id = java.util.UUID.randomUUID().toString(); prefs.edit().putString("device_id", id).apply() }
        return id
    }
    suspend fun registerDevice() {
        request("POST", "/rest/v1/device_sessions?on_conflict=user_id,device_id",
            JSONObject().put("user_id", userId).put("device_id", deviceId).put("device_name", android.os.Build.MODEL.ifBlank { "Android device" })
                .put("platform", "Android").put("last_seen", java.time.Instant.now().toString()).put("revoked_at", JSONObject.NULL),
            prefer = "resolution=merge-duplicates,return=minimal")
    }
    suspend fun deviceSessions(): List<NexusDevice> {
        val rows = JSONArray(request("GET", "/rest/v1/device_sessions?select=id,device_id,device_name,platform,last_seen,revoked_at&user_id=eq." + encode(userId) + "&order=last_seen.desc"))
        return (0 until rows.length()).map { val row = rows.getJSONObject(it); NexusDevice(row.optString("id"), row.optString("device_id"), row.optString("device_name"), row.optString("platform"), row.optString("last_seen"), !row.isNull("revoked_at")) }
    }
    suspend fun revokeDevice(id: String) { request("PATCH", "/rest/v1/device_sessions?id=eq." + encode(id), JSONObject().put("revoked_at", java.time.Instant.now().toString()), prefer = "return=minimal") }
    suspend fun isDeviceRevoked(): Boolean {
        val rows = JSONArray(request("GET", "/rest/v1/device_sessions?select=revoked_at&user_id=eq." + encode(userId) + "&device_id=eq." + encode(deviceId)))
        return rows.length() > 0 && !rows.getJSONObject(0).isNull("revoked_at")
    }
    suspend fun feed(): List<NexusPost> {
        val rows = JSONArray(request("GET", "/rest/v1/posts?select=id,user_id,caption,location,created_at,profiles!posts_user_id_fkey(id,username,avatar_url),post_media(id,media_url,media_type),post_likes(user_id),comments(count)&order=created_at.desc&limit=50"))
        return (0 until rows.length()).map { NexusPost.from(rows.getJSONObject(it)) }
    }
    suspend fun toggleLike(postId: String, currentlyLiked: Boolean) {
        if (currentlyLiked) request("DELETE", "/rest/v1/post_likes?post_id=eq." + encode(postId) + "&user_id=eq." + encode(userId))
        else request("POST", "/rest/v1/post_likes", JSONObject().put("post_id", postId).put("user_id", userId), prefer = "return=minimal")
    }
    suspend fun publishPost(caption: String, location: String, media: ByteArray?, mimeType: String, extension: String) {
        val rows = JSONArray(request("POST", "/rest/v1/posts", JSONObject().put("user_id", userId).put("caption", caption).put("location", if (location.isBlank()) JSONObject.NULL else location), prefer = "return=representation"))
        val postId = rows.getJSONObject(0).getString("id")
        if (media == null) return
        val path = userId + "/" + java.util.UUID.randomUUID().toString() + "." + extension
        try {
            request("POST", "/storage/v1/object/post-media/" + path, bytes = media, mimeType = mimeType)
            val url = baseUrl + "/storage/v1/object/public/post-media/" + path
            request("POST", "/rest/v1/post_media", JSONObject().put("post_id", postId).put("user_id", userId)
                .put("storage_path", path).put("media_url", url).put("media_type", if (mimeType.startsWith("video/")) "video" else "image"), prefer = "return=minimal")
        } catch (e: Exception) {
            try { request("DELETE", "/rest/v1/posts?id=eq." + encode(postId)) } catch (_: Exception) {}
            throw e
        }
    }
    suspend fun comments(postId: String): List<NexusComment> {
        val rows = JSONArray(request("GET", "/rest/v1/comments?select=id,content,profiles!comments_user_id_fkey(username,avatar_url)&post_id=eq." + encode(postId) + "&order=created_at.asc"))
        return (0 until rows.length()).map { NexusComment.from(rows.getJSONObject(it)) }
    }
    suspend fun addComment(postId: String, text: String) {
        request("POST", "/rest/v1/comments", JSONObject().put("post_id", postId).put("user_id", userId).put("content", text), prefer = "return=minimal")
    }
    suspend fun searchPeople(query: String): List<NexusProfile> {
        val rows = JSONArray(request("GET", "/rest/v1/profiles?select=id,username,full_name,avatar_url,bio&username=ilike.*" + encode(query) + "*&limit=25"))
        return (0 until rows.length()).map { NexusProfile.from(rows.getJSONObject(it)) }
    }
    suspend fun follow(profileId: String, following: Boolean) {
        if (following) request("DELETE", "/rest/v1/follows?follower_id=eq." + encode(userId) + "&following_id=eq." + encode(profileId))
        else request("POST", "/rest/v1/follows", JSONObject().put("follower_id", userId).put("following_id", profileId), prefer = "return=minimal")
    }
    suspend fun startConversation(profileId: String): String {
        val raw = request("POST", "/rest/v1/rpc/start_conversation", JSONObject().put("other_user_id", profileId))
        return raw.trim().trim('"')
    }
    suspend fun conversations(): List<NexusConversation> {
        val rows = JSONArray(request("GET", "/rest/v1/conversation_members?select=conversation_id,conversations(id,conversation_members(user_id,profiles!conversation_members_user_id_fkey(id,username,avatar_url)))&user_id=eq." + encode(userId)))
        val result = mutableListOf<NexusConversation>()
        for (i in 0 until rows.length()) {
            val conv = rows.getJSONObject(i).optJSONObject("conversations") ?: continue
            val members = conv.optJSONArray("conversation_members") ?: continue
            var other: JSONObject? = null
            for (j in 0 until members.length()) {
                val member = members.getJSONObject(j)
                if (member.optString("user_id") != userId) other = member.optJSONObject("profiles")
            }
            if (other != null) result.add(NexusConversation(conv.optString("id"), other.optString("username"), other.optString("avatar_url")))
        }
        return result
    }
    suspend fun messages(conversationId: String): List<NexusMessage> {
        val rows = JSONArray(request("GET", "/rest/v1/messages?select=id,sender_id,body&conversation_id=eq." + encode(conversationId) + "&order=created_at.asc&limit=200"))
        return (0 until rows.length()).map { val m = rows.getJSONObject(it); NexusMessage(m.optString("id"), m.optString("sender_id"), m.optString("body")) }
    }
    suspend fun sendMessage(conversationId: String, body: String) {
        request("POST", "/rest/v1/messages", JSONObject().put("conversation_id", conversationId).put("sender_id", userId).put("body", body), prefer = "return=minimal")
    }
    suspend fun notifications(): List<String> {
        val rows = JSONArray(request("GET", "/rest/v1/notifications?select=id,kind,created_at,profiles!notifications_actor_id_fkey(username)&order=created_at.desc&limit=50&recipient_id=eq." + encode(userId)))
        val values = (0 until rows.length()).map { i -> val n = rows.getJSONObject(i); val actor = n.optJSONObject("profiles")?.optString("username") ?: "Someone"; actor + " " + when(n.optString("kind")) { "like" -> "liked your post"; "comment" -> "commented on your post"; "follow" -> "started following you"; else -> "interacted with you" } }
        request("PATCH", "/rest/v1/notifications?recipient_id=eq." + encode(userId) + "&is_read=eq.false", JSONObject().put("is_read", true), prefer = "return=minimal")
        return values
    }

    suspend fun postsForUser(profileId: String): List<NexusPost> {
        val rows = JSONArray(request("GET", "/rest/v1/posts?select=id,user_id,caption,location,created_at,profiles!posts_user_id_fkey(id,username,avatar_url),post_media(id,media_url,media_type),post_likes(user_id),comments(count)&user_id=eq." + encode(profileId) + "&order=created_at.desc"))
        return (0 until rows.length()).map { NexusPost.from(rows.getJSONObject(it)) }
    }
    suspend fun followStatus(profileId: String): Boolean {
        val rows = JSONArray(request("GET", "/rest/v1/follows?select=follower_id&follower_id=eq." + encode(userId) + "&following_id=eq." + encode(profileId)))
        return rows.length() > 0
    }
    suspend fun profileCounts(profileId: String): Pair<Int, Int> {
        val followers = JSONArray(request("GET", "/rest/v1/follows?select=follower_id&following_id=eq." + encode(profileId)))
        val following = JSONArray(request("GET", "/rest/v1/follows?select=following_id&follower_id=eq." + encode(profileId)))
        return Pair(followers.length(), following.length())
    }
    suspend fun toggleSaved(postId: String, alreadySaved: Boolean) {
        if (alreadySaved) request("DELETE", "/rest/v1/saved_posts?user_id=eq." + encode(userId) + "&post_id=eq." + encode(postId))
        else request("POST", "/rest/v1/saved_posts", JSONObject().put("user_id", userId).put("post_id", postId), prefer = "return=minimal")
    }
    suspend fun savedPosts(): List<NexusPost> {
        val rows = JSONArray(request("GET", "/rest/v1/saved_posts?select=posts(id,user_id,caption,location,created_at,profiles!posts_user_id_fkey(id,username,avatar_url),post_media(id,media_url,media_type),post_likes(user_id),comments(count))&user_id=eq." + encode(userId) + "&order=created_at.desc"))
        val result = mutableListOf<NexusPost>()
        for (i in 0 until rows.length()) {
            val post = rows.getJSONObject(i).optJSONObject("posts") ?: continue
            result.add(NexusPost.from(post))
        }
        return result
    }

    suspend fun stories(): List<NexusStory> {
        val rows = JSONArray(request("GET", "/rest/v1/stories?select=id,user_id,media_url,media_type,profiles!stories_user_id_fkey(id,username,avatar_url)&expires_at=gt." + encode(java.time.Instant.now().toString()) + "&order=created_at.asc&limit=100"))
        return (0 until rows.length()).map { NexusStory.from(rows.getJSONObject(it)) }
    }
    suspend fun publishStory(media: ByteArray, mimeType: String, extension: String) {
        if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) throw IOException("Choose an image or video.")
        val path = userId + "/" + java.util.UUID.randomUUID().toString() + "." + extension
        request("POST", "/storage/v1/object/post-media/" + path, bytes = media, mimeType = mimeType)
        val url = baseUrl + "/storage/v1/object/public/post-media/" + path
        try {
            request("POST", "/rest/v1/stories", JSONObject().put("user_id", userId).put("storage_path", path)
                .put("media_url", url).put("media_type", if (mimeType.startsWith("video/")) "video" else "image"), prefer = "return=minimal")
        } catch (e: Exception) {
            try { request("DELETE", "/storage/v1/object/post-media/" + path) } catch (_: Exception) {}
            throw e
        }
    }
}
