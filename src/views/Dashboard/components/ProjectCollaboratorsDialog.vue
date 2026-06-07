<template>
  <el-dialog
    v-model="visible"
    :title="`项目协作管理 - ${project?.name || ''}`"
    width="650px"
    destroy-on-close
    append-to-body
  >
    <div class="collaboration-dialog">
      <div class="add-member-section">
        <el-form :inline="true" :model="form" class="member-form">
          <el-form-item label="添加成员">
            <el-select
              v-model="form.userId"
              filterable
              remote
              placeholder="搜索人员..."
              :remote-method="searchUsers"
              :loading="searching"
              style="width: 200px"
            >
              <el-option
                v-for="user in userOptions"
                :key="user.id"
                :label="user.fullName"
                :value="user.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="form.memberRole" style="width: 140px">
              <el-option label="技术专家" value="TECHNICAL_EXPERT" />
              <el-option label="商务协助" value="COMMERCIAL_SUPPORT" />
              <el-option label="法务风控" value="LEGAL" />
              <el-option label="财务初审" value="FINANCE" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="adding" @click="handleAddMember">添加</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-divider content-position="left">项目组成员</el-divider>

      <el-table :data="members" border stripe style="width: 100%" v-loading="loading">
        <el-table-column prop="fullName" label="姓名" width="120" />
        <el-table-column prop="memberRole" label="项目职责" width="150">
          <template #default="{ row }">
            <el-tag size="small">{{ formatRole(row.memberRole) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="permissionLevel" label="权限" width="100" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button
              type="danger"
              link
              :disabled="row.isInherited"
              @click="handleRemoveMember(row)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectsApi } from '@/api'

const props = defineProps({
  modelValue: Boolean,
  project: Object
})

const emit = defineEmits(['update:modelValue', 'changed'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const members = ref([])
const loading = ref(false)
const adding = ref(false)
const searching = ref(false)
const userOptions = ref([])

const form = ref({
  userId: null,
  memberRole: 'TECHNICAL_EXPERT',
  permissionLevel: 'VIEWER'
})

const loadMembers = async () => {
  if (!props.project?.id) return
  loading.value = true
  try {
    const res = await projectsApi.getMembers(props.project.id)
    if (res.success) {
      members.value = res.data
    }
  } catch (err) {
    ElMessage.error('加载成员失败')
  } finally {
    loading.value = false
  }
}

const searchUsers = async (query) => {
  if (!query || query.length < 2) return
  searching.value = true
  try {
    // Lazy import keeps `usersApi` out of this dialog's eager module graph,
    // so component-mount tests that mock `@/api` don't have to also mock the
    // transitive client.js + router import chain reached via @/api/modules/users.js.
    const { usersApi } = await import('@/api/modules/users.js')
    // usersApi.search already unwraps the {success, data} envelope and returns the array directly.
    // Backend UserSearchResult: { id, name, role }; UI el-option label uses `fullName`.
    const list = await usersApi.search(query)
    userOptions.value = Array.isArray(list)
      ? list.map((u) => ({ id: u.id, fullName: u.name, role: u.role }))
      : []
  } catch (err) {
    userOptions.value = []
    ElMessage.error('搜索成员失败')
  } finally {
    searching.value = false
  }
}

const handleAddMember = async () => {
  if (!form.value.userId) return ElMessage.warning('请选择人员')
  adding.value = true
  try {
    const res = await projectsApi.addMember(props.project.id, form.value)
    if (res.success) {
      ElMessage.success('添加成功')
      form.value.userId = null
      await loadMembers()
      emit('changed')
    }
  } catch (err) {
    ElMessage.error('添加失败')
  } finally {
    adding.value = false
  }
}

const handleRemoveMember = async (member) => {
  try {
    await ElMessageBox.confirm(`确定要移除 ${member.fullName} 吗？`, '提示', { type: 'warning' })
    const res = await projectsApi.removeMember(props.project.id, member.userId)
    if (res.success) {
      ElMessage.success('已移除')
      await loadMembers()
      emit('changed')
    }
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('移除失败')
  }
}

const formatRole = (role) => {
  return {
    TECHNICAL_EXPERT: '技术专家',
    COMMERCIAL_SUPPORT: '商务协助',
    LEGAL: '法务风控',
    FINANCE: '财务初审'
  }[role] || role
}

watch(() => props.project?.id, (newVal) => {
  if (newVal) loadMembers()
}, { immediate: true })
</script>

<style scoped>
.collaboration-dialog {
  padding: 10px 0;
}
.member-form {
  margin-bottom: 20px;
}
</style>
