import SwiftUI

struct PicstreamFeedView: View {
    @StateObject private var model = FeedViewModel()
    @State private var isCompact = false
    @State private var selectedTab = 0

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 0) {
                header

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 24) {
                        ForEach(model.posts) { post in
                            PicstreamPostRow(post: post)
                        }
                    }
                    .padding(.horizontal, 19)
                    .padding(.top, 38)
                    .padding(.bottom, 120)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .scrollIndicators(.hidden)
                .onScrollGeometryChange(for: CGFloat.self) { geometry in
                    geometry.contentOffset.y
                } action: { oldValue, newValue in
                    let movement = newValue - oldValue
                    guard abs(movement) > 2 else { return }
                    withAnimation(.snappy(duration: 0.22)) {
                        isCompact = movement > 0
                    }
                }
            }
        }
        .overlay(alignment: .bottom) {
            PicstreamNavigationBar(isCompact: isCompact, selectedTab: $selectedTab)
                .padding(.horizontal, 27)
                .padding(.bottom, 0)
        }
        .task {
            await model.load()
        }
    }

    private var header: some View {
        HStack {
            Spacer(minLength: 0)
            Image("PicstreamLogo")
                .resizable()
                .scaledToFit()
                .frame(width: isCompact ? 180 : 237, height: isCompact ? 40 : 53)
                .accessibilityLabel("Picstream")
            Spacer(minLength: 0)
        }
        .frame(height: isCompact ? 46 : 63)
        .padding(.top, isCompact ? 0 : 10)
        .animation(.snappy(duration: 0.22), value: isCompact)
    }
}

private struct PicstreamPostRow: View {
    let post: FeedPost

    private var displayName: String {
        let fullName = post.profiles?.fullName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return fullName.isEmpty ? (post.profiles?.username ?? "") : fullName
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 3) {
                avatar(size: 34)
                VStack(alignment: .leading, spacing: 1) {
                    Text(displayName)
                        .font(.system(size: 15, weight: .regular))
                        .foregroundStyle(.black)
                    Text("@\(post.profiles?.username ?? "")")
                        .font(.system(size: 10, weight: .regular))
                        .foregroundStyle(.black)
                }
            }
            .frame(height: 34, alignment: .leading)

            Text(post.caption)
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(.black)
                .lineLimit(2)
                .padding(.top, 6)

            GeometryReader { geometry in
                let mediaSize = min(305, max(0, geometry.size.width - 42))
                HStack(alignment: .top, spacing: 5) {
                    media
                        .frame(width: mediaSize, height: mediaSize)
                        .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))

                    actionRail
                        .frame(width: 37)
                        .padding(.top, 28)
                }
            }
            .frame(height: 305)
            .padding(.top, 6)
        }
    }

    @ViewBuilder
    private var media: some View {
        if let url = post.media?.mediaURL {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                case .empty, .failure:
                    Color(white: 0.77)
                @unknown default:
                    Color(white: 0.77)
                }
            }
        } else {
            Color(white: 0.77)
        }
    }

    private var actionRail: some View {
        VStack(spacing: 18) {
            actionSymbol("heart", label: abbreviated(post.likeCount))
            actionSymbol("hand.thumbsdown", label: "Dislike")
            actionSymbol("bubble.right", label: abbreviated(post.commentCount))
        }
        .foregroundStyle(.black)
    }

    private func actionSymbol(_ symbol: String, label: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: symbol)
                .font(.system(size: 24, weight: .regular))
                .frame(width: 32, height: 27)
            Text(label)
                .font(.system(size: 11, weight: .regular))
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private func avatar(size: CGFloat) -> some View {
        if let url = post.profiles?.avatarURL {
            AsyncImage(url: url) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    Circle().fill(Color(white: 0.9))
                        .overlay(Image(systemName: "person.fill").foregroundStyle(.gray))
                }
            }
            .frame(width: size, height: size)
            .clipShape(Circle())
        } else {
            Circle()
                .fill(Color(white: 0.9))
                .overlay(Image(systemName: "person.fill").font(.system(size: 15)).foregroundStyle(.gray))
                .frame(width: size, height: size)
        }
    }

    private func abbreviated(_ value: Int) -> String {
        if value >= 1_000_000 { return String(format: "%.1fM", Double(value) / 1_000_000) }
        if value >= 1_000 { return String(format: "%.1fK", Double(value) / 1_000) }
        return String(value)
    }
}

private struct PicstreamNavigationBar: View {
    let isCompact: Bool
    @Binding var selectedTab: Int

    private let symbols = ["house", "magnifyingglass", "plus", "person.crop.circle"]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(symbols.indices, id: \.self) { index in
                Button {
                    selectedTab = index
                } label: {
                    Image(systemName: symbols[index])
                        .font(.system(size: isCompact ? 23 : 26, weight: .regular))
                        .foregroundStyle(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: isCompact ? 56 : 76)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(["Home", "Explore", "Create", "Profile"][index])
            }
        }
        .background {
            if #available(iOS 26.0, *) {
                Capsule()
                    .fill(.clear)
                    .glassEffect(.regular, in: Capsule())
            } else {
                Capsule()
                    .fill(.ultraThinMaterial)
                    .overlay(Capsule().stroke(Color.black.opacity(0.06), lineWidth: 0.7))
            }
        }
        .clipShape(Capsule())
        .frame(height: isCompact ? 56 : 76)
        .animation(.snappy(duration: 0.22), value: isCompact)
    }
}
