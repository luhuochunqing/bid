// Input: router registration and auth navigation requests
// Output: resilient login navigation without importing router from stores
// Pos: src/router/ - Session navigation bridge
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

let loginNavigator = async () => {
  if (typeof window !== 'undefined' && Object.prototype.hasOwnProperty.call(window, 'routerPushCalled')) {
    window.routerPushCalled = true
  }
}

const hardRedirectToLogin = () => {
  if (typeof window === 'undefined' || window.location.pathname === '/login') {
    return
  }

  window.location.assign('/login')
}

export const registerLoginNavigator = (navigator) => {
  if (typeof navigator === 'function') {
    loginNavigator = navigator
  }
}

export const navigateToLogin = async () => {
  try {
    await loginNavigator()
  } catch (error) {
    console.warn('Navigation to login failed, falling back to hard redirect:', error)
    hardRedirectToLogin()
  }
}
