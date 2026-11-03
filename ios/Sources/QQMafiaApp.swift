import SwiftUI

@main
struct QQMafiaApp: App {
    var body: some Scene {
        WindowGroup {
            WebContainer()
                .ignoresSafeArea(.keyboard)
                .background(Color(red: 0.059, green: 0, blue: 0.004).ignoresSafeArea())
                .preferredColorScheme(.dark)
        }
    }
}
