import Foundation
import Combine

struct FeedProfile: Decodable {
    let username: String
    let fullName: String?
    let avatarURL: URL?

    enum CodingKeys: String, CodingKey {
        case username
        case fullName = "full_name"
        case avatarURL = "avatar_url"
    }
}

struct FeedMedia: Decodable {
    let mediaURL: URL?
    let mediaType: String?

    enum CodingKeys: String, CodingKey {
        case mediaURL = "media_url"
        case mediaType = "media_type"
    }
}

struct FeedCount: Decodable {
    let count: Int?
}

struct FeedPost: Decodable, Identifiable {
    let id: UUID
    let caption: String
    let profiles: FeedProfile?
    let postMedia: [FeedMedia]
    let postLikes: [FeedCount]
    let comments: [FeedCount]

    enum CodingKeys: String, CodingKey {
        case id, caption, profiles, comments
        case postMedia = "post_media"
        case postLikes = "post_likes"
    }

    var likeCount: Int { postLikes.first?.count ?? postLikes.count }
    var commentCount: Int { comments.first?.count ?? comments.count }
    var media: FeedMedia? { postMedia.first }
}

@MainActor
final class FeedViewModel: ObservableObject {
    @Published private(set) var posts: [FeedPost] = []
    @Published private(set) var isLoading = true

    func load() async {
        defer { isLoading = false }
        guard
            let baseURL = Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String,
            let anonKey = Bundle.main.object(forInfoDictionaryKey: "SupabaseAnonKey") as? String,
            !baseURL.isEmpty,
            !anonKey.isEmpty,
            var components = URLComponents(string: baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/rest/v1/posts")
        else { return }

        components.queryItems = [
            URLQueryItem(name: "select", value: "id,caption,profiles!posts_user_id_fkey(username,full_name,avatar_url),post_media(media_url,media_type),post_likes(count),comments(count)"),
            URLQueryItem(name: "order", value: "created_at.desc"),
            URLQueryItem(name: "limit", value: "30")
        ]
        guard let url = components.url else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 20
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(anonKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return }
            let decoded = try JSONDecoder().decode([FeedPost].self, from: data)
            posts = decoded.filter { $0.media?.mediaURL != nil }
        } catch {
            // The feed remains empty when the public feed cannot be reached.
        }
    }
}
