import request from '@/utils/request'

// 获取试卷列表
export const getPaperList = () => {
  return request({
    url: '/api/paper/list',
    method: 'get'
  })
}

// 【新增】获取详情
export const getPaperDetail = (id: number) => {
  return request({
    url: `/api/paper/${id}`,
    method: 'get'
  })
}

// 智能组卷
export const createPaper = (data: any) => {
  return request({
    url: '/api/paper/create',
    method: 'post',
    data
  })
}

// 删除试卷
export function deletePaper(id: number) {
  return request({
    url: `/api/paper/${id}`,
    method: 'delete'
  })
}
