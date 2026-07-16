<!-- Input: panels (signup/opening/deposit) / loading / activePeriod
Output: 截止时间卡片（Tab 切换 + 3 列列表 + 倒计时），点行进项目详情
Pos: src/views/Dashboard/components/ - 工作台改造组件
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <section class="deadline-panels-card">
    <div class="deadline-panels-header">
      <div class="deadline-panels-title"><span class="icon"></span>截止时间</div>
      <div class="deadline-panels-tabs" role="tablist">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="deadline-panels-tab"
          :class="{ active: activePeriod === tab.key }"
          role="tab"
          tabindex="0"
          @click="emit('update:activePeriod', tab.key)"
          @keydown.enter.prevent="emit('update:activePeriod', tab.key)"
        >
          {{ tab.label }}
        </div>
      </div>
    </div>

    <div class="deadline-panels-grid" v-loading="loading">
      <DeadlinePanelColumn
        v-for="col in columns"
        :key="col.key"
        :title="col.title"
        :panel-class="col.key"
        :items="panels[col.key] || []"
        @row-click="emit('row-click', $event)"
      />
    </div>
  </section>
</template>

<script setup>
import DeadlinePanelColumn from './DeadlinePanelColumn.vue'

defineProps({
  panels: { type: Object, default: () => ({}) },
  activePeriod: { type: String, default: 'week' },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['update:activePeriod', 'row-click'])

const tabs = [
  { key: 'today', label: '今天' },
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
]

const columns = [
  { key: 'signup', title: '报名截止' },
  { key: 'opening', title: '开标时间' },
  { key: 'deposit', title: '保证金截止' },
]
</script>
