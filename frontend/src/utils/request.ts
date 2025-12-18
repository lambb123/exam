import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 axios 实例
const request = axios.create({
  baseURL: 'http://localhost:8081', // 后端地址
  timeout: 5000
})

// 响应拦截器：统一处理后端返回的结果
request.interceptors.response.use(
  response => {
    const res = response.data
    // 如果后端返回 code 不是 200，说明报错了（比如密码错误）
    if (res.code && res.code !== 200) {
      ElMessage.error(res.msg || '系统错误')
      return Promise.reject(new Error(res.msg || 'Error'))
    }
    return res
  },
  error => {
    console.error('请求出错:', error)
    ElMessage.error('网络连接失败，请检查后端是否启动')
    return Promise.reject(error)
  }
)

export default request
