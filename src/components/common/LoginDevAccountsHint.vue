<template>
  <div class="test-accounts">
    <div class="test-header">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <path d="M12 16v-4"/>
        <path d="M12 8h.01"/>
      </svg>
      <span>登录提示</span>
    </div>
    <div class="account-list">
      <span v-for="account in DEV_ACCOUNTS" :key="account" class="account-tag">
        {{ account }}
      </span>
    </div>
    <p class="test-hint">{{ DEV_HINT }}</p>
  </div>
</template>

<script setup>
const DEV_ACCOUNTS = [
  '管理员: admin / XiyuAdmin2026!',
  '审计员: auditor / Test@123',
  '经理: manager / Test@123',
  '投标部门管理员: /bidAdmin / Test@123',
  '投标组长: bid-TeamLeader / Test@123',
  '项目负责人: bid-projectLeader / Test@123',
  '投标专员: bid-Team / Test@123',
  '行政人员: bid-administration / Test@123'
]
const DEV_HINT = '本地测试账号已按角色权限划分，方便实际测试'
</script>

<style scoped>
.test-accounts {
  padding: 20px;
  background: linear-gradient(135deg, rgba(3, 105, 161, 0.05) 0%, rgba(3, 105, 161, 0.02) 100%);
  border: 1px solid rgba(3, 105, 161, 0.1);
  border-radius: 12px;
}

.test-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--accent-blue);
  font-size: 13px;
  font-weight: 500;
}

.test-header svg {
  width: 16px;
  height: 16px;
}

.account-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.account-tag {
  display: inline-block;
  padding: 4px 12px;
  background: var(--bg-card);
  border: 1px solid rgba(3, 105, 161, 0.2);
  border-radius: 16px;
  font-size: 12px;
  color: var(--accent-blue);
  font-weight: 500;
}

.test-hint {
  font-size: 12px;
  color: var(--text-slate);
  margin: 0;
}
</style>
