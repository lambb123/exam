import request from '@/utils/request'

// 获取首页统计数据 (用户数、试卷数等)
export const getDashboardStats = () => {
  return request({
    url: '/api/dashboard/stats',
    method: 'get'
  })
}

// 获取数据库连接状态 (MySQL, Oracle, SQL Server)
export const getDbStatus = () => {
  return request({
    url: '/api/dashboard/db-status',
    method: 'get'
  })
}

/**
 * 获取试卷复杂统计数据 (多表连接 + 聚合查询)
 */
export function getPaperStats() {
  return request({
    url: '/api/dashboard/stats/papers', // 对应后端 DashboardController 的接口
    method: 'get'
  })
}
