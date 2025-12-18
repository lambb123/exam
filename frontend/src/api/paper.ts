import request from '@/utils/request'

// 获取试卷列表
export const getPaperList = () => {
  return request({
    url: '/api/paper/list',
    method: 'get'
  })
}

// 新增：智能组卷
export const createPaper = (data: any) => {
  return request({
    url: '/api/paper/create',
    method: 'post',
    data
  })
}
