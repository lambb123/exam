import request from '@/utils/request'

export const getQuestionList = () => {
  return request({
    url: '/api/question/list',
    method: 'get'
  })
}

export const addQuestion = (data: any) => {
  return request({
    url: '/api/question/add',
    method: 'post',
    data
  })
}

export const deleteQuestion = (id: number) => {
  return request({
    url: `/api/question/delete/${id}`,
    method: 'delete'
  })
}
