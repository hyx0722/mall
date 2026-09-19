package com.user.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.model.bean.User;
import com.user.bean.UserAddress;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;



@Mapper
public interface UserMapper  extends BaseMapper<User> {

    @Insert("INSERT INTO user(username,password,created_time,updated_time,status) values(#{username},#{password},now(),now(),1)")
    void registerInsert(@Param("username")String username,@Param("password")String password);

    @Select("select password from user where username=#{username}")
    String findPasswordByUserName(@Param("username") String username);

    @Select("select id,username,password,status,role from user where username=#{username}")
    User findIdAndPasswordByUserName(@Param("username") String username);



    @Select("select username,email,phone,avatar,status,role from user where username=#{username}")
    User findUserByUsername(@Param("username") String username);

    @Select("select username,avatar,status from user where username=#{username}")
    User findOtherUserByUsername(@Param("username") String username);

    // ---------- 按 id 查用户名（通知列表的店铺补全、店铺页按用户名定位商家） ----------

    @Select("select username from user where id=#{id}")
    String findUsernameById(@Param("id") Long id);

    /**
     * 批量按 id 取用户名，供通知列表一次性补全店铺名——不要在循环里逐个查。
     *
     * 店铺页是按用户名访问的（{@code /store/:username}），而通知行里只存了 store_id，
     * 所以这里必须补出 username，前端才能拼出 /store/:username 的跳转。
     */
    @Select("<script>select id,username from user where id in "
            + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<User> findUsernamesByIds(@Param("ids") Collection<Long> ids);

    @Delete("delete from user where username=#{username}")
    void delete(@Param("username") String username);
    //更新用户基本信息（只更新传入的非空字段）
    @Update("<script>update user set updated_time=now()" +
            "<if test='phone != null'>,phone=#{phone}</if>" +
            "<if test='email != null'>,email=#{email}</if>" +
            " where id=#{id}</script>")
    void update(@Param("id") Long id, @Param("phone") String phone, @Param("email") String email);

    //更新用户头像
    @Update("update user set avatar=#{avatar},updated_time=now() where id=#{id}")
    void updateAvatar(@Param("avatar") String avatar, @Param("id") Long id);
    //更新用户密码
    @Update("update user set password=#{hashcode},updated_time=now() where id=#{id}")
    void updatePwd(@Param("hashcode") String hashcode, @Param("id") Long id);

    @Insert("<script>" +
            "INSERT INTO user_address " +
            "<trim prefix='(' suffix=')' suffixOverrides=','>" +
            "user_id, receiver_name, receiver_phone, " +
            "<if test='province != null and province != \"\"'>province,</if>" +
            "<if test='city != null and city != \"\"'>city,</if>" +
            "<if test='district != null and district != \"\"'>district,</if>" +
            "detail_address, is_default, created_time" +
            "</trim>" +
            " VALUES " +
            "<trim prefix='(' suffix=')' suffixOverrides=','>" +
            "#{userId}, #{receiverName}, #{receiverPhone}, " +
            "<if test='province != null and province != \"\"'>#{province},</if>" +
            "<if test='city != null and city != \"\"'>#{city},</if>" +
            "<if test='district != null and district != \"\"'>#{district},</if>" +
            "#{detailAddress}, #{isDefault}, now()" +
            "</trim>" +
            "</script>")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void addReceiverDetail(UserAddress userAddress);

}
