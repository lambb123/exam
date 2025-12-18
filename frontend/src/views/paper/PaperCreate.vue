<template>
  <div class="page-container">
    <div class="header">
      <h2>📝 智能组卷</h2>
      <p class="desc">系统将根据您的要求，从题库中随机抽取试题生成试卷。</p>
    </div>

    <el-card class="box-card">
      <el-form :model="form" label-width="120px" size="large">

        <el-form-item label="试卷名称" required>
          <el-input v-model="form.paperName" placeholder="例如：2024期末考试A卷" />
        </el-form-item>

        <el-form-item label="出卷教师">
          <el-input v-model="teacherName" disabled />
          <span class="tips">（当前登录用户）</span>
        </el-form-item>

        <el-form-item label="题目数量" required>
          <el-input-number v-model="form.questionCount" :min="1" :max="50" />
          <span class="tips"> 系统将随机抽取 {{ form.questionCount }} 道题目（每题10分）</span>
        </el-form-item>

        <el-divider />

        <div class="preview-info">
          <el-statistic title="预计总分" :value="form.questionCount * 10" />
        </div>

        <el-form-item>
          <el-button type="primary" @click="onSubmit" :loading="loading" class="submit-btn">
            开始生成试卷
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>

      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { createPaper } from '@/api/paper'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const teacherName = ref('')
const currentUserId = ref(0)

const form = reactive({
  paperName: '',
  teacherId: 0,
  questionCount: 5 // 默认抽5道题
})

onMounted(() => {
  // 从缓存获取当前登录老师的信息
  const userStr = localStorage.getItem('user')
  if(userStr) {
    const user = JSON.parse(userStr)
    teacherName.value = user.realName || user.username
    currentUserId.value = user.id
    form.teacherId = user.id
  }
})

const onSubmit = async () => {
  if(!form.paperName) return ElMessage.warning('请输入试卷名称')

  loading.value = true
  try {
    const res: any = await createPaper(form)
    if(res.code === 200) {
      ElMessage.success('恭喜！试卷生成成功')
      router.push('/paper/list') // 生成完跳回列表看结果
    }
  } catch(e) {
    // 错误处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.header { margin-bottom: 30px; text-align: center; }
.desc { color: #909399; font-size: 14px; margin-top: 5px; }
.box-card { max-width: 600px; margin: 0 auto; }
.tips { color: #999; font-size: 12px; margin-left: 10px; }
.preview-info { margin-bottom: 30px; text-align: center; background: #f5f7fa; padding: 20px; border-radius: 8px; }
.submit-btn { width: 150px; }
</style>
