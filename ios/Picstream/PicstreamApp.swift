import SwiftUI

@main
struct PicstreamApp: App {
    var body: some Scene {
        WindowGroup {
            PicstreamFeedView()
                .preferredColorScheme(.light)
        }
    }
}
