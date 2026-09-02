import request from './request'

// ---------- 买家浏览（mall-service-product，网关前缀 /product） ----------

// 分页浏览在售商品：{ keyword, categoryId, sort, page, size } → { total, items }
export function listProducts(params) {
  return request.get('/product/list', { params })
}

// 顶层分类列表（parentId=0）
export function listCategories() {
  return request.get('/product/category/list', { params: { parentId: 0 } })
}

// 全量分类树
export function categoryTree() {
  return request.get('/product/category/tree')
}

// 老接口：按名称搜索（start 为页码，从 1 起）
export function findProductsByName(start, size, productName) {
  return request.get('/product/findProductByProductName', {
    params: { start, size, productName },
  })
}

// 老接口：按卖家用户名查看其在售商品（店铺页）
export function findProductsByUsername(start, size, username) {
  return request.get('/product/findProductByUserName', {
    params: { start, size, username },
  })
}

// 老接口：查看自己发布的商品（商家后台，含已下架，返回字段较少）
export function findMyProducts(start, size) {
  return request.get('/product/findProductByUserId', { params: { start, size } })
}

// 商品详情（全字段）
export function getProductDetail(id) {
  return request.get('/product/findProductById', { params: { id } })
}

// ---------- 商家管理（商品） ----------

// 发布商品 + 初始化库存（后端复合接口，路径挂在 user 服务）
// body 为 Product：{ name, subtitle, categoryId, mainImage, detail, price, originalPrice }
export function publishProduct(payload) {
  return request.post('/user/userToAddProduct', payload)
}

// 直接新增商品行（一般无需单独调用，publishProduct 已代为完成）
export function addProductNum(payload) {
  return request.post('/product/addNumProduct', payload)
}

// 编辑商品：body 为 UpdateProductRequest，仅需携带要改的非空字段 + id
export function updateProduct(payload) {
  return request.put('/product/updateProduct', payload)
}

// 上/下架：status = 1 上架 / 0 下架
export function setProductShelf(id, status) {
  return request.put('/product/shelfProduct', null, { params: { id, status } })
}

// ---------- 分类管理 ----------

export function addCategory(payload) {
  return request.post('/product/category/add', payload)
}

export function updateCategory(payload) {
  return request.put('/product/category/update', payload)
}
