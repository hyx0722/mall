package com.product.mapper;

import com.product.bean.Category;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CategoryMapper {

    //浏览：某父分类下的启用子分类，按 sort_order 升序
    @Select("select * from category where parent_id=#{parentId} and status=1 order by sort_order,id")
    List<Category> listEnabledByParent(@Param("parentId") Long parentId);

    //浏览：全部启用分类（供拼树），按父/排序
    @Select("select * from category where status=1 order by parent_id,sort_order,id")
    List<Category> listEnabledAll();

    //管理：全部分类（含禁用）
    @Select("select * from category order by parent_id,sort_order,id")
    List<Category> listAll();

    @Select("select * from category where id=#{id}")
    Category findById(@Param("id") Long id);

    //同一父分类下是否已存在同名分类（新增时 excludeId 传 0；编辑时排除自身）
    @Select("select count(*) from category where parent_id=#{parentId} and name=#{name} and id<>#{excludeId}")
    long countSiblingByName(@Param("parentId") Long parentId,
                            @Param("name") String name,
                            @Param("excludeId") Long excludeId);

    @Insert("insert into category(parent_id,name,sort_order,status,created_time,updated_time) " +
            "values(#{parentId},#{name},#{sortOrder},#{status},now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCategory(Category category);

    @Update("update category set parent_id=#{parentId},name=#{name},sort_order=#{sortOrder}," +
            "status=#{status},updated_time=now() where id=#{id}")
    int updateCategory(Category category);
}
