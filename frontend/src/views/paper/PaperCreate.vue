<template>
  <div class="page-container">
    <div class="header">
      <h2>📝 智能组卷</h2>
      <p class="desc">请配置各题型的数量，系统将从题库中随机抽取试题。</p>
    </div>

    <el-card class="box-card">
      <el-form :model="form" label-width="120px" size="large">

        <el-form-item label="试卷名称" required>
          <el-input v-model="form.paperName" placeholder="例如：2024期末考试A卷" />
        </el-form-item>

        <el-form-item label="出卷教师">
          <el-input v-model="teacherName" disabled />
        </el-form-item>

        <el-divider content-position="left">题型配置 (每题10分)</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="单选题数量">
              <el-input-number v-model="form.singleCount" :min="0" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="多选题数量">
              <el-input-number v-model="form.multiCount" :min="0" :max="20" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="判断题数量">
              <el-input-number v-model="form.judgeCount" :min="0" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="填空题数量">
              <el-input-number v-model="form.fillCount" :min="0" :max="20" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="简答题数量">
              <el-input-number v-model="form.essayCount" :min="0" :max="10" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider />

        <div class="preview-info">
          <el-row>
            <el-col :span="12">
              <el-statistic title="题目总数" :value="totalCount" />
            </el-col>
            <el-col :span="12">
              <el-statistic title="预计总分" :value="totalCount * 10" />
            </el-col>
          </el-row>
        </div>

        <el-form-item>
          <el-button type="primary" @click="onSubmit" :loading="loading" class="submit-btn">
            生成试卷
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>

      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
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
  singleCount: 0,
  multiCount: 0,
  judgeCount: 0,
  fillCount: 0,
  essayCount: 0
})

const totalCount = computed(() => {
  return (form.singleCount || 0) +
    (form.multiCount || 0) +
    (form.judgeCount || 0) +
    (form.fillCount || 0) +
    (form.essayCount || 0)
})

onMounted(() => {
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
  if(totalCount.value === 0) return ElMessage.warning('请至少配置一种题型')

  loading.value = true
  try {
    const res: any = await createPaper(form)
    if(res.code === 200) {
      ElMessage.success('恭喜！试卷生成成功')
      router.push('/paper/list')
    } else {
      ElMessage.error(res.msg || '生成失败，可能是题库题目不足')
    }
  } catch(e) {
    ElMessage.error('请求出错')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.header { margin-bottom: 20px; text-align: center; }
.desc { color: #909399; font-size: 14px; margin-top: 5px; }
.box-card { max-width: 700px; margin: 0 auto; }
.preview-info { margin-bottom: 30px; text-align: center; background: #f5f7fa; padding: 20px; border-radius: 8px; }
.submit-btn { width: 150px; }
</style>
