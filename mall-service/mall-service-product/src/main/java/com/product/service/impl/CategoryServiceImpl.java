package com.product.service.impl;

import com.model.exception.BusinessException;
import com.product.bean.Category;
import com.product.mapper.CategoryMapper;
import com.product.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    CategoryMapper categoryMapper;

    @Override
    public List<Category> listEnabledByParent(Integer parentId) {
        int p = (parentId == null || parentId < 0) ? 0 : parentId;
        return categoryMapper.listEnabledByParent(p);
    }

    @Override
    public List<Category> tree() {
        List<Category> all = categoryMapper.listEnabledAll();
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            int pid = (c.getParentId() == null) ? 0 : c.getParentId();
            byParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(c);
        }
        byParent.values().forEach(kids -> kids.sort(Comparator.comparing(Category::getSortOrder,
                Comparator.nullsFirst(Integer::compareTo)).thenComparing(Category::getId)));

        List<Category> roots = byParent.getOrDefault(0, List.of());
        Set<Integer> visited = new HashSet<>();
        roots.forEach(root -> attach(root, byParent, visited));
        return roots;
    }

    @Override
    public Category add(Category category) {
        normalize(category);
        validateParent(category.getParentId(), null);
        if (categoryMapper.countSiblingByName(category.getParentId(), category.getName(), 0) > 0) {
            throw new BusinessException("同级已存在同名分类");
        }
        categoryMapper.insertCategory(category);
        return category;
    }

    @Override
    public void update(Category category) {
        if (category.getId() == null) {
            throw new BusinessException("缺少分类 id");
        }
        Category exist = categoryMapper.findById(category.getId());
        if (exist == null) {
            throw new BusinessException("分类不存在");
        }
        // 未传的字段沿用现值（避免把 DB 值清空）
        if (category.getParentId() == null) category.setParentId(exist.getParentId());
        if (category.getSortOrder() == null) category.setSortOrder(exist.getSortOrder());
        if (category.getStatus() == null) category.setStatus(exist.getStatus());
        normalize(category);
        validateParent(category.getParentId(), category.getId());
        if (categoryMapper.countSiblingByName(category.getParentId(), category.getName(), category.getId()) > 0) {
            throw new BusinessException("同级已存在同名分类");
        }
        categoryMapper.updateCategory(category);
    }

    private void attach(Category node, Map<Integer, List<Category>> byParent, Set<Integer> visited) {
        if (!visited.add(node.getId())) {
            node.setChildren(null); // 防御历史脏数据造成环
            return;
        }
        List<Category> kids = byParent.remove(node.getId());
        if (kids != null) {
            kids.forEach(k -> attach(k, byParent, visited));
            node.setChildren(kids);
        } else {
            node.setChildren(null);
        }
    }

    private void normalize(Category c) {
        if (c.getParentId() == null) c.setParentId(0);
        if (c.getSortOrder() == null) c.setSortOrder(0);
        if (c.getStatus() == null) c.setStatus(1);
    }

    /** 父分类必须为 0 或已存在；编辑时禁止把分类挂到自己之下 */
    private void validateParent(Integer parentId, Integer selfId) {
        if (parentId == 0) {
            return;
        }
        if (parentId != null && parentId.equals(selfId)) {
            throw new BusinessException("父分类不能是自己");
        }
        if (categoryMapper.findById(parentId) == null) {
            throw new BusinessException("父分类不存在");
        }
    }
}
