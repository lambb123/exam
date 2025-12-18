import request from '@/utils/request'

export const getDashboardStats = () => {
  return request({
    url: '/api/dashboard/stats',
    method: 'get'
  })
}
