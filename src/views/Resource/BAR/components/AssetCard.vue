<template>
  <div class="asset-card" :class="`status-${capability.status}`">
    <div class="card-header">
      <div class="site-info">
        <h3 class="site-name">{{ site.name }}</h3>
        <el-tag v-if="capability.status === 'available'" type="success" size="large">
          可投标
        </el-tag>
        <el-tag v-else-if="capability.status === 'risk'" type="warning" size="large">
          有风险
        </el-tag>
        <el-tag v-else type="danger" size="large">
          不可投标
        </el-tag>
      </div>
      <a v-if="site.url" :href="site.url" target="_blank" class="site-link">
        <el-icon><Link /></el-icon>
        {{ site.url }}
      </a>
      <span v-else class="site-link site-link-muted">未登记网址</span>
    </div>

    <div class="card-body">
      <!-- 账号状态 -->
      <div class="status-item">
        <el-icon v-if="capability.hasAccount" class="icon-success"><CircleCheck /></el-icon>
        <el-icon v-else class="icon-error"><CircleClose /></el-icon>
        <span class="label">账号：</span>
        <span v-if="capability.hasAccount">已注册 ({{ capability.accountCount }}个)</span>
        <span v-else class="text-error">未注册</span>
      </div>

      <!-- 登录方式 -->
      <div class="status-item">
        <el-icon class="icon-info"><Lock /></el-icon>
        <span class="label">登录：</span>
        <span>{{ getLoginTypeText(site.loginType) }}</span>
      </div>

      <!-- UK状态 -->
      <div class="status-item">
        <el-icon v-if="capability.hasAvailableUK" class="icon-success"><CircleCheck /></el-icon>
        <el-icon v-else-if="capability.ukCount > 0" class="icon-warning"><Warning /></el-icon>
        <el-icon v-else class="icon-error"><CircleClose /></el-icon>
        <span class="label">UK需求：</span>
        <span v-if="capability.ukCount === 0">不需要</span>
        <span v-else>
          <template v-for="(uk, index) in site.uks" :key="uk.id">
            {{ uk.type }}{{ uk.status === 'available' ? ' ✅' : uk.status === 'borrowed' ? ' ⚠️借出' : ' ❌' }}
            <template v-if="uk.status === 'borrowed'">(借:{{ uk.borrower }})</template>
            <template v-if="index < site.uks.length - 1">、</template>
          </template>
        </span>
      </div>

      <!-- 责任人 -->
      <div class="status-item" v-if="capability.primaryOwner">
        <el-icon class="icon-user"><User /></el-icon>
        <span class="label">责任人：</span>
        <span>{{ capability.primaryOwner }} ({{ capability.primaryPhone }})</span>
      </div>

      <!-- 风险提示 -->
      <div class="risk-alert" v-if="capability.hasRisk">
        <el-icon class="icon-warning"><Warning /></el-icon>
        <span v-html="getRiskText(site)"></span>
      </div>
    </div>

    <div class="card-footer">
      <el-button type="primary" @click="handleViewDetail">查看详情</el-button>
      <el-button v-if="capability.ukCount > 0" @click="handleBorrow">借用UK</el-button>
      <el-button @click="handleContact">联系责任人</el-button>
      <el-button link @click="handleViewSOP">查看SOP</el-button>
    </div>
  </div>
</template>

<script setup>
import { Link, CircleCheck, CircleClose, Lock, User, Warning } from '@element-plus/icons-vue'

const props = defineProps({
  site: {
    type: Object,
    required: true
  },
  capability: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['view-detail', 'borrow', 'contact', 'view-sop'])

const getLoginTypeText = (type) => {
  const map = {
    'password': '密码登录',
    'ca': 'CA登录',
    'both': '密码 + CA双认证'
  }
  return map[type] || type
}

const getRiskText = (site) => {
  const risks = []

  // 检查UK是否即将过期
  if (site.uks) {
    for (const uk of site.uks) {
      if (uk.expiryDate) {
        const daysLeft = Math.ceil((new Date(uk.expiryDate) - new Date()) / (1000 * 60 * 60 * 24))
        if (daysLeft <= 30 && daysLeft > 0) {
          risks.push(`${uk.type} ${daysLeft}天后过期`)
        } else if (daysLeft <= 0) {
          risks.push(`${uk.type} 已过期`)
        }
      }
    }
  }

  // 检查账号状态
  if (site.accounts) {
    const inactiveAccounts = site.accounts.filter(a => a.status === 'inactive')
    if (inactiveAccounts.length > 0) {
      risks.push(`账号异常: ${inactiveAccounts.map(a => a.username).join(', ')}`)
    }
  }

  return risks.join('；') || '存在风险项'
}

const handleViewDetail = () => emit('view-detail', props.site)
const handleBorrow = () => emit('borrow', props.site)
const handleContact = () => emit('contact', props.site)
const handleViewSOP = () => emit('view-sop', props.site)
</script>

<style scoped>
.asset-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  background: var(--bg-card);
  transition: all 0.3s ease;
}

.asset-card.status-available {
  border-color: #67c23a;
  box-shadow: 0 2px 12px rgba(103, 194, 58, 0.15);
}

.asset-card.status-risk {
  border-color: #e6a23c;
  box-shadow: 0 2px 12px rgba(230, 162, 60, 0.15);
}

.asset-card.status-unavailable {
  border-color: #f56c6c;
  box-shadow: 0 2px 12px rgba(245, 108, 108, 0.15);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.site-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.site-name {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
  word-break: break-word;
}

.site-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-muted);
  font-size: 13px;
  text-decoration: none;
  transition: color 0.2s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.site-link:hover {
  color: #409eff;
}

.site-link-muted {
  color: #c0c4cc;
}

.card-body {
  margin-bottom: 16px;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 14px;
}

.status-item .label {
  color: var(--text-secondary-ui);
  font-weight: 500;
}

.icon-success {
  color: #67c23a;
  font-size: 18px;
}

.icon-error {
  color: #f56c6c;
  font-size: 18px;
}

.icon-warning {
  color: #e6a23c;
  font-size: 18px;
}

.icon-info {
  color: #409eff;
  font-size: 18px;
}

.icon-user {
  color: var(--text-muted);
  font-size: 18px;
}

.text-error {
  color: #f56c6c;
}

.risk-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 12px;
  background: #fef0e6;
  border-left: 3px solid #e6a23c;
  border-radius: 4px;
  font-size: 13px;
  color: #e6a23c;
}

.card-footer {
  display: flex;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.card-footer .el-button {
  flex: 1;
  min-width: 80px;
}

.card-footer .el-button {
  flex: 1;
  min-width: 100px;
}
</style>
