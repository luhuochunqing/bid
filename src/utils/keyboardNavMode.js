// Input: browser window and document objects
// Output: installs keyboard navigation mode markers for focus styling
// Pos: src/utils/ - shared browser interaction utility
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const POINTER_EVENTS = ['mousedown', 'pointerdown', 'touchstart']

export function installKeyboardNavMode({ windowRef = window, documentRef = document } = {}) {
  const root = documentRef.documentElement
  const enableKeyboardMode = (event) => {
    if (event.key === 'Tab') {
      root.dataset.keyboardNav = 'true'
    }
  }
  const disableKeyboardMode = () => {
    delete root.dataset.keyboardNav
  }

  windowRef.addEventListener('keydown', enableKeyboardMode, true)
  for (const eventName of POINTER_EVENTS) {
    windowRef.addEventListener(eventName, disableKeyboardMode, true)
  }

  return () => {
    windowRef.removeEventListener('keydown', enableKeyboardMode, true)
    for (const eventName of POINTER_EVENTS) {
      windowRef.removeEventListener(eventName, disableKeyboardMode, true)
    }
  }
}
