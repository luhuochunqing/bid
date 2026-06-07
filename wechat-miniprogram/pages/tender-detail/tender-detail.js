const tendersApi = require('../../api/tenders.js');
const dayjs = require('dayjs');

Page({
  data: {
    tenderId: null,
    tender: null,
    prediction: null,
    loading: true
  },

  onLoad(options) {
    if (options.id) {
      this.setData({ tenderId: parseInt(options.id) });
      this.loadTenderDetail();
    }
  },

  async loadTenderDetail() {
    this.setData({ loading: true });

    try {
      const tender = await tendersApi.getTenderDetail(this.data.tenderId);

      // 格式化数据
      tender.publishDate = tender.publishDate ? dayjs(tender.publishDate).format('YYYY-MM-DD HH:mm') : '-';
      tender.deadline = tender.deadline ? dayjs(tender.deadline).format('YYYY-MM-DD HH:mm') : '-';
      tender.bidOpeningTime = tender.bidOpeningTime ? dayjs(tender.bidOpeningTime).format('YYYY-MM-DD HH:mm') : '-';
      tender.statusText = this.getStatusText(tender.status);
      tender.riskLevelText = this.getRiskLevelText(tender.riskLevel);

      this.setData({ tender, loading: false });

      // 加载商机预测
      if (tender.purchaserHash) {
        this.loadPrediction(tender.purchaserHash);
      }
    } catch (error) {
      console.error('加载标讯详情失败', error);
      this.setData({ loading: false });
      wx.showToast({
        title: '加载失败',
        icon: 'none'
      });
    }
  },

  async loadPrediction(purchaserHash) {
    try {
      const prediction = await tendersApi.getMarketPrediction(purchaserHash);
      if (prediction) {
        prediction.nextTenderDate = dayjs(prediction.nextTenderDate).format('YYYY-MM-DD');
        prediction.confidence = Math.round(prediction.confidence * 100);
      }
      this.setData({ prediction });
    } catch (error) {
      console.error('加载预测失败', error);
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

  getRiskLevelText(level) {
    const levelMap = {
      'LOW': '低风险',
      'MEDIUM': '中风险',
      'HIGH': '高风险'
    };
    return levelMap[level] || level;
  },

  async onToggleFavorite() {
    const tender = this.data.tender;
    try {
      if (tender.isFavorite) {
        await tendersApi.unfavoriteTender(tender.id);
      } else {
        await tendersApi.favoriteTender(tender.id);
      }
      tender.isFavorite = !tender.isFavorite;
      this.setData({ tender });
    } catch (error) {
      console.error('收藏操作失败', error);
    }
  },

  onBid() {
    wx.showModal({
      title: '确认投标',
      content: '确定要标记此标讯为已投标吗？',
      success: async (res) => {
        if (res.confirm) {
          wx.showToast({
            title: '投标成功',
            icon: 'success'
          });
          wx.navigateBack();
        }
      }
    });
  }
});
