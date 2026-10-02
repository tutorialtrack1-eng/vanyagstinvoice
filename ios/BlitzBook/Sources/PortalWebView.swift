import SwiftUI
import WebKit
import UIKit

/// State shared between the SwiftUI screen and the web view.
final class PortalModel: ObservableObject {
    @Published var isLoading = true
    @Published var failure: String?
    weak var webView: WKWebView?
    func reload() { failure = nil; isLoading = true; webView?.load(URLRequest(url: Config.portalURL)) }
}

/// The portal in a WKWebView, plus the bridge the portal uses (webportal/js/native.js):
///   print    {title, html}          -> the document is laid out as the portal prints it, turned into a PDF and shared
///   download {name, content, type}  -> the exported file (Excel, CSV, backup) is written and shared
///   open     {url}                  -> opened outside the app
/// Links that leave the portal (WhatsApp, UPI apps, sms:, mailto:, tel:, other sites) open outside as well.
struct PortalWebView: UIViewRepresentable {
    @ObservedObject var model: PortalModel

    func makeCoordinator() -> Coordinator { Coordinator(model: model) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()                     // login and books persist between launches
        config.allowsInlineMediaPlayback = true
        config.limitsNavigationsToAppBoundDomains = true         // with WKAppBoundDomains: the portal's service worker runs
        config.userContentController.add(context.coordinator, name: Config.bridgeName)
        config.preferences.javaScriptCanOpenWindowsAutomatically = true
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        web.uiDelegate = context.coordinator
        web.allowsBackForwardNavigationGestures = false
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.isOpaque = false
        web.backgroundColor = UIColor(red: 0.118, green: 0.106, blue: 0.294, alpha: 1)
        web.customUserAgent = (web.value(forKey: "userAgent") as? String ?? "") + " BlitzBookiOS/1.0"
        model.webView = web
        web.load(URLRequest(url: Config.portalURL))
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    // MARK: - delegate

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        let model: PortalModel
        private var printers: [PrintJob] = []   // kept alive until their PDF is made
        init(model: PortalModel) { self.model = model }

        // Navigation: the portal stays inside, everything else leaves the app
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = action.request.url else { decisionHandler(.allow); return }
            let scheme = url.scheme?.lowercased() ?? ""
            let inside = (scheme == "https" || scheme == "http") && Config.ownHosts.contains(url.host?.lowercased() ?? "")
            if inside && action.targetFrame != nil { decisionHandler(.allow); return }
            if scheme == "about" || scheme == "blob" || scheme == "data" { decisionHandler(.allow); return }
            UIApplication.shared.open(url)
            decisionHandler(.cancel)
        }
        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if let url = action.request.url { UIApplication.shared.open(url) }   // target="_blank": WhatsApp, the App Store ...
            return nil
        }
        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) { DispatchQueue.main.async { self.model.isLoading = true } }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { DispatchQueue.main.async { self.model.isLoading = false; self.model.failure = nil } }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { failed(error) }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { failed(error) }
        private func failed(_ error: Error) {
            let e = error as NSError
            if e.code == NSURLErrorCancelled { return }
            DispatchQueue.main.async {
                self.model.isLoading = false
                self.model.failure = e.code == NSURLErrorNotConnectedToInternet || e.code == NSURLErrorTimedOut
                    ? "No internet connection. BlitzBook needs a connection the first time it opens; after that it opens offline and syncs when you are back online."
                    : "BlitzBook could not be opened.\n\(e.localizedDescription)"
            }
        }

        // JavaScript dialogs (the portal uses its own, these are a safety net)
        func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
            let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
            present(a)
        }
        func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
            let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in completionHandler(false) })
            a.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler(true) })
            present(a)
        }

        // The bridge
        func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == Config.bridgeName, let body = message.body as? [String: Any], let kind = body["kind"] as? String else { return }
            switch kind {
            case "print":
                let html = body["html"] as? String ?? "", title = body["title"] as? String ?? "Document"
                let job = PrintJob(html: html, title: title) { [weak self] url in
                    self?.printers.removeAll { $0.done }
                    if let url = url { self?.share(url) }
                }
                printers.append(job); job.start()
            case "download":
                let name = (body["name"] as? String ?? "BlitzBook.txt").replacingOccurrences(of: "/", with: "-")
                let content = body["content"] as? String ?? ""
                let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
                try? content.data(using: .utf8)?.write(to: url, options: .atomic)
                share(url)
            case "open":
                if let s = body["url"] as? String, let url = URL(string: s) { UIApplication.shared.open(url) }
            default: break
            }
        }

        private func share(_ url: URL) {
            let sheet = UIActivityViewController(activityItems: [url], applicationActivities: nil)
            if let pop = sheet.popoverPresentationController, let view = model.webView {   // iPad anchors the sheet
                pop.sourceView = view; pop.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.maxY - 40, width: 1, height: 1); pop.permittedArrowDirections = .down
            }
            present(sheet)
        }
        private func present(_ vc: UIViewController) {
            guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first(where: { $0.activationState == .foregroundActive }),
                  var top = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return }
            while let next = top.presentedViewController { top = next }
            top.present(vc, animated: true)
        }
    }
}

