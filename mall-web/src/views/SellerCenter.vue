<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  findMyProducts,
  getProductDetail,
  publishProduct,
  updateProduct,
  setProductShelf,
  categoryTree,
} from '../api/product'
import { restock } from '../api/inventory'
import { money } from '../utils/format'

const router = useRouter()

const loading = ref(true)
const list = ref([])
const page = ref(1)
const size = ref(10)
const categories = ref([])

const detailVisible = ref(false)
const detail = ref(null)

// 发布 / 编辑共用的商品表单
const formVisible = ref(false)
const editing = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  categoryId: null,
  name: '',
  subtitle: '',
  mainImage: '',
  price: null,
  originalPrice: null,
  detail: '',
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
}

function flatten(nodes, depth, out = []) {
  for (const n of nodes || []) {
    out.push({ ...n, depth })
    flatten(n.children, depth + 1, out)
  }
  return out
}
const catLabel = (c) => '　'.repeat(c.depth) + (c.name || '')

async function loadCategories() {
  try {
    const tree = (await categoryTree()) || []
    categories.value = flatten(tree, 0)
  } catch {
    categories.value = []
  }
}

async function load() {
  loading.value = true
  try {
    list.value = (await findMyProducts(page.value, size.value)) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editing.value = false
  Object.assign(form, {
    id: null,
    categoryId: categories.value[0]?.id ?? null,
    name: '',
    subtitle: '',
    mainImage: '',
    price: null,
    originalPrice: null,
    detail: '',
  })
  formVisible.value = true
}

async function openEdit(row) {
  try {
    detail.value = (await getProductDetail(row.id)) || row
  } catch {
    detail.value = row
  }
  const p = detail.value
  editing.value = true
  Object.assign(form, {
    id: p.id,
    categoryId: p.categoryId ?? null,
    name: p.name || '',
    subtitle: p.subtitle || '',
    mainImage: p.mainImage || '',
    price: p.price ?? null,
    originalPrice: p.originalPrice ?? null,
    detail: p.detail || '',
  })
  formVisible.value = true
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (Number(form.price) <= 0) {
    ElMessage.warning('价格必须大于 0')
    return
  }
  const payload = {
    categoryId: form.categoryId,
    name: form.name.trim(),
    subtitle: form.subtitle?.trim() || undefined,
    mainImage: form.mainImage?.trim() || undefined,
    detail: form.detail?.trim() || undefined,
    price: Number(form.price),
    originalPrice: form.originalPrice != null && form.originalPrice !== '' ? Number(form.originalPrice) : undefined,
  }
  try {
    if (editing.value) {
      await updateProduct({ ...payload, id: form.id })
      ElMessage.success('商品已更新')
    } else {
      await publishProduct(payload) // 创建商品 + 初始化库存
      ElMessage.success('发布成功')
    }
    formVisible.value = false
    load()
  } catch {
    // 拦截器已提示
  }
}

async function toggleShelf(row) {
  const on = Number(row.status) === 1
  const next = on ? 0 : 1
  try {
    await setProductShelf(row.id, next)
    ElMessage.success(next === 1 ? '已上架' : '已下架')
    load()
  } catch {
    // 拦截器已提示
  }
}

async function openRestock(row) {
  let value
  try {
    const r = await ElMessageBox.prompt(
      `为「${row.name}」补货，请输入数量（增加总库存与可售库存）`,
      '库存补货',
      { inputPattern: /^[1-9]\d*$/, inputErrorMessage: '请输入正整数', confirmButtonText: '补货', cancelButtonText: '取消' },
    )
    value = r.value
  } catch {
    return
  }
  try {
    await restock(row.id, Number(value))
    ElMessage.success('补货成功')
  } catch {
    // 拦截器已提示
  }
}

function openDetail(row) {
  detailVisible.value = true
  detail.value = row
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  load()
}

function nextPage() {
  page.value += 1
  load()
}

function showStatus(row) {
  // Product.status：1 上架 / 0 下架（同通用 ORDER_STATUS 复用仅取文本）
  return Number(row.status) === 1
    ? { text: '在售', type: 'success' }
    : { text: '下架', type: 'info' }
}

onMounted(async () => {
  await loadCategories()
  load()
})
</script>

<template>
  <div class="page">
    <div class="head">
      <h3 class="title">卖家中心 · 我的商品</h3>
      <div class="head-actions">
        <el-button @click="router.push('/seller/orders')">查看商品订单</el-button>
        <el-button type="primary" @click="openAdd">发布商品</el-button>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="list" style="width: 100%">
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="prod-cell">
              <el-image v-if="row.mainImage" :src="row.mainImage" fit="cover" class="thumb">
                <template #error><div class="tf">图</div></template>
              </el-image>
              <div v-else class="thumb tf">图</div>
              <div class="pname" :title="row.name">{{ row.name }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="110">
          <template #default="{ row }">¥{{ money(row.price) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="showStatus(row).type" size="small">{{ showStatus(row).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link @click="toggleShelf(row)">
              {{ Number(row.status) === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button link type="warning" @click="openRestock(row)">补货</el-button>
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="还没有发布过商品，点击右上角「发布商品」开始" />
    </el-card>

    <div v-if="list.length" class="pager">
      <el-button :disabled="page <= 1" @click="prevPage">上一页</el-button>
      <span class="pno">第 {{ page }} 页</span>
      <el-button @click="nextPage">下一页</el-button>
    </div>

    <!-- 发布 / 编辑 -->
    <el-dialog v-model="formVisible" :title="editing ? '编辑商品' : '发布商品'" width="620px" top="6vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" maxlength="200" show-word-limit placeholder="商品名称" clearable />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="catLabel(c)" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" maxlength="200" placeholder="一句卖点（选填）" clearable />
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="1" style="width: 200px" />
          <span class="cur">¥</span>
        </el-form-item>
        <el-form-item label="划线价">
          <el-input-number v-model="form.originalPrice" :min="0.01" :precision="2" :step="1" style="width: 200px" />
          <span class="cur">¥</span>
        </el-form-item>
        <el-form-item label="主图 URL">
          <el-input v-model="form.mainImage" placeholder="https:// 图片地址（选填，必须为合法 URL）" clearable />
        </el-form-item>
        <el-form-item v-if="form.mainImage" label="主图预览">
          <el-image :src="form.mainImage" fit="cover" class="preview" style="width: 120px; height: 120px">
            <template #error><span class="tf">加载失败</span></template>
          </el-image>
        </el-form-item>
        <el-form-item label="详情描述">
          <el-input v-model="form.detail" type="textarea" :rows="4" maxlength="10000" placeholder="商品详情（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">{{ editing ? '保存修改' : '发布' }}</el-button>
      </template>
    </el-dialog>

    <!-- 快速查看 -->
    <el-dialog v-model="detailVisible" title="商品信息" width="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="分类 ID">{{ detail.categoryId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="价格">¥{{ money(detail.price) }}</el-descriptions-item>
          <el-descriptions-item label="划线价">
            {{ detail.originalPrice != null ? '¥' + money(detail.originalPrice) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="showStatus(detail).type" size="small">{{ showStatus(detail).text }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 12px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin: 12px 0 24px;
}
.pno {
  color: #606266;
  font-size: 14px;
}
.title {
  margin: 0;
}
.head-actions {
  display: flex;
  gap: 8px;
}
.prod-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
.thumb {
  width: 56px;
  height: 56px;
  border-radius: 4px;
  background: #f5f7fa;
  flex-shrink: 0;
}
.tf {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 12px;
}
.pname {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.cur {
  margin-left: 8px;
}
.preview {
  border-radius: 4px;
}
</style>
