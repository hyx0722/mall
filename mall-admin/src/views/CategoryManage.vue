<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { categoryTree, addCategory, updateCategory } from '../api/admin'

const loading = ref(true)
const rows = ref([])

const dialogVisible = ref(false)
const editing = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  parentId: 0,
  name: '',
  sortOrder: 0,
  status: 1,
})

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}

// 树 → 平铺（带层级缩进），同时作为父级选择项
function flatten(nodes, depth, out = []) {
  for (const n of nodes || []) {
    out.push({ ...n, depth })
    flatten(n.children, depth + 1, out)
  }
  return out
}

const indented = (row) => '　'.repeat(row.depth || 0) + (row.name || '')

async function load() {
  loading.value = true
  try {
    const tree = (await categoryTree()) || []
    rows.value = flatten(tree, 0)
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function openAdd(parentId = 0) {
  editing.value = false
  Object.assign(form, { id: null, parentId, name: '', sortOrder: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId ?? 0,
    name: row.name || '',
    sortOrder: row.sortOrder ?? 0,
    status: Number(row.status) === 1 ? 1 : 0,
  })
  dialogVisible.value = true
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  try {
    if (editing.value) {
      await updateCategory({
        id: form.id,
        parentId: form.parentId,
        name: form.name,
        sortOrder: Number(form.sortOrder || 0),
        status: Number(form.status),
      })
      ElMessage.success('分类已更新')
    } else {
      await addCategory({
        parentId: Number(form.parentId || 0),
        name: form.name,
        sortOrder: Number(form.sortOrder || 0),
        status: 1,
      })
      ElMessage.success('分类已添加')
    }
    dialogVisible.value = false
    load()
  } catch {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <h3 class="title">分类管理</h3>
      <el-button type="primary" @click="openAdd(0)">新增顶级分类</el-button>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="rows" style="width: 100%">
        <el-table-column label="分类名称" min-width="240">
          <template #default="{ row }">
            <span class="indent" :style="{ paddingLeft: (row.depth || 0) * 20 + 'px' }">
              {{ indented(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="parentId" label="父级 ID" width="100" />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="Number(row.status) === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAdd(row.id)">新增子分类</el-button>
            <el-button link type="warning" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无分类，先新增顶级分类" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑分类' : '新增分类'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级分类">
          <el-select v-model="form.parentId" style="width: 100%" :disabled="!editing && form.parentId === 0 && rows.length === 0">
            <el-option label="顶级分类" :value="0" />
            <el-option v-for="r in rows" :key="r.id" :label="indented(r)" :value="r.id" />
          </el-select>
          <div v-if="editing" class="hint">当前父级：{{ form.parentId }}</div>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="分类名称" maxlength="50" clearable />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1000px;
  margin: 0 auto;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 12px;
}
.title {
  margin: 0;
}
.hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
