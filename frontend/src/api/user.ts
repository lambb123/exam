import request from '@/utils/request'

// 登录
export const login = (data: any) => {
  return request({
    url: '/api/user/login',
    method: 'post',
    data
  })
}

// 注册
export const register = (data: any) => {
  return request({
    url: '/api/user/register',
    method: 'post',
    data
  })
}
