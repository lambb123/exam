<template>
  <div class="page-container">
    <div class="header">
      <h2>📘 在线数据字典 (Schema Viewer)</h2>
    </div>

    <el-alert
      title="关于数据字典"
      type="info"
      :closable="false"
      show-icon
      class="schema-alert"
    >
      <template #default>
        本模块实时读取数据库元数据 (Metadata)，展示系统的表结构设计。
        <br>字段说明优先级：<b>数据库 Comment</b> > <b>智能推断</b> > <b>暂无描述</b>
      </template>
    </el-alert>

    <el-container class="schema-box">
      <el-aside width="260px" class="table-list">
        <div class="list-header">
          <span>数据表 ({{ tables.length }})</span>
        </div>
        <el-scrollbar>
          <ul class="nav-list">
            <li
              v-for="t in tables"
              :key="t.TABLE_NAME"
              :class="{ active: currentTable === t.TABLE_NAME }"
              @click="handleSelectTable(t)"
            >
              <div class="nav-item-row">
                <el-icon><Grid /></el-icon>
                <span class="table-name">{{ t.TABLE_NAME }}</span>
              </div>
              <div class="table-desc" v-if="t.TABLE_COMMENT">{{ t.TABLE_COMMENT }}</div>
            </li>
          </ul>
        </el-scrollbar>
      </el-aside>

      <el-main class="table-detail">
        <div v-if="currentTable">
          <div class="detail-header">
            <div>
              <h3 style="margin:0">{{ currentTable }}</h3>
              <span style="font-size: 13px; color: #999;">
                {{ currentTableComment || '暂无表描述' }}
              </span>
            </div>
            <el-tag effect="dark">MySQL Engine</el-tag>
          </div>

          <el-table :data="columns" border stripe style="width: 100%">
            <el-table-column prop="field" label="字段名" width="180">
              <template #default="{ row }">
                <span style="font-weight: bold; color: #303133">{{ row.field }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="type" label="类型" width="140">
              <template #default="{ row }">
                <span style="color: #409EFF; font-family: monospace;">{{ row.type }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="key" label="键" width="80" align="center">
              <template #default="{ row }">
                <el-tooltip content="主键 (Primary Key)" v-if="row.key === 'PRI'">
                  <el-tag type="danger" size="small" effect="plain">PK</el-tag>
                </el-tooltip>
                <el-tooltip content="外键/索引 (Index)" v-else-if="row.key === 'MUL'">
                  <el-tag type="warning" size="small" effect="plain">IDX</el-tag>
                </el-tooltip>
              </template>
            </el-table-column>

            <el-table-column prop="nullable" label="必填" width="70" align="center">
              <template #default="{ row }">
                <el-icon v-if="row.nullable === 'NO'" color="#F56C6C"><StarFilled /></el-icon>
              </template>
            </el-table-column>

            <el-table-column prop="defaultVal" label="默认值" width="120">
              <template #default="{ row }">
                <span v-if="row.defaultVal" style="font-family: monospace; color: #666">{{ row.defaultVal }}</span>
                <span v-else style="color: #eee">NULL</span>
              </template>
            </el-table-column>

            <el-table-column label="业务含义 / 备注" min-width="200">
              <template #default="{ row }">
                <div v-if="row.comment" style="color: #333;">
                  {{ row.comment }}
                </div>

                <div v-else-if="getSmartDescription(row.field)" style="color: #909399; font-style: italic;">
                  <el-icon style="vertical-align: middle; margin-right: 2px"><InfoFilled /></el-icon>
                  {{ getSmartDescription(row.field) }} (自动识别)
                </div>

                <div v-else style="color: #dcdfe6;">-</div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-else class="empty-state">
          <el-empty description="请在左侧选择数据表以查看结构定义" />
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDbTables, getTableColumns } from '@/api/schema'
import { Grid, StarFilled, InfoFilled } from '@element-plus/icons-vue'

const tables = ref<any[]>([])
const columns = ref<any[]>([])
const currentTable = ref('')
const currentTableComment = ref('')

// 3. 智能推断字典：如果数据库没写注释，前端帮它补上
const commonFields: Record<string, string> = {
  'id': '系统主键 (自增 ID)',
  'create_time': '创建时间',
  'update_time': '更新时间',
  'create_by': '创建人 ID',
  'update_by': '更新人 ID',
  'is_deleted': '逻辑删除标记 (0:否 1:是)',
  'status': '状态 (0:禁用 1:启用)',
  'remark': '备注信息',
  'username': '登录账号',
  'password': '加密密码',
  'real_name': '真实姓名',
  'role': '用户角色',
  'paper_id': '关联试卷 ID',
  'student_id': '关联学生 ID',
  'question_id': '关联题目 ID',
  'score': '分数数值',
  'content': '正文内容/JSON数据'
}

const getSmartDescription = (fieldName: string) => {
  // 1. 精确匹配
  if (commonFields[fieldName]) return commonFields[fieldName]
  // 2. 模糊匹配
  if (fieldName.endsWith('_id')) return '外键关联 ID'
  if (fieldName.endsWith('_time')) return '时间日期字段'
  return null
}

const loadTables = async () => {
  try {
    const res: any = await getDbTables()
    if (res.code === 200) {
      tables.value = res.data
    }
  } catch (e) { console.error(e) }
}

const handleSelectTable = async (t: any) => {
  currentTable.value = t.TABLE_NAME
  currentTableComment.value = t.TABLE_COMMENT
  columns.value = [] // 切换时先清空，防止闪烁
  try {
    const res: any = await getTableColumns(t.TABLE_NAME)
    if (res.code === 200) {
      columns.value = res.data
    }
  } catch (e) { console.error(e) }
}

onMounted(() => {
  loadTables()
})
</script>

<style scoped>
.page-container { padding: 20px; height: calc(100vh - 80px); display: flex; flex-direction: column; }
.header { margin-bottom: 10px; }
.schema-alert { margin-bottom: 15px; }

.schema-box {
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  flex: 1;
  overflow: hidden;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}

.table-list {
  border-right: 1px solid #dcdfe6;
  background: #f8f9fa;
  display: flex;
  flex-direction: column;
}

.list-header {
  padding: 15px;
  font-weight: bold;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
  color: #606266;
  display: flex;
  justify-content: space-between;
}

.nav-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.nav-list li {
  padding: 12px 15px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.2s;
}
.nav-list li:hover { background: #ecf5ff; }
.nav-list li.active { background: #e6f7ff; border-right: 3px solid #409EFF; }

.nav-item-row { display: flex; align-items: center; color: #303133; font-weight: 500; font-size: 14px;}
.nav-item-row .el-icon { margin-right: 6px; color: #909399; }
.nav-list li.active .nav-item-row .el-icon { color: #409EFF; }

.table-desc {
  margin-top: 4px;
  margin-left: 22px;
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.table-detail { padding: 0 20px 20px 20px; background: #fff; overflow-y: auto; }
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 10;
  padding: 20px 0;
  border-bottom: 1px solid #eee;
}
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}
</style>
