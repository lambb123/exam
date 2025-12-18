import request from '@/utils/request'

// 获取试卷详情（做题用）
export const getExamDetail = (paperId: number) => {
  return request({ url: `/api/exam/${paperId}`, method: 'get' })
}

// 提交试卷
export const submitExam = (data: any) => {
  return request({ url: '/api/exam/submit', method: 'post', data })
}

// 获取我的成绩
export const getMyScore = (studentId: number) => {
  return request({ url: `/api/exam/result/list/${studentId}`, method: 'get' })
}

// 获取所有成绩（教师端）
export const getAllScores = () => {
  return request({ url: '/api/exam/result/all', method: 'get' })
}
