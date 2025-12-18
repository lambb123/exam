<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">👥 教务人员管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon style="margin-right:5px"><Plus /></el-icon> 新增人员
          </el-button>
        </div>
      </template>

      <div class="filter-container">
        <el-input v-model="searchKeyword" placeholder="搜索用户名或姓名" style="width: 200px; margin-right: 10px;" clearable />
        <el-select v-model="filterRole" placeholder="角色筛选" style="width: 150px; margin-right: 10px;" clearable>
          <el-option label="全部" value="" />
          <el-option label="学生" value="STUDENT" />
          <el-option label="教师" value="TEACHER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
      </div>

      <el-table :data="filteredData" border stripe v-loading="loading" style="width: 100%; margin-top: 20px;">
        <el-table-column prop="id" label="ID" width="80" align="center" />

        <el-table-column prop="username" label="用户名" min-width="120" />

        <el-table-column prop="realName" label="真实姓名" min-width="120" />

        <el-table-column prop="role" label="角色" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getRoleTag(scope.row.role)">
              {{ getRoleName(scope.row.role) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="注册时间" width="180" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-popconfirm title="确定删除该用户吗？此操作不可恢复" @confirm="handleDelete(scope.row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="500px"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="登录账号" />
        </el-form-item>

        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="例如：张三" />
        </el-form-item>

        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="为空则不修改/默认123456" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { getUserList, addUser, updateUser, deleteUser } from '@/api/user'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

// === 状态定义 ===
const loading = ref(false)
const userList = ref<any[]>([])
const searchKeyword = ref('')
const filterRole = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  username: '',
  realName: '',
  role: 'STUDENT',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

// === 计算属性：前端过滤 ===
const filteredData = computed(() => {
  return userList.value.filter(item => {
    const matchKey = !searchKeyword.value ||
      (item.username && item.username.includes(searchKeyword.value)) ||
      (item.realName && item.realName.includes(searchKeyword.value))
    const matchRole = !filterRole.value || item.role === filterRole.value
    return matchKey && matchRole
  })
})

// === 方法 ===
const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await getUserList()
    if (res.code === 200) {
      userList.value = res.data
    }
  } catch (e) {
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').split('.')[0]
}

const getRoleName = (role: string) => {
  const map: any = { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员' }
  return map[role] || role
}

const getRoleTag = (role: string) => {
  if (role === 'ADMIN') return 'danger'
  if (role === 'TEACHER') return 'warning'
  return 'success'
}

// 打开新增
const handleCreate = () => {
  isEdit.value = false
  form.id = null
  form.username = ''
  form.realName = ''
  form.role = 'STUDENT'
  form.password = ''
  dialogVisible.value = true
}

// 打开编辑
const handleEdit = (row: any) => {
  isEdit.value = true
  form.id = row.id
  form.username = row.username
  form.realName = row.realName
  form.role = row.role
  form.password = '' // 编辑时不回显密码
  dialogVisible.value = true
}

// 删除
const handleDelete = async (id: number) => {
  try {
    const res: any = await deleteUser(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchData()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (e) {
    ElMessage.error('请求出错')
  }
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      try {
        const api = isEdit.value ? updateUser : addUser
        const res: any = await api(form)
        if (res.code === 200) {
          ElMessage.success(isEdit.value ? '修改成功' : '添加成功')
          dialogVisible.value = false
          fetchData()
        } else {
          ElMessage.error(res.msg || '操作失败')
        }
      } catch (e) {
        ElMessage.error('请求出错')
      }
    }
  })
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-weight: bold; font-size: 16px; }
.filter-container { display: flex; align-items: center; margin-bottom: 10px; }
</style>
