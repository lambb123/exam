import axios from 'axios'
import { ElMessage } from 'element-plus'

// 定义一个接口，描述你后端的返回结构
interface ApiResponse<T = any> {
  code: number;
  msg: string;
  data: T;
}

// 创建 axios 实例
const request = axios.create({
  baseURL: 'http://localhost:8081', // 后端地址
  timeout: 5000
})

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    // 1. 获取后端返回的实际数据
    const res = response.data as ApiResponse

    // 2. 业务逻辑判断
    // 注意：这里检查 res.code，如果报错 "Property code does not exist..."
    // 是因为 TS 之前不知道 response.data 是什么结构，现在加了 as ApiResponse 就好了
    if (res.code && res.code !== 200) {
      ElMessage.error(res.msg || '系统错误')
      return Promise.reject(new Error(res.msg || 'Error'))
    }

    // 3. 返回解包后的数据 (res)，这样前端组件拿到就是 { code: 200, data: ... }
    return res as any
  },
  (error) => {
    console.error('请求出错:', error)
    ElMessage.error('网络连接失败，请检查后端是否启动')
    return Promise.reject(error)
  }
)

// 导出时强转为 any，或者你在 api 定义时指定返回 Promise<any>
// 这样在组件里 await request.get() 时，TS 就会推断为 any，允许你访问 .code
export default request as any
