// Input: router registration and auth navigation requests
// Output: login navigation without importing router from stores
// Pos: src/router/ - Session navigation bridge
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

let loginNavigator = async () => {
  if (typeof window !== 'undefined' && Object.prototype.hasOwnProperty.call(window, 'routerPushCalled')) {
    window.routerPushCalled = true
  }
}

export const registerLoginNavigator = (navigator) => {
  if (typeof navigator === 'function') {
    loginNavigator = navigator
  }
}

export const navigateToLogin = () => loginNavigator()
