import request from '@/utils/request'

// 获取同步日志列表
export const getSyncLogs = () => {
  return request({
    url: '/api/logs',
    method: 'get'
  })
}
