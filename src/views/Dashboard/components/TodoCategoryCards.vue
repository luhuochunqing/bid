<!-- Input: cards (from buildTodoCategoryCards)
Output: 4 分类待办卡片网格（任务/标讯/项目/资源），点名称进详情
Pos: src/views/Dashboard/components/ - 工作台改造组件
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <section class="todo-category-cards">
    <div
      v-for="card in cards"
      :key="card.key"
      class="todo-category-card"
      :class="`accent-${card.accent}`"
    >
      <div class="todo-category-card-head">
        <div class="todo-category-card-title">{{ card.title }}</div>
        <div class="todo-category-card-num">{{ card.count }}</div>
      </div>
      <div class="todo-category-card-list" v-if="card.items.length > 0">
        <div
          v-for="item in card.items"
          :key="item.id"
          class="todo-category-item"
          role="button"
          tabindex="0"
          @click="emit('item-click', { cardKey: card.key, item })"
          @keydown.enter.prevent="emit('item-click', { cardKey: card.key, item })"
        >
          <span class="left">{{ item.name }}</span>
          <span class="right">{{ item.rightText }}</span>
        </div>
      </div>
      <div class="todo-category-empty" v-else>暂无待办</div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  cards: { type: Array, default: () => [] },
})

const emit = defineEmits(['item-click'])
</script>
