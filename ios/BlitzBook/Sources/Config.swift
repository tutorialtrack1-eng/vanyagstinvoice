import Foundation

/// Where the portal lives. The app shows this address in a web view; the login, the books and the sync are the
/// portal's own (webportal/), so the iPhone app, the Android app and the browser share one account.
enum Config {
    static let portalURL = URL(string: "https://blitzbook.co.in/")!
    /// Hosts the web view may show itself; anything else opens in Safari or the matching app (WhatsApp, UPI ...)
    static let ownHosts: Set<String> = ["blitzbook.co.in", "www.blitzbook.co.in"]
    /// Name of the message handler the portal posts to (webportal/js/native.js)
    static let bridgeName = "blitzbook"
}
