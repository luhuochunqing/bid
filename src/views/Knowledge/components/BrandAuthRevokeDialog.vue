<template>
  <el-dialog v-model="visible" title="作废授权" width="480px">
    <el-alert type="error" title="作废操作不可撤销" :closable="false" show-icon style="margin-bottom:16px" />
    <el-form :model="form">
      <el-form-item label="作废原因" required>
        <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请填写作废原因（不低于10个字）" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="danger" :disabled="(form.reason||'').trim().length < 10" :loading="revoking" @click="confirmRevoke">确认作废</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import brandAuthApi from '@/api/modules/brandAuth.js'

const props = defineProps({ modelValue: Boolean, target: Object })
const emit = defineEmits(['update:modelValue', 'done'])
const visible = ref(false); const revoking = ref(false)
const form = reactive({ reason: '' })
watch(() => props.modelValue, (v) => { visible.value = v; if (v) form.reason = '' })
watch(visible, (v) => emit('update:modelValue', v))

const confirmRevoke = async () => {
  if (!props.target) return
  revoking.value = true
  try {
    await brandAuthApi.revoke(props.target.id, form.reason)
    ElMessage.success('已作废'); visible.value = false; emit('done')
  } catch (e) { ElMessage.error(e.response?.data?.message || '作废失败') }
  finally { revoking.value = false }
}
</script>
