package com.user.mapper;

import com.user.bean.StoreMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商店公告。发布即写一行，随后由 {@code NotificationService.fanOut} 群发通知，
 * 两件事在同一个本地事务里（同库，要么都成功要么都回滚）。
 */
@Mapper
public interface StoreMessageMapper {

    /** 插入并回填主键——扇出通知时要用它做 ref_id 与幂等键的一部分 */
    @Insert("insert into store_message(store_id,content,created_time) "
            + "values(#{storeId},#{content},now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(StoreMessage message);

    /** 店铺页：某店的最新公告，倒序分页 */
    @Select("select * from store_message where store_id=#{storeId} order by id desc "
            + "limit #{size} offset #{offset}")
    List<StoreMessage> selectByStore(@Param("storeId") Long storeId,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    @Select("select count(*) from store_message where store_id=#{storeId}")
    long countByStore(@Param("storeId") Long storeId);

    /**
     * 删除公告。
     * ⚠️ {@code and store_id=#{storeId}} 是防越权的唯一凭据——商家只能删自己发的公告。
     *    漏掉它 SQL 照样跑，任何商家都能删别家的公告。
     *
     * 只删本表行，**不删**已投递的 notification：那是投递日志，
     * 级联删等于让商家能抹掉别人收件箱里的历史。
     */
    @Delete("delete from store_message where id=#{id} and store_id=#{storeId}")
    int deleteOwned(@Param("id") Long id, @Param("storeId") Long storeId);
}
