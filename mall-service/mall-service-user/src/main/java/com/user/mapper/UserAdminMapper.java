package com.user.mapper;

import com.model.bean.User;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;
@Mapper
public interface UserAdminMapper {

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

    // ---------- 管理员：看板 ----------

    /** 用户概览：总数 / 正常 / 管理员 / 今日新增 */
    @Select("""
            select count(*)                                                      as totalUsers,
                   coalesce(sum(case when status = 1 then 1 else 0 end), 0)      as activeUsers,
                   coalesce(sum(case when role = 2 then 1 else 0 end), 0)        as adminUsers,
                   coalesce(sum(case when date(created_time) = curdate() then 1 else 0 end), 0) as todayNew
            from user
            """)
    Map<String, Object> dashboard();

    /** 近 N 天注册趋势；空缺日由 service 补 0 */
    @Select("""
            select date(created_time) as day, count(*) as cnt
            from user
            where created_time >= date_sub(curdate(), interval #{days} day)
            group by date(created_time)
            order by day
            """)
    List<Map<String, Object>> registerTrend(@Param("days") int days);
}
