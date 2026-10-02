# BlitzBook for iPhone and iPad

iOS has no APK: an app reaches an iPhone either through the App Store or as a *web app* installed from Safari.
BlitzBook offers both.

## 1. Web app (works today, no Mac needed)

The portal at https://blitzbook.co.in is installable: `webportal/manifest.webmanifest`, the icons under
`webportal/icons/` and the offline shell `webportal/sw.js` make it so. On the phone:

1. Open **blitzbook.co.in** in **Safari** (Chrome on iOS cannot install web apps).
2. Tap **Share** (the square with the arrow), then **Add to Home Screen**, then **Add**.

BlitzBook then opens full screen from the home screen, keeps its login and books, prints / saves PDFs through
Safari's share sheet and syncs with the Android app through Supabase. The portal's **Download App** button
shows these steps under *iPhone / iPad* (and downloads the APK under *Android*).

## 2. App Store app (this folder)

`BlitzBook/` is a native shell: SwiftUI + `WKWebView` showing the portal, with a bridge for what a web view
cannot do on its own:

| Portal asks (`webportal/js/native.js`) | The app does (`PortalWebView.swift`) |
|---|---|
| `print {title, html}` | lays the document out at its `@page` size, renders a multi-page PDF, opens the share sheet (Save to Files, AirPrint, WhatsApp, Mail ...) |
| `download {name, content, type}` | writes the Excel / CSV / backup file and opens the share sheet |
| `open {url}`, `target="_blank"`, `upi:` `sms:` `tel:` `mailto:` links | opens them outside the app |

The portal detects the shell (`Native.ios`) and hides its own Download App button there. Everything else -
storage, sync, the OTP flows, file pickers for uploads and the signature - works in the web view as in Safari.
`WKAppBoundDomains` lists the portal, which is what lets the portal's service worker run inside the app.

### Build (needs a Mac with Xcode 15 or newer)

```
brew install xcodegen
cd ios/BlitzBook
xcodegen generate          # writes BlitzBook.xcodeproj from project.yml
open BlitzBook.xcodeproj
```

In Xcode: select the BlitzBook target → *Signing & Capabilities* → choose your team (or put the team id in
`project.yml` under `DEVELOPMENT_TEAM`), pick a simulator or a connected iPhone and press Run. The bundle id is
`in.co.blitzbook.app`; change it in `project.yml` if that id is taken on your developer account.

### Publish

1. Apple Developer Program membership (paid, apple.com/developer).
2. Xcode → *Product → Archive* → *Distribute App* → App Store Connect; create the app record there, fill in
   the listing and screenshots, and submit (TestFlight first if you want testers).
3. When it is live, put the App Store link in `IOS_APP_URL` at the top of `webportal/js/app.js`: the
   *iPhone / iPad* option of Download App then opens the App Store (and still offers the web app).

### Changing the portal address

`Sources/Config.swift` holds the URL the app opens and the hosts it keeps inside the web view.
