import request from '@/utils/request'

// 获取考试列表 (学生用)
export const getExamList = () => {
  return request({
    url: '/api/exam/list',
    method: 'get'
  })
}

// 获取考试详情 (开始考试用)
export const getExamDetail = (paperId: number) => {
  return request({
    url: `/api/exam/${paperId}`,
    method: 'get'
  })
}

// 提交试卷
export const submitExam = (data: any) => {
  return request({
    url: '/api/exam/submit',
    method: 'post',
    data
  })
}

// 获取我的成绩 (学生用)
export const getMyScores = (studentId: number) => {
  return request({
    url: `/api/exam/result/list/${studentId}`,
    method: 'get'
  })
}

// 获取所有成绩 (管理员/教师用)
export const getAllScores = () => {
  return request({
    url: '/api/exam/result/all',
    method: 'get'
  })
}

// 【新增】获取答卷详情 (查看错题/阅卷用)
export const getExamResultDetail = (resultId: number) => {
  return request({
    url: `/api/exam/result/detail/${resultId}`,
    method: 'get'
  })
}
