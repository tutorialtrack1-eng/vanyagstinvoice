import SwiftUI

/// The one screen of the app: the portal, a progress bar while it loads, and a note with Retry when it cannot
/// be reached at all (after the first visit the portal's own offline shell takes over, so this is rare).
struct ContentView: View {
    @StateObject private var portal = PortalModel()

    var body: some View {
        ZStack {
            Color(red: 0.118, green: 0.106, blue: 0.294).ignoresSafeArea() // #1E1B4B, the portal's top bar
            PortalWebView(model: portal)
                .ignoresSafeArea(.container, edges: .bottom)
            if portal.isLoading && portal.failure == nil {
                VStack { ProgressView().progressViewStyle(.linear).tint(.white).padding(.horizontal); Spacer() }
            }
            if let failure = portal.failure {
                VStack(spacing: 14) {
                    Image(systemName: "bolt.fill").font(.system(size: 44)).foregroundColor(.white)
                    Text("BlitzBook").font(.title.bold()).foregroundColor(.white)
                    Text(failure).multilineTextAlignment(.center).foregroundColor(.white.opacity(0.85)).padding(.horizontal, 32)
                    Button("Try again") { portal.reload() }
                        .buttonStyle(.borderedProminent).tint(.white).foregroundColor(Color(red: 0.118, green: 0.106, blue: 0.294))
                }
                .padding().frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(red: 0.118, green: 0.106, blue: 0.294).ignoresSafeArea())
            }
        }
        .preferredColorScheme(.light)
    }
}
