import request from '@/utils/request'

export const getDashboardStats = () => {
  return request({
    url: '/api/dashboard/stats',
    method: 'get'
  })
}

// === 【新增】获取数据库状态 ===
export const getDbStatus = () => {
  return request({
    url: '/api/dashboard/db-status',
    method: 'get'
  })
}
