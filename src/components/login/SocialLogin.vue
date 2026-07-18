<template>
  <div class="social-login">
    <div class="divider">
      <span>其他登录方式</span>
    </div>
    <el-button
      class="wecom-button"
      @click="handleWeComLogin"
    >
      <template #icon>
        <img src="/wecom-icon.png" alt="WeCom" class="wecom-icon-img">
      </template>
      企业微信登录
    </el-button>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/modules/auth'

const handleWeComLogin = async () => {
  try {
    const { data } = await authApi.getWeComAuthorizeParams()
    const { state, appid, agentid } = data
    
    if (!appid || !agentid) {
      ElMessage.error('企业微信集成配置不完整，请联系管理员')
      return
    }

    const redirectUri = encodeURIComponent(window.location.origin + '/login')
    // 企微工作台跳转 + 浏览器扫码兼容的 OAuth2 授权链接（snsapi_base 静默授权）
    const wecomUrl = `https://open.weixin.qq.com/connect/oauth2/authorize?appid=${appid}&redirect_uri=${redirectUri}&response_type=code&scope=snsapi_base&agentid=${agentid}&state=${state}#wechat_redirect`

    window.location.href = wecomUrl
  } catch (error) {
    ElMessage.error('无法启动企业微信登录，请联系管理员')
  }
}
</script>

<style scoped>
.social-login {
  margin-top: 32px;
}

.divider {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--gray-200);
}

.divider span {
  padding: 0 16px;
  font-size: 13px;
  color: var(--gray-400);
}

.wecom-button {
  width: 100%;
  height: 44px;
  border: 1px solid var(--gray-200);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--sidebar-text);
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  transition: all 0.2s ease;
}

.wecom-button:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #0F172A;
}

.wecom-icon-img {
  width: 20px;
  height: 20px;
}
</style>
