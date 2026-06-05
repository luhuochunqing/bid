/**
 * 图标统一导出
 * 从 @element-plus/icons-vue 导出常用图标
 */

import {
  // 基础图标
  Plus,
  Search,
  Edit,
  Delete,
  View,
  Download,
  Upload,
  Refresh,

  // 状态图标
  Success,
  Warning,
  InfoFilled,
  CircleCheck,
  CircleClose,

  // AI功能图标
  MagicStick as Magic,
  Opportunity,
  EditPen,
  Checked,
  TrendCharts,
  PieChart,
  Shield,
  Timer,
  Clock,

  // 导航图标
  ArrowLeft,
  ArrowRight,
  DArrowLeft,
  DArrowRight,
  Back,
  Fold,
  Expand,

  // 用户相关
  User,
  UserFilled,
  Avatar,
  Lock,

  // 文件相关
  Document,
  Folder,
  FolderOpened,
  Files,
  Paperclip,

  // 消息相关
  Bell,
  ChatDotRound,
  Message,
  Phone,
  Iphone,

  // 时间相关
  Calendar,
  Clock as TimeClock,
  Timer as Stopwatch,

  // 其他
  More,
  MoreFilled,
  Setting,
  Share,
  Link,
  Star,
  StarFilled,
  Thumb,
  Position,
  Coordinate,
  Grid,
  List,
  Menu
} from '@element-plus/icons-vue'

// 统一导出
export const icons = {
  // 基础图标
  Plus,
  Search,
  Edit,
  Delete,
  View,
  Download,
  Upload,
  Refresh,

  // 状态图标
  Success,
  Warning,
  InfoFilled,
  CircleCheck,
  CircleClose,

  // AI功能图标
  Magic,
  Opportunity,
  EditPen,
  Checked,
  TrendCharts,
  PieChart,
  Shield,
  Timer,
  Clock,

  // 导航图标
  ArrowLeft,
  ArrowRight,
  DArrowLeft,
  DArrowRight,
  Back,
  Fold,
  Expand,

  // 用户相关
  User,
  UserFilled,
  Avatar,
  Lock,

  // 文件相关
  Document,
  Folder,
  FolderOpened,
  Files,
  Paperclip,

  // 消息相关
  Bell,
  ChatDotRound,
  Message,
  Phone,
  Iphone,

  // 时间相关
  Calendar,
  TimeClock,
  Stopwatch,

  // 其他
  More,
  MoreFilled,
  Setting,
  Share,
  Link,
  Star,
  StarFilled,
  Thumb,
  Position,
  Coordinate,
  Grid,
  List,
  Menu
}

// 默认导出所有图标
export default icons

// 按类别导出，方便按需导入
export const basicIcons = {
  Plus, Search, Edit, Delete, View, Download, Upload, Refresh
}

export const statusIcons = {
  Success, Warning, InfoFilled, CircleCheck, CircleClose
}

export const aiIcons = {
  Magic, Opportunity, EditPen, Checked, TrendCharts, PieChart, Shield, Timer, Clock
}

export const navIcons = {
  ArrowLeft, ArrowRight, DArrowLeft, DArrowRight, Back, Fold, Expand
}

export const userIcons = {
  User, UserFilled, Avatar, Lock
}

export const fileIcons = {
  Document, Folder, FolderOpened, Files, Paperclip
}

export const messageIcons = {
  Bell, ChatDotRound, Message, Phone, Iphone
}

export const timeIcons = {
  Calendar, TimeClock, Stopwatch
}
