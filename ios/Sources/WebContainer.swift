import SwiftUI
import WebKit

/// Оболочка над сайтом игры: WKWebView с постоянным хранилищем (вход сохраняется между запусками).
struct WebContainer: UIViewRepresentable {
    static let site = URL(string: "https://mafia.qqhtml.online/")!

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        cfg.websiteDataStore = .default()                       // localStorage и cookie сохраняются
        cfg.allowsInlineMediaPlayback = true
        // Сайт определяет приложение по window.QQApp (push на iOS пока нет, поэтому token пустой)
        let shim = "window.QQApp={token:function(){return ''},version:function(){return 'ios'},openUrl:function(u){window.open(u,'_blank')},checkUpdate:function(){}};"
        cfg.userContentController.addUserScript(WKUserScript(source: shim, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        let web = WKWebView(frame: .zero, configuration: cfg)
        web.customUserAgent = (web.value(forKey: "userAgent") as? String ?? "") + " QQMafiaApp-iOS/1.0"
        web.allowsBackForwardNavigationGestures = true
        web.isOpaque = false
        web.backgroundColor = UIColor(red: 0.059, green: 0, blue: 0.004, alpha: 1)
        web.scrollView.backgroundColor = web.backgroundColor
        web.scrollView.contentInsetAdjustmentBehavior = .never   // отступы рисует сам сайт (safe-area)
        web.navigationDelegate = context.coordinator
        web.uiDelegate = context.coordinator
        web.load(URLRequest(url: Self.site))
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        private func isOurs(_ url: URL?) -> Bool { url?.host == WebContainer.site.host }

        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = action.request.url else { return decisionHandler(.cancel) }
            if url.scheme == "about" || (url.scheme == "https" && isOurs(url)) { return decisionHandler(.allow) }
            UIApplication.shared.open(url)                      // t.me, политика и т.д. открываются в Telegram / Safari
            decisionHandler(.cancel)
        }

        // window.open (вход через бота, ссылки) — во внешнее приложение
        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if let url = action.request.url { UIApplication.shared.open(url) }
            return nil
        }

        func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
            let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
            UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }.first?.present(a, animated: true)
        }

        func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
            let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "Отмена", style: .cancel) { _ in completionHandler(false) })
            a.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler(true) })
            UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }.first?.present(a, animated: true)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            // нет сети: пробуем ещё раз через пару секунд
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { webView.load(URLRequest(url: WebContainer.site)) }
        }
    }
}