/// Renders one printable document (the HTML the portal would have sent to the print dialog) in an offscreen
/// web view and turns it into a PDF with the page size the document asks for in its @page rule, page by page,
/// exactly as Safari's print would. Shares the file when done.
final class PrintJob: NSObject, WKNavigationDelegate {
    private let html: String, title: String, finish: (URL?) -> Void
    private var web: WKWebView?
    private(set) var done = false

    init(html: String, title: String, finish: @escaping (URL?) -> Void) { self.html = html; self.title = title; self.finish = finish }

    func start() {
        let size = PrintJob.pageSize(in: html)
        let web = WKWebView(frame: CGRect(x: 0, y: 0, width: size.width, height: size.height))
        web.navigationDelegate = self
        self.web = web
        web.loadHTMLString(html, baseURL: nil)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // The document's own script has laid the pages out by now; a short pause lets fonts settle
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { [self] in
            let page = CGRect(origin: .zero, size: PrintJob.pageSize(in: html))
            let renderer = UIPrintPageRenderer()
            renderer.addPrintFormatter(webView.viewPrintFormatter(), startingAtPageAt: 0)
            renderer.setValue(page, forKey: "paperRect")
            renderer.setValue(page, forKey: "printableRect")
            let data = NSMutableData()
            UIGraphicsBeginPDFContextToData(data, page, [kCGPDFContextTitle as String: title, kCGPDFContextCreator as String: "BlitzBook"])
            for i in 0..<max(1, renderer.numberOfPages) { UIGraphicsBeginPDFPage(); renderer.drawPage(at: i, in: UIGraphicsGetPDFContextBounds()) }
            UIGraphicsEndPDFContext()
            let safe = title.replacingOccurrences(of: "[^A-Za-z0-9 _.-]", with: "_", options: .regularExpression)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent((safe.isEmpty ? "BlitzBook" : safe) + ".pdf")
            try? data.write(to: url, options: .atomic)
            done = true; web = nil
            finish(url)
        }
    }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { done = true; web = nil; finish(nil) }

    /// Page size in points from the document's "@page { size: ... }": A4 / A5 / letter / legal or "Wmm Hmm" / "Win Hin"
    static func pageSize(in html: String) -> CGSize {
        let mmToPt = 72.0 / 25.4
        guard let r = html.range(of: #"@page\s*\{\s*size:\s*([^;}]+)"#, options: .regularExpression) else { return CGSize(width: 595.3, height: 841.9) }
        let spec = String(html[r]).replacingOccurrences(of: #"@page\s*\{\s*size:\s*"#, with: "", options: .regularExpression).lowercased()
        if spec.contains("a5") { return CGSize(width: 148 * mmToPt, height: 210 * mmToPt) }
        if spec.contains("letter") { return CGSize(width: 612, height: 792) }
        if spec.contains("legal") { return CGSize(width: 612, height: 1008) }
        let nums = spec.components(separatedBy: CharacterSet(charactersIn: " ")).compactMap { part -> Double? in
            if part.hasSuffix("mm"), let v = Double(part.dropLast(2)) { return v * mmToPt }
            if part.hasSuffix("in"), let v = Double(part.dropLast(2)) { return v * 72 }
            return nil
        }
        if nums.count >= 2 { return CGSize(width: nums[0], height: nums[1]) }
        return CGSize(width: 595.3, height: 841.9)
    }
}
