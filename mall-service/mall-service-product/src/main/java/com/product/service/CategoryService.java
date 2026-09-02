package com.product.service;

import com.product.bean.Category;

import java.util.List;

public interface CategoryService {

    /** 浏览：某父分类下的启用子分类（parentId=0 即顶级分类） */
    List<Category> listEnabledByParent(Integer parentId);

    /** 浏览：启用分类的完整树（根节点为顶级分类） */
    List<Category> tree();

    /** 管理：新增分类（返回带主键的实体） */
    Category add(Category category);

    /** 管理：编辑分类（含启用/禁用），非法父级/重名返回 null 由 Controller 提示 */
    void update(Category category);
}
