// Input: tendersApi
// Output: useBiddingStore - Pinia store for tenders, todos, and calendar state
// Pos: src/stores/ - State management layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { tendersApi } from '@/api'
import {
  normalizeTenderCollection,
  normalizeTenderStatusCode,
  TENDER_STATUSES,
} from '@/views/Bidding/bidding-utils-status.js'

export const useBiddingStore = defineStore('bidding', {
  state: () => ({
    tenders: [],
    loading: false,
    todos: [],
    calendar: []
  }),

  getters: {
    newTenders: (state) => state.tenders.filter(t => t.status === TENDER_STATUSES.PENDING_ASSIGNMENT),
    followingTenders: (state) => state.tenders.filter(t => t.status === TENDER_STATUSES.TRACKING),
    biddingTenders: (state) => state.tenders.filter(t => t.status === TENDER_STATUSES.BIDDING),
    highPriorityTenders: (state) => state.tenders.filter(t => t.aiScore >= 85),
    urgentTodos: (state) => state.todos.filter(t => t.priority === 'high'),
    todayEvents: (state) => {
      const today = new Date().toISOString().split('T')[0]
      return state.calendar.filter(c => c.date === today)
    }
  },

  actions: {
    async getTenders(filters = {}, retryCount = 0) {
      this.loading = true
      try {
        const result = await tendersApi.getList(filters)
        this.tenders = result?.success ? normalizeTenderCollection(result.data || []) : []
        return this.tenders
      } catch (error) {
        if (error?.response?.status === 429 && retryCount < 3) {
          const delay = Math.pow(2, retryCount) * 1000
          await new Promise(r => setTimeout(r, delay))
          return this.getTenders(filters, retryCount + 1)
        }
        const isRateLimited = error?.response?.status === 429
        if (isRateLimited) {
          console.warn('API 限流(429)，稍后重试:', error.message)
          ElMessage.warning('操作过于频繁，请稍后重试')
        } else {
          console.warn('API 调用失败，返回空列表:', error.message)
          ElMessage.error('加载标讯列表失败，请检查网络后重试')
        }
        this.tenders = []
      } finally {
        this.loading = false
      }
      return this.tenders
    },

    async updateTenderStatus(id, status) {
      const tender = this.tenders.find(t => String(t.id) === String(id))
      if (tender) {
        tender.status = normalizeTenderStatusCode(status)
      }
    },

    async getTodos() {
      return this.todos
    },

    async updateTodoStatus(id, status) {
      const todo = this.todos.find(t => t.id === id)
      if (todo) {
        todo.status = status
      }
    },

    async getCalendar() {
      return this.calendar
    },

    setCalendar(events = []) {
      this.calendar = Array.isArray(events) ? events : []
    }
  }
})
