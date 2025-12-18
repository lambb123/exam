<template>
  <div class="login-container">
    <div class="login-card">
      <div class="header">
        <h2>考试组卷系统</h2>
        <p>Exam Sync System</p>
      </div>

      <el-form v-if="isLogin" :model="loginForm" :rules="rules" ref="loginRef">
        <el-form-item prop="username">
          <el-input v-model="loginForm.username" placeholder="用户名" :prefix-icon="User" size="large"/>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="密码" :prefix-icon="Lock" size="large" show-password/>
        </el-form-item>
        <el-button type="primary" class="w-100" size="large" @click="handleLogin" :loading="loading">
          登 录
        </el-button>
        <div class="footer-links">
          <span>还没账号？</span>
          <el-link type="primary" @click="isLogin = false">去注册</el-link>
        </div>
      </el-form>

      <el-form v-else :model="regForm" :rules="rules" ref="regRef">
        <el-form-item prop="username">
          <el-input v-model="regForm.username" placeholder="设置用户名" :prefix-icon="User" size="large"/>
        </el-form-item>
        <el-form-item prop="realName">
          <el-input v-model="regForm.realName" placeholder="真实姓名" :prefix-icon="Postcard" size="large"/>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="regForm.password" type="password" placeholder="设置密码" :prefix-icon="Lock" size="large"/>
        </el-form-item>
        <el-form-item prop="role">
          <el-select v-model="regForm.role" placeholder="选择角色" size="large" style="width:100%">
            <el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-button type="success" class="w-100" size="large" @click="handleRegister" :loading="loading">
          注 册
        </el-button>
        <div class="footer-links">
          <span>已有账号？</span>
          <el-link type="primary" @click="isLogin = true">去登录</el-link>
        </div>
      </el-form>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Postcard } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login, register } from '@/api/user'

const router = useRouter()
const isLogin = ref(true) // 控制显示登录还是注册
const loading = ref(false)

// 表单数据
const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', realName: '', role: 'STUDENT' })

// 验证规则
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

// 登录逻辑
const handleLogin = async () => {
  if(!loginForm.username || !loginForm.password) return ElMessage.warning('请填写完整')

  loading.value = true
  try {
    const res: any = await login(loginForm)
    if(res.code === 200) {
      ElMessage.success('登录成功')
      // 保存用户信息
      localStorage.setItem('user', JSON.stringify(res.data))
      router.push('/') // 跳转到首页（还没写，暂时会白屏）
    }
  } finally {
    loading.value = false
  }
}

// 注册逻辑
const handleRegister = async () => {
  if(!regForm.username || !regForm.password || !regForm.realName) return ElMessage.warning('请填写完整')

  loading.value = true
  try {
    const res: any = await register(regForm)
    if(res.code === 200) {
      ElMessage.success('注册成功，请登录')
      isLogin.value = true // 切换回登录页
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #1c92d2 0%, #f2fcfe 100%); /* 漂亮的蓝色渐变背景 */
}
.login-card {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.1);
}
.header { text-align: center; margin-bottom: 30px; }
.header h2 { margin: 0; color: #333; }
.header p { margin: 5px 0; color: #999; font-size: 14px; }
.w-100 { width: 100%; }
.footer-links {
  margin-top: 15px;
  text-align: center;
  font-size: 14px;
  color: #666;
  display: flex;
  justify-content: center;
  gap: 5px;
}
</style>
