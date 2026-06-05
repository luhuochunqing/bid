const tasksApi = require('../../api/tasks.js');
const dayjs = require('dayjs');

Page({
  data: {
    todoList: [],
    stats: {
      total: 0,
      today: 0,
      overdue: 0
    },
    page: 1,
    size: 20,
    loading: false,
    noMore: false
  },

  onLoad() {
    this.loadTodoList();
    this.loadStats();
  },

  onShow() {
    this.setData({ page: 1, todoList: [], noMore: false });
    this.loadTodoList();
    this.loadStats();
  },

  async loadTodoList() {
    if (this.data.loading || this.data.noMore) return;

    this.setData({ loading: true });

    try {
      const result = await tasksApi.getTodoList({
        page: this.data.page,
        size: this.data.size
      });

      const list = (result.list || []).map(item => {
        const dueDate = item.dueDate ? dayjs(item.dueDate) : null;
        const today = dayjs().startOf('day');
        const isOverdue = dueDate && dueDate.isBefore(today) && item.status !== 'DONE';

        return {
          ...item,
          dueDate: dueDate ? dueDate.format('YYYY-MM-DD') : '-',
          createdAt: dayjs(item.createdAt).format('YYYY-MM-DD'),
          priorityText: this.getPriorityText(item.priority),
          overdue: isOverdue
        };
      });

      this.setData({
        todoList: this.data.page === 1 ? list : this.data.todoList.concat(list),
        loading: false,
        noMore: list.length < this.data.size
      });
    } catch (error) {
      console.error('加载待办列表失败', error);
      this.setData({ loading: false });
    }
  },

  async loadStats() {
    try {
      const stats = await tasksApi.getTodoStats();
      this.setData({ stats });
    } catch (error) {
      console.error('加载统计失败', error);
    }
  },

  getPriorityText(priority) {
    const priorityMap = {
      'HIGH': '高优先',
      'MEDIUM': '中优先',
      'LOW': '低优先'
    };
    return priorityMap[priority] || priority;
  },

  onLoadMore() {
    if (!this.data.noMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadTodoList();
    }
  },

  async onToggleComplete(e) {
    const id = e.currentTarget.dataset.id;
    const list = this.data.todoList;
    const item = list.find(i => i.id === id);

    if (!item) return;

    try {
      if (item.status === 'DONE') {
        // 取消完成 - 暂不支持
        wx.showToast({ title: '请在PC端取消完成', icon: 'none' });
      } else {
        await tasksApi.completeTodo(id);
        item.status = 'DONE';
        this.setData({ todoList: list });
        this.loadStats();
        wx.showToast({ title: '已完成', icon: 'success' });
      }
    } catch (error) {
      console.error('完成待办失败', error);
    }
  },

  goToTender(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/tender-detail/tender-detail?id=${id}`
    });
  }
});
