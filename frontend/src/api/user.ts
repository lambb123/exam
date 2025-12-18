import request from '@/utils/request'

// 登录
export const login = (data: any) => {
  return request({ url: '/api/user/login', method: 'post', data })
}

// 注册
export const register = (data: any) => {
  return request({ url: '/api/user/register', method: 'post', data })
}

// === 新增管理接口 ===

// 获取列表
export const getUserList = () => {
  return request({ url: '/api/user/list', method: 'get' })
}

// 添加用户
export const addUser = (data: any) => {
  return request({ url: '/api/user/add', method: 'post', data })
}

// 更新用户
export const updateUser = (data: any) => {
  return request({ url: '/api/user/update', method: 'post', data })
}

// 删除用户
export const deleteUser = (id: number) => {
  return request({ url: `/api/user/delete/${id}`, method: 'delete' })
}
