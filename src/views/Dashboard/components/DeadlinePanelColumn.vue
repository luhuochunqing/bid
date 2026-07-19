<!-- Input: title / panelClass / items
Output: 截止时间单列（报名截止/开标/保证金），固定列宽 + 名称省略 + YYYY-MM-DD 日期，4 条可见 + 滚动
Pos: src/views/Dashboard/components/ - 工作台改造组件
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="deadline-panel" :class="panelClass">
    <div class="deadline-panel-head">
      <span class="dot"></span>{{ title }}
      <span class="deadline-panel-count">共 {{ items.length }} 项</span>
    </div>
    <div class="deadline-panel-body">
      <div
        v-for="item in items"
        :key="`${item.targetType}-${item.id}`"
        class="deadline-row"
        role="button"
        tabindex="0"
        @click="emit('row-click', item)"
        @keydown.enter.prevent="emit('row-click', item)"
      >
        <span class="name" :title="item.name">{{ item.name }}</span>
        <span class="date">{{ item.date }}</span>
      </div>
      <div class="deadline-panel-empty" v-if="items.length === 0">暂无相关截止时间</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  panelClass: { type: String, default: '' },
  items: { type: Array, default: () => [] },
})

const emit = defineEmits(['row-click'])
</script>
