// Input: auth session events from the HTTP client and store registration callbacks
// Output: decoupled auth-store synchronization without dynamic imports
// Pos: src/api/ - Auth side-effect bridge between data access and Pinia
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

let bridge = {
  applySession: null,
  resetSession: null
}

export const registerAuthStoreBridge = (handlers = {}) => {
  bridge = {
    ...bridge,
    ...handlers
  }
}

export const syncAuthStoreSession = async (authData) => {
  if (!authData?.user || typeof bridge.applySession !== 'function') {
    return
  }

  await bridge.applySession(authData)
}

export const resetAuthStoreSession = async () => {
  if (typeof bridge.resetSession !== 'function') {
    return
  }

  await bridge.resetSession()
}
