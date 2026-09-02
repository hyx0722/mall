package com.user.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.model.bean.User;
import com.user.bean.UserAddress;
import org.apache.ibatis.annotations.*;

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

    // ---------- 管理员：用户管理 ----------

    // 分页查询所有用户（不含 password，可按用户名/邮箱/手机号模糊过滤）
    @Select("<script>select id,username,email,phone,avatar,status,role,created_time,updated_time from user where 1=1" +
            "<if test='kw != null and kw != \"\"'> and (username like concat('%',#{kw},'%') or email like concat('%',#{kw},'%') or phone like concat('%',#{kw},'%'))</if>" +
            " order by id desc limit #{size} offset #{offset}</script>")
    List<User> pageUsers(@Param("kw") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>select count(*) from user where 1=1" +
            "<if test='kw != null and kw != \"\"'> and (username like concat('%',#{kw},'%') or email like concat('%',#{kw},'%') or phone like concat('%',#{kw},'%'))</if>" +
            "</script>")
    long countUsers(@Param("kw") String keyword);

    // 按 id 查询（校验目标存在）
    @Select("select id,username,status,role from user where id=#{id}")
    User findUserById(@Param("id") Long id);

    // 管理员修改用户：仅更新传入的非空字段（status/role/email/phone）
    @Update("<script>update user set updated_time=now()" +
            "<if test='status != null'>,status=#{status}</if>" +
            "<if test='role != null'>,role=#{role}</if>" +
            "<if test='email != null'>,email=#{email}</if>" +
            "<if test='phone != null'>,phone=#{phone}</if>" +
            " where id=#{id}</script>")
    int adminUpdateFields(@Param("id") Long id, @Param("status") Integer status,
                          @Param("role") Integer role, @Param("email") String email, @Param("phone") String phone);

    // 重置任意用户密码（管理员）
    @Update("update user set password=#{hash},updated_time=now() where id=#{id}")
    int updatePasswordById(@Param("id") Long id, @Param("hash") String hash);

    // 启动种子：管理员是否存在 / 写入管理员
    @Select("select count(*) from user where role=2")
    long countAdmins();

    @Insert("insert into user(username,password,status,role,created_time,updated_time) values(#{username},#{password},1,2,now(),now())")
    void insertAdmin(@Param("username") String username, @Param("password") String password);
}
