App({
  onLaunch() {
    // 小程序初始化
    console.log('西域投标小程序启动');
  },
  globalData: {
    apiBaseUrl: 'http://127.0.0.1:18080',
    userInfo: null,
    token: null
  }
});
