import UIKit

final class ScreenWakeController {
    func setActiveMatch(_ isActive: Bool) {
        UIApplication.shared.isIdleTimerDisabled = isActive
    }
}

