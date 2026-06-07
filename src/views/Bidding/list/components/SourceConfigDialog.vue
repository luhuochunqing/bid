<template>
  <el-dialog
    v-model="modelValue"
    title="外部标讯源配置"
    width="640px"
  >
    <el-form :model="sourceConfig" label-width="120px">
      <el-form-item label="标讯源平台">
        <el-checkbox-group v-model="sourceConfig.platforms">
          <el-checkbox v-for="platform in sourcePlatforms" :key="platform" :label="platform" :value="platform" />
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="API端点">
        <el-input v-model="sourceConfig.apiEndpoint" placeholder="输入第三方API端点地址" />
      </el-form-item>
      <el-form-item label="API密钥">
        <el-input v-model="sourceConfig.apiKey" type="password" placeholder="仅本次会话使用，不会写入本地存储" show-password />
      </el-form-item>
      <el-form-item label="关键字">
        <el-select v-model="sourceConfig.keywords" multiple allow-create filterable placeholder="选择或输入关键字" class="full-width">
          <el-option v-for="keyword in keywordOptions" :key="keyword" :label="keyword" :value="keyword" />
        </el-select>
      </el-form-item>
      <el-form-item label="地区">
        <el-select v-model="sourceConfig.regions" multiple placeholder="不选则全国" class="full-width">
          <el-option v-for="region in regionOptions" :key="region" :label="region" :value="region" />
        </el-select>
      </el-form-item>
      <el-form-item label="预算范围">
        <el-input-number v-model="sourceConfig.minBudget" :min="0" />
        <span class="range-separator">-</span>
        <el-input-number v-model="sourceConfig.maxBudget" :min="0" />
        <span class="unit-text">万元</span>
      </el-form-item>
      <el-form-item label="自动同步">
        <el-switch v-model="sourceConfig.autoSync" />
        <span v-if="sourceConfig.autoSync" class="sync-interval">
          每 <el-input-number v-model="sourceConfig.syncInterval" :min="1" :max="24" size="small" /> 小时
        </span>
      </el-form-item>
      <el-form-item label="数据处理">
        <el-checkbox v-model="sourceConfig.autoSave">自动匹配后入库</el-checkbox>
        <el-checkbox v-model="sourceConfig.enableDedupe">自动去重</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="modelValue = false">取消</el-button>
      <el-button
        :loading="testing"
        :disabled="!canTestConnection"
        type="success"
        @click="$emit('test')"
      >
        测试连接
      </el-button>
      <el-button type="primary" :loading="saving" @click="$emit('save')">保存配置</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { REGION_OPTIONS, SOURCE_KEYWORD_OPTIONS, SOURCE_PLATFORM_OPTIONS } from '../constants.js'

const modelValue = defineModel({ type: Boolean, default: false })
const sourceConfig = defineModel('sourceConfig', { type: Object, required: true })
defineProps({
  saving: { type: Boolean, default: false },
  testing: { type: Boolean, default: false },
  sourcePlatforms: { type: Array, default: () => SOURCE_PLATFORM_OPTIONS },
  keywordOptions: { type: Array, default: () => SOURCE_KEYWORD_OPTIONS },
  regionOptions: { type: Array, default: () => REGION_OPTIONS },
})

defineEmits(['save', 'test'])

const canTestConnection = computed(() => {
  const config = sourceConfig.value
  const hasThirdParty = config.platforms?.includes('第三方商机服务')
  const hasEndpoint = config.apiEndpoint && config.apiEndpoint.trim().length > 0
  const hasKey = config.apiKey && config.apiKey.trim().length > 0
  return hasThirdParty && hasEndpoint && hasKey
})
</script>
