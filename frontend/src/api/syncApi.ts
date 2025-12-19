import request from '@/utils/request'

// 获取仪表盘统计 (原有的)
export const getDashboardStats = () => {
  return request({ url: '/api/dashboard/stats', method: 'get' })
}

// 获取数据库连接状态 (原有的)
export const getDbStatus = () => {
  return request({ url: '/api/dashboard/db-status', method: 'get' })
}

// === 【新增】获取各表同步详情 ===
export const getTableSyncStatus = () => {
  return request({
    url: '/api/monitor/table-status',
    method: 'get'
  })
}


// 获取同步监控大屏数据
export const getSyncDashboardStats = () => {
  return request({
    url: '/api/sync/stats/dashboard',
    method: 'get'
  })
}

// 手动触发同步 (可选)
export const triggerManualSync = () => {
  return request({
    url: '/api/sync/trigger', // 假设你有这个接口
    method: 'post'
  })
}
