/**
 * 业务图标到 Element Plus 图标的映射
 *
 * 使用方式:
 * 1. 在模板中使用 CommonIcon 组件，传入业务图标名称
 *    <CommonIcon name="dashboard" />
 * 2. 直接使用 Element Plus 图标名称
 *    <CommonIcon name="Odometer" />
 */

export const iconMap = {
  // ========== 首页/仪表盘 ==========
  'dashboard': 'Odometer',
  'workbench': 'Grid',
  'home': 'HomeFilled',
  'overview': 'DataBoard',

  // ========== 标讯 ==========
  'bidding': 'Document',
  'tender': 'Files',
  'tender-document': 'DocumentCopy',
  'bid-opportunity': 'Opportunity',
  'bid-status': 'CircleCheck',

  // ========== AI ==========
  'ai-center': 'MagicStick',
  'ai-analysis': 'TrendCharts',
  'smart-assembly': 'Setting',
  'ai-assistant': 'ChatLineSquare',
  'ai-writing': 'EditPen',
  'ai-review': 'CircleCheck',
  'ai-score': 'Medal',
  'ai-intel': 'Compass',
  'ai-competition': 'Trophy',

  // ========== 项目 ==========
  'project': 'Briefcase',
  'project-list': 'List',
  'project-create': 'Plus',
  'project-edit': 'Edit',
  'project-delete': 'Delete',
  'project-detail': 'View',
  'project-timeline': 'Clock',
  'project-member': 'User',
  'project-task': 'Tickets',

  // ========== 知识库 ==========
  'knowledge': 'Reading',
  'case': 'Notebook',
  'template': 'Tickets',
  'qualification': 'Medal',
  'document': 'Document',
  'folder': 'Folder',
  'folder-open': 'FolderOpened',
  'file-text': 'Document',
  'file-pdf': 'Document',
  'file-word': 'Document',
  'file-excel': 'Grid',

  // ========== 资源 ==========
  'resource': 'Wallet',
  'expense': 'Receipt',
  'account': 'User',
  'cost': 'Coin',
  'budget': 'Wallet',
  'invoice': 'Tickets',
  'payment': 'CreditCard',

  // ========== 分析 ==========
  'analytics': 'DataAnalysis',
  'chart': 'BarChart',
  'chart-line': 'TrendCharts',
  'chart-pie': 'PieChart',
  'chart-bar': 'Histogram',
  'report': 'Document',
  'statistics': 'DataLine',

  // ========== 系统 ==========
  'settings': 'Setting',
  'user': 'User',
  'logout': 'SwitchButton',
  'notification': 'Bell',
  'message': 'ChatDotSquare',
  'search': 'Search',
  'filter': 'Filter',
  'refresh': 'Refresh',
  'download': 'Download',
  'upload': 'Upload',
  'export': 'Download',
  'import': 'Upload',
  'add': 'Plus',
  'edit': 'Edit',
  'delete': 'Delete',
  'save': 'Check',
  'cancel': 'Close',
  'confirm': 'Check',
  'close': 'Close',
  'back': 'ArrowLeft',
  'forward': 'ArrowRight',
  'up': 'ArrowUp',
  'down': 'ArrowDown',
  'expand': 'ArrowDown',
  'collapse': 'ArrowUp',
  'fullscreen': 'FullScreen',
  'exit-fullscreen': 'Aim',
  'lock': 'Lock',
  'unlock': 'Unlock',
  'eye': 'View',
  'eye-off': 'Hide',
  'copy': 'CopyDocument',
  'cut': 'Scissor',
  'paste': 'Paste',

  // ========== 状态 ==========
  'success': 'CircleCheck',
  'error': 'CircleClose',
  'warning': 'Warning',
  'info': 'InfoFilled',
  'loading': 'Loading',
  'pending': 'Clock',
  'processing': 'Loading',
  'complete': 'CircleCheck',
  'failed': 'CircleClose',
  'draft': 'Edit',

  // ========== 导航 ==========
  'menu': 'Menu',
  'more': 'MoreFilled',
  'more-vertical': 'MoreFilled',
  'link': 'Link',
  'external-link': 'TopRight',
  'share': 'Share',

  // ========== 用户相关 ==========
  'avatar': 'Avatar',
  'profile': 'UserFilled',
  'team': 'UserFilled',
  'role': 'Key',
  'permission': 'Lock',

  // ========== 通知相关 ==========
  'bell': 'Bell',
  'bell-ring': 'BellFilled',
  'email': 'Message',
  'sms': 'ChatLineSquare',
  'alert': 'Notification',

  // ========== 时间相关 ==========
  'calendar': 'Calendar',
  'date': 'Calendar',
  'time': 'Clock',
  'timer': 'Timer',
  'history': 'Clock',

  // ========== 文件操作 ==========
  'file': 'Document',
  'file-add': 'DocumentAdd',
  'file-delete': 'Delete',
  'folder-add': 'FolderAdd',
  'folder-delete': 'Delete',
  'attachment': 'Paperclip',

  // ========== 数据操作 ==========
  'sort': 'Sort',
  'sort-asc': 'Sort',
  'sort-desc': 'Sort',
  'screen': 'Grid',
  'grid': 'Grid',
  'list': 'List',
  'card': 'Grid',

  // ========== 编辑器相关 ==========
  'bold': 'Bold',
  'italic': 'Italic',
  'underline': 'Underline',
  'align-left': 'AlignLeft',
  'align-center': 'AlignCenter',
  'align-right': 'AlignRight',
  'code': 'Document',
  'link-editor': 'Link',
  'image': 'Picture',

  // ========== 其他 ==========
  'star': 'Star',
  'star-filled': 'StarFilled',
  'heart': 'Heart',
  'heart-filled': 'CircleCheck',
  'bookmark': 'Collection',
  'bookmark-filled': 'Collection',
  'tag': 'PriceTag',
  'category': 'Guide',
  'location': 'Location',
  'phone': 'PhoneFilled',
  'mobile': 'Iphone',
  'print': 'Printer',
  'zoom-in': 'ZoomIn',
  'zoom-out': 'ZoomOut',
  'question': 'QuestionFilled',
  'help': 'QuestionFilled',
  'feedback': 'ChatLineSquare',
  'service': 'Headset',
  'api': 'Connection',
  'database': 'Coin',
  'server': 'Monitor',
  'cloud': 'Cloudy',
  'wifi': 'Connection',
  'bluetooth': 'Connection',
  'usb': 'Connection',

  // ========== 方向/箭头 ==========
  'left': 'ArrowLeft',
  'right': 'ArrowRight',
  'top': 'ArrowUp',
  'bottom': 'ArrowDown',
  'double-left': 'DArrowLeft',
  'double-right': 'DArrowRight',
  'double-top': 'DArrowUp',
  'double-bottom': 'DArrowDown',

  // ========== 形状 ==========
  'circle': 'CircleFilled',
  'square': 'Sunny',
  'triangle': 'Sunny',
  'check': 'Check',
  'close-solid': 'CircleCloseFilled',
  'check-circle': 'CircleCheck',
  'close-circle': 'CircleClose',
  'plus-circle': 'CirclePlus',
  'minus-circle': 'CircleMinus',

  // ========== 特殊业务图标 ==========
  'qualification-level': 'Trophy',
  'company-info': 'OfficeBuilding',
  'personnel': 'User',
  'performance': 'TrendCharts',
  'credit': 'Medal',
  'contract': 'Document',
  'approval': 'CircleCheck',
  'workflow': 'Operation',
  'audit': 'View',
  'log': 'Document',
  'archive': 'FolderOpened',
  'trash': 'Delete',
  'recycle': 'Delete',
  'scan': 'View',
  'qrcode': 'View',
  'barcode': 'View'
}

/**
 * 获取 Element Plus 图标名称
 * @param {string} businessIconName - 业务图标名称
 * @returns {string} Element Plus 图标名称
 */
export function getIconName(businessIconName) {
  return iconMap[businessIconName] || businessIconName
}

/**
 * 检查图标是否存在
 * @param {string} iconName - 图标名称
 * @returns {boolean}
 */
export function hasIcon(iconName) {
  return Object.prototype.hasOwnProperty.call(iconMap, iconName)
}

/**
 * 获取所有图标名称
 * @returns {string[]}
 */
export function getAllIconNames() {
  return Object.keys(iconMap)
}

/**
 * 按分类获取图标
 * @param {string} category - 分类名称（可选）
 * @returns {Object|string[]}
 */
export function getIconsByCategory(category) {
  // 可根据需要实现分类逻辑
  return iconMap
}
