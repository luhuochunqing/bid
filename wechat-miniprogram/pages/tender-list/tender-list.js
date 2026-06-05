const tendersApi = require('../../api/tenders.js');
const dayjs = require('dayjs');

Page({
  data: {
    tenderList: [],
    statusList: [
      { label: '全部', value: '' },
      { label: '待分配', value: 'PENDING_ASSIGNMENT' },
      { label: '跟踪中', value: 'TRACKING' },
      { label: '已投标', value: 'BIDDING' },
      { label: '已中标', value: 'WON' },
      { label: '未中标', value: 'LOST' },
      { label: '已放弃', value: 'ABANDONED' }
    ],
    currentStatus: '',
    searchKeyword: '',
    page: 1,
    size: 20,
    loading: false,
    noMore: false
  },

  onLoad() {
    this.loadTenderList();
  },

  onShow() {
    // 页面显示时刷新列表
    this.setData({ page: 1, tenderList: [], noMore: false });
    this.loadTenderList();
  },

  async loadTenderList() {
    if (this.data.loading || this.data.noMore) return;

    this.setData({ loading: true });

    try {
      const params = {
        page: this.data.page,
        size: this.data.size
      };

      if (this.data.currentStatus) {
        params.status = this.data.currentStatus;
      }
      if (this.data.searchKeyword) {
        params.keyword = this.data.searchKeyword;
      }

      const result = await tendersApi.getTenderList(params);

      // 格式化数据
      const list = (result.list || []).map(item => ({
        ...item,
        publishDate: dayjs(item.publishDate).format('YYYY-MM-DD'),
        statusText: this.getStatusText(item.status)
      }));

      this.setData({
        tenderList: this.data.page === 1 ? list : this.data.tenderList.concat(list),
        loading: false,
        noMore: list.length < this.data.size
      });
    } catch (error) {
      console.error('加载标讯列表失败', error);
      this.setData({ loading: false });
      wx.showToast({
        title: '加载失败',
        icon: 'none'
      });
    }
  },

  getStatusText(status) {
    const statusMap = {
      'PENDING_ASSIGNMENT': '待分配',
      'TRACKING': '跟踪中',
      'BIDDING': '已投标',
      'WON': '已中标',
      'LOST': '未中标',
      'ABANDONED': '已放弃'
    };
    return statusMap[status] || status;
  },

  onSearchInput(e) {
    this.setData({ searchKeyword: e.detail.value });
  },

  onSearch() {
    this.setData({ page: 1, tenderList: [], noMore: false });
    this.loadTenderList();
  },

  onStatusChange(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({
      currentStatus: status,
      page: 1,
      tenderList: [],
      noMore: false
    });
    this.loadTenderList();
  },

  onLoadMore() {
    if (!this.data.noMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadTenderList();
    }
  },

  onTenderTap(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/tender-detail/tender-detail?id=${id}`
    });
  },

  async onFavorite(e) {
    const id = e.currentTarget.dataset.id;
    const list = this.data.tenderList;
    const item = list.find(i => i.id === id);

    if (!item) return;

    try {
      if (item.isFavorite) {
        await tendersApi.unfavoriteTender(id);
      } else {
        await tendersApi.favoriteTender(id);
      }

      // 更新本地状态
      item.isFavorite = !item.isFavorite;
      this.setData({ tenderList: list });
    } catch (error) {
      console.error('收藏操作失败', error);
    }
  }
});
