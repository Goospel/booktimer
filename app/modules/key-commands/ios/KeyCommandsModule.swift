import ExpoModulesCore
import UIKit

// Option+P = 그 화면의 시작·종료 버튼(범위 보충 REQ-105). 언제 켜고 끄나 · 무엇을 누르나는 JS(PR-P) 몫이다.
// 키 윈도 루트 뷰 컨트롤러에 UIKeyCommand를 붙인다 — 키 명령은 first responder에서 위로 올라가는 응답자 사슬에서 찾으므로
// 입력 포커스를 빼앗지 않는다(추론 — U-96). 숨은 뷰를 becomeFirstResponder로 세우는 방식(expo-key-event)은 포커스를 빼앗아 기각했다.
// RN 뷰는 보통 first responder가 되지 않는다 — 홈처럼 입력 칸이 없는 화면에서 이 명령이 찾아지는지가 U-96 ①이다(추론).
public final class KeyCommandsModule: Module {
  static weak var current: KeyCommandsModule?
  private var commands: [UIKeyCommand] = []
  private weak var host: UIViewController?

  public func definition() -> ModuleDefinition {
    Name("KeyCommands")
    Events("onMeasureShortcut")

    OnCreate {
      KeyCommandsModule.current = self
    }

    OnDestroy {
      DispatchQueue.main.async { [weak self] in
        MainActor.assumeIsolated { self?.apply(false) }
      }
    }

    // true면 지금 키 윈도에 다시 붙인다(창 · 루트가 바뀌었을 수 있다). false면 뗀다.
    // runOnQueue(.main)이 메인 스레드를 보장한다 — keyWindow()가 @MainActor라 격리를 컴파일러에 알린다(ExpoModulesCore currentViewController()와 같은 꼴).
    AsyncFunction("setEnabled") { (enabled: Bool) in
      MainActor.assumeIsolated { self.apply(enabled) }
    }.runOnQueue(.main)
  }

  @MainActor private func apply(_ enabled: Bool) {
    if let host {
      commands.forEach { host.removeKeyCommand($0) }
    }
    commands = []
    host = nil
    guard enabled, let root = Utilities.keyWindow()?.rootViewController else { return }
    // "p"(영문) + "ㅔ"(두벌식 한글 — 같은 P 자리). 한글 입력 소스에서 자동 현지화가 안 맞을 때의 대비(U-96).
    // 둘째는 제목을 비워 ⌘ 단축키 안내 목록에 두 번 뜨지 않게 한다.
    commands = [Self.command(input: "p", title: "측정 시작·종료"), Self.command(input: "ㅔ", title: "")]
    commands.forEach { root.addKeyCommand($0) }
    host = root
  }

  @MainActor private static func command(input: String, title: String) -> UIKeyCommand {
    let command = UIKeyCommand(title: title, action: #selector(UIViewController.bt_measureShortcut(_:)),
                               input: input, modifierFlags: .alternate)
    command.wantsPriorityOverSystemBehavior = true
    return command
  }

  fileprivate func fire() {
    sendEvent("onMeasureShortcut")
  }
}

extension UIViewController {
  // 응답자 사슬에서 이 메서드를 먼저 가진 뷰 컨트롤러가 받는다 — 어느 컨트롤러가 받아도 같은 이벤트 하나다.
  @objc func bt_measureShortcut(_ sender: UIKeyCommand) {
    KeyCommandsModule.current?.fire()
  }
}
