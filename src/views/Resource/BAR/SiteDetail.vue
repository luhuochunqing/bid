<template>
  <div class="bar-site-detail" v-loading="loading">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <el-button link @click="$router.push('/resource/bar/sites')">
          <el-icon><Back /></el-icon>
          返回列表
        </el-button>
        <div class="site-title">
          <h2>{{ site?.name }}</h2>
          <el-tag v-if="site?.status === 'active'" type="success">正常</el-tag>
          <el-tag v-else type="danger">异常</el-tag>
        </div>
        <p class="site-url">{{ site?.url }}</p>
      </div>
      <div class="header-actions">
        <el-button @click="handleEdit">
          <el-icon><Edit /></el-icon>
          编辑
        </el-button>
        <el-button type="danger" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          删除
        </el-button>
      </div>
    </div>

    <template v-if="site">
      <!-- 基本信息 -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <span class="card-title">基本信息</span>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="地区">{{ site.region }}</el-descriptions-item>
          <el-descriptions-item label="行业">{{ site.industry }}</el-descriptions-item>
          <el-descriptions-item label="站点类型">{{ site.siteType }}</el-descriptions-item>
          <el-descriptions-item label="登录方式">
            <el-tag size="small">{{ getLoginTypeText(site.loginType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="注册状态">
            <el-tag :type="site.status === 'active' ? 'success' : 'danger'" size="small">
              {{ site.status === 'active' ? '已注册' : '未注册/异常' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="最近验证">{{ site.lastVerifyTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ site.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 账号管理 -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">账号管理</span>
            <el-button type="primary" size="small" @click="showAccountDialog = true">
              <el-icon><Plus /></el-icon>
              添加账号
            </el-button>
          </div>
        </template>
        <el-table :data="site.accounts" stripe size="small">
          <el-table-column prop="username" label="用户名" width="150" />
          <el-table-column label="角色" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ getRoleText(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="owner" label="责任人" width="100" />
          <el-table-column prop="phone" label="手机号" width="130" />
          <el-table-column prop="email" label="邮箱" min-width="150" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
                {{ row.status === 'active' ? '正常' : '异常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="editAccount(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="deleteAccount(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!site.accounts || site.accounts.length === 0" description="暂无账号" :image-size="80" />
      </el-card>

      <!-- UK/CA管理 -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">UK/CA管理</span>
            <el-button type="primary" size="small" @click="showUkDialog = true">
              <el-icon><Plus /></el-icon>
              添加UK
            </el-button>
          </div>
        </template>
        <el-table :data="site.uks" stripe size="small">
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column prop="provider" label="厂商" width="100" />
          <el-table-column prop="serialNo" label="序列号" width="150" />
          <el-table-column prop="holder" label="持有人" width="100" />
          <el-table-column prop="expiryDate" label="有效期" width="110">
            <template #default="{ row }">
              <span :class="{ 'text-warning': isExpiringSoon(row.expiryDate) }">
                {{ row.expiryDate }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="location" label="存放位置" width="120" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag
                :type="row.status === 'available' ? 'success' : row.status === 'borrowed' ? 'warning' : 'danger'"
                size="small"
              >
                {{ getUkStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'available'"
                link
                type="primary"
                size="small"
                @click="borrowUk(row)"
              >
                借用
              </el-button>
              <el-button
                v-else-if="row.status === 'borrowed'"
                link
                type="success"
                size="small"
                @click="returnUk(row)"
              >
                归还
              </el-button>
              <el-button link type="primary" size="small" @click="editUk(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="deleteUk(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!site.uks || site.uks.length === 0" description="暂无UK" :image-size="80" />
      </el-card>

      <!-- 找回SOP -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">找回SOP</span>
            <el-button link type="primary" @click="goToSOP">编辑SOP →</el-button>
          </div>
        </template>
        <div v-if="site.sop" class="sop-preview">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="找回入口" :span="2">
              <a v-if="site.sop.resetUrl" :href="site.sop.resetUrl" target="_blank" class="sop-link">
                {{ site.sop.resetUrl }}
              </a>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="所需材料" :span="2">
              <el-tag
                v-for="(doc, index) in site.sop.requiredDocs"
                :key="index"
                size="small"
                style="margin-right: 8px; margin-bottom: 4px;"
              >
                {{ doc.name }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="联系方式" :span="2">
              {{ site.sop.contacts?.join('、') || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="预计时长">
              {{ site.sop.estimatedTime || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="历史处理">
              {{ site.sop.history?.length || 0 }} 次
            </el-descriptions-item>
          </el-descriptions>
        </div>
        <el-empty v-else description="暂无SOP信息" :image-size="80" />
      </el-card>

      <!-- 附件材料 -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">附件材料</span>
            <el-button type="primary" size="small" @click="handleUploadAttachment">上传</el-button>
          </div>
        </template>
        <div class="attachment-list">
          <div
            v-for="file in site.attachments"
            :key="file.id"
            class="attachment-item"
          >
            <el-icon class="file-icon"><Document /></el-icon>
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size">{{ file.size }}</span>
          </div>
        </div>
        <el-empty v-if="!site.attachments || site.attachments.length === 0" description="暂无附件" :image-size="80" />
      </el-card>

      <!-- 操作记录 -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <span class="card-title">操作记录</span>
        </template>
        <el-timeline>
          <el-timeline-item
            v-for="(log, index) in site.auditLog"
            :key="index"
            :timestamp="log.time"
            placement="top"
          >
            {{ log.user }} {{ log.action }}
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="!site.auditLog || site.auditLog.length === 0" description="暂无操作记录" :image-size="80" />
      </el-card>
    </template>

    <!-- 账号弹窗 -->
    <el-dialog v-model="showAccountDialog" :title="editingAccount ? '编辑账号' : '添加账号'" width="500px">
      <el-form :model="accountForm" :rules="accountRules" ref="accountFormRef" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="accountForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="accountForm.role" placeholder="请选择角色">
            <el-option label="管理员" value="admin" />
            <el-option label="经办人" value="operator" />
            <el-option label="查看者" value="viewer" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人" prop="owner">
          <el-input v-model="accountForm.owner" placeholder="请输入责任人姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="accountForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="accountForm.email" placeholder="请输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAccountDialog = false">取消</el-button>
        <el-button type="primary" @click="saveAccount">保存</el-button>
      </template>
    </el-dialog>

    <!-- UK弹窗 -->
    <el-dialog v-model="showUkDialog" :title="editingUk ? '编辑UK' : '添加UK'" width="500px">
      <el-form :model="ukForm" :rules="ukRules" ref="ukFormRef" label-width="100px">
        <el-form-item label="类型" prop="type">
          <el-input v-model="ukForm.type" placeholder="如 法人CA" />
        </el-form-item>
        <el-form-item label="厂商" prop="provider">
          <el-input v-model="ukForm.provider" placeholder="如 北京CA" />
        </el-form-item>
        <el-form-item label="序列号" prop="serialNo">
          <el-input v-model="ukForm.serialNo" placeholder="请输入序列号" />
        </el-form-item>
        <el-form-item label="持有人" prop="holder">
          <el-input v-model="ukForm.holder" placeholder="请输入持有人" />
        </el-form-item>
        <el-form-item label="存放位置">
          <el-input v-model="ukForm.location" placeholder="如 保险柜A-1" />
        </el-form-item>
        <el-form-item label="有效期" prop="expiryDate">
          <el-date-picker
            v-model="ukForm.expiryDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUkDialog = false">取消</el-button>
        <el-button type="primary" @click="saveUk">保存</el-button>
      </template>
    </el-dialog>

    <!-- 借用弹窗 -->
    <BorrowDialog
      v-model="showBorrowDialog"
      :site="site"
      :uk="currentUk"
      @confirm="handleBorrowConfirm"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useBarStore } from '@/stores/bar'
import {
  Back, Edit, Delete, Plus, Document
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BorrowDialog from './components/BorrowDialog.vue'

const router = useRouter()
const route = useRoute()
const barStore = useBarStore()

const loading = ref(false)
const site = ref(null)
const showAccountDialog = ref(false)
const showUkDialog = ref(false)
const showBorrowDialog = ref(false)
const editingAccount = ref(null)
const editingUk = ref(null)
const currentUk = ref(null)

const accountFormRef = ref(null)
const ukFormRef = ref(null)

const accountForm = ref({
  username: '',
  role: 'admin',
  owner: '',
  phone: '',
  email: '',
  status: 'active'
})

const accountRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  owner: [{ required: true, message: '请输入责任人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }]
}

const ukForm = ref({
  type: '',
  provider: '',
  serialNo: '',
  holder: '',
  location: '',
  expiryDate: '',
  status: 'available'
})

const ukRules = {
  type: [{ required: true, message: '请输入类型', trigger: 'blur' }],
  provider: [{ required: true, message: '请输入厂商', trigger: 'blur' }],
  serialNo: [{ required: true, message: '请输入序列号', trigger: 'blur' }],
  holder: [{ required: true, message: '请输入持有人', trigger: 'blur' }],
  expiryDate: [{ required: true, message: '请选择有效期', trigger: 'change' }]
}

const getLoginTypeText = (type) => {
  const map = {
    'password': '密码登录',
    'ca': 'CA登录',
    'both': '密码+CA'
  }
  return map[type] || type
}

const getRoleText = (role) => {
  const map = {
    'admin': '管理员',
    'operator': '经办人',
    'viewer': '查看者'
  }
  return map[role] || role
}

const getUkStatusText = (status) => {
  const map = {
    'available': '在库',
    'borrowed': '借出',
    'expired': '过期'
  }
  return map[status] || status
}

const isExpiringSoon = (date) => {
  if (!date) return false
  const daysLeft = Math.ceil((new Date(date) - new Date()) / (1000 * 60 * 60 * 24))
  return daysLeft <= 30 && daysLeft > 0
}

const refreshSite = async () => {
  const latestSite = await barStore.getSiteById(route.params.id)
  site.value = latestSite
}

const handleEdit = async () => {
  if (!site.value) return
  const nextRemark = `${site.value.remark || '维护记录'}；${new Date().toLocaleDateString('zh-CN')} 已完成站点信息校正`
  const response = await barStore.updateSite(site.value.id, {
    ...site.value,
    remark: nextRemark })
  if (!response?.success) {
    ElMessage.error(response?.msg || '站点更新失败')
    return
  }
  site.value.auditLog = Array.isArray(site.value.auditLog) ? site.value.auditLog : []
  site.value.auditLog.unshift({
    time: new Date().toLocaleString('zh-CN', { hour12: false }),
    user: '李总',
    action: '更新了站点基础信息'
  })
  await refreshSite()
  ElMessage.success(`已保存站点「${site.value?.name || ''}」修改`)
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除站点"${site.value?.name}"吗？`,
      '确认删除',
      { type: 'warning' }
    )
    const response = await barStore.deleteSite(site.value.id)
    if (!response?.success) {
      ElMessage.error(response?.msg || '删除失败')
      return
    }
    ElMessage.success('删除成功')
    router.push('/resource/bar/sites')
  } catch {
    // 用户取消
  }
}

const editAccount = (account) => {
  editingAccount.value = account
  accountForm.value = { ...account }
  showAccountDialog.value = true
}

const deleteAccount = async (account) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除账号"${account.username}"吗？`,
      '确认删除',
      { type: 'warning' }
    )
    const response = await barStore.deleteAccount(site.value.id, account.id)
    if (!response?.success) {
      ElMessage.error(response?.msg || '删除账号失败')
      return
    }
    await refreshSite()
    ElMessage.success('删除成功')
  } catch {
    // 用户取消
  }
}

const saveAccount = async () => {
  try {
    await accountFormRef.value.validate()

    const response = editingAccount.value
      ? await barStore.updateAccount(site.value.id, editingAccount.value.id, accountForm.value)
      : await barStore.addAccount(site.value.id, accountForm.value)

    if (!response?.success) {
      ElMessage.error(response?.msg || '保存账号失败')
      return
    }

    ElMessage.success(editingAccount.value ? '更新成功' : '添加成功')

    showAccountDialog.value = false
    editingAccount.value = null
    accountForm.value = {
      username: '',
      role: 'admin',
      owner: '',
      phone: '',
      email: '',
      status: 'active'
    }
    await refreshSite()
  } catch {
    // 表单验证失败
  }
}

const editUk = (uk) => {
  editingUk.value = uk
  ukForm.value = { ...uk }
  showUkDialog.value = true
}

const deleteUk = async (uk) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除UK"${uk.type}"吗？`,
      '确认删除',
      { type: 'warning' }
    )
    const response = await barStore.deleteUk(site.value.id, uk.id)
    if (!response?.success) {
      ElMessage.error(response?.msg || '删除 UK 失败')
      return
    }
    await refreshSite()
    ElMessage.success('删除成功')
  } catch {
    // 用户取消
  }
}

const saveUk = async () => {
  try {
    await ukFormRef.value.validate()

    const response = editingUk.value
      ? await barStore.updateUk(site.value.id, editingUk.value.id, ukForm.value)
      : await barStore.addUk(site.value.id, ukForm.value)

    if (!response?.success) {
      ElMessage.error(response?.msg || '保存 UK 失败')
      return
    }

    ElMessage.success(editingUk.value ? '更新成功' : '添加成功')

    showUkDialog.value = false
    editingUk.value = null
    ukForm.value = {
      type: '',
      provider: '',
      serialNo: '',
      holder: '',
      location: '',
      expiryDate: '',
      status: 'available'
    }
    await refreshSite()
  } catch {
    // 表单验证失败
  }
}

const borrowUk = (uk) => {
  currentUk.value = uk
  showBorrowDialog.value = true
}

const returnUk = async (uk) => {
  try {
    await ElMessageBox.confirm(
      `确认归还 "${uk.type}" 吗？`,
      '确认归还',
      { type: 'info' }
    )
    const response = await barStore.returnUk(site.value.id, uk.id)
    if (!response?.success) {
      ElMessage.error(response?.msg || '归还失败')
      return
    }
    await refreshSite()
    ElMessage.success('归还成功')
  } catch {
    // 用户取消
  }
}

const handleBorrowConfirm = async (data) => {
  const response = await barStore.borrowUk(site.value.id, data.ukId, data)
  if (!response?.success) {
    ElMessage.error(response?.msg || '借用申请提交失败')
    return
  }
  await refreshSite()
  ElMessage.success('借用成功')
}

const goToSOP = () => {
  router.push(`/resource/bar/sop/${site.value.id}`)
}

const handleUploadAttachment = async () => {
  if (!site.value) return
  const response = await barStore.addAttachment(site.value.id, {
    name: `${site.value.name}_附件_${Date.now()}.pdf`,
    size: '128KB',
    contentType: 'application/pdf',
    uploadedBy: '李总',
    url: '' })
  if (!response?.success) {
    ElMessage.error(response?.msg || '附件上传失败')
    return
  }
  await refreshSite()
  ElMessage.success('附件已上传')
}

onMounted(async () => {
  loading.value = true
  const response = await barStore.getSites()
  if (!response?.success) {
    ElMessage.error(response?.msg || 'BAR 站点数据加载失败')
  }
  await refreshSite()
  loading.value = false
})
</script>

<style scoped>
.bar-site-detail {
  padding: 20px;
}

.page-header {
  margin-bottom: 24px;
}

.header-left .site-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0;
}

.header-left h2 {
  font-size: 22px;
  font-weight: 600;
  margin: 0;
}

.header-left .site-url {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.info-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
}

.sop-preview .sop-link {
  color: #409eff;
  text-decoration: none;
}

.sop-preview .sop-link:hover {
  text-decoration: underline;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-subtle);
  border-radius: 4px;
}

.attachment-item .file-icon {
  color: #409eff;
}

.attachment-item .file-name {
  font-size: 14px;
  color: var(--gray-750);
}

.attachment-item .file-size {
  font-size: 12px;
  color: var(--text-muted);
}

.text-warning {
  color: #e6a23c;
}
</style>
