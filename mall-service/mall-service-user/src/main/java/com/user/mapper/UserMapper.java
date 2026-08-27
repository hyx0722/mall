package com.user.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.model.bean.User;
import com.user.bean.UserAddress;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper  extends BaseMapper<User> {

    @Insert("INSERT INTO user(username,password,created_time,updated_time,status) values(#{username},#{password},now(),now(),1)")
    void registerInsert(@Param("username")String username,@Param("password")String password);

    @Select("select password from user where username=#{username}")
    String findPasswordByUserName(@Param("username") String username);

    @Select("select id,username,password from user where username=#{username}")
    User findIdAndPasswordByUserName(@Param("username") String username);



    @Select("select username,email,phone,avatar,status from user where username=#{username}")
    User findUserByUsername(@Param("username") String username);

    @Select("select username,avatar,status from user where username=#{username}")
    User findOtherUserByUsername(@Param("username") String username);

    @Delete("delete from user where username=#{username}")
    void delete(@Param("username") String username);
    //更新用户基本信息（只更新传入的非空字段）
    @Update("<script>update user set updated_time=now()" +
            "<if test='phone != null'>,phone=#{phone}</if>" +
            "<if test='email != null'>,email=#{email}</if>" +
            " where id=#{id}</script>")
    void update(@Param("id") Integer id, @Param("phone") String phone, @Param("email") String email);

    //更新用户头像
    @Update("update user set avatar=#{avatar},updated_time=now() where id=#{id}")
    void updateAvatar(@Param("avatar") String avatar, @Param("id") Integer id);
    //更新用户密码
    @Update("update user set password=#{hashcode},updated_time=now() where id=#{id}")
    void updatePwd(@Param("hashcode") String hashcode, @Param("id") Integer id);

    @Insert("<script>" +
            "INSERT INTO user_address " +
            "<trim prefix='(' suffix=')' suffixOverrides=','>" +
            "user_id, receiver_name, receiver_phone, " +
            "<if test='province != null and province != \"\"'>province,</if>" +
            "<if test='city != null and city != \"\"'>city,</if>" +
            "<if test='district != null and district != \"\"'>district,</if>" +
            "detail_address, is_default, create_time" +
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
