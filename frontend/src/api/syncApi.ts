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
