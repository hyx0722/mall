package com.user.mapper;

import com.user.bean.UserAddress;
import org.apache.ibatis.annotations.*;

import java.util.List;



@Mapper
public interface UserAddressMapper {

    @Insert("INSERT INTO user_address (user_id,receiver_name,receiver_phone,province,city,district,detail_address,is_default,create_time)" +
            "    VALUES (" +
            "        #{userId},#{receiverName},#{receiverPhone},#{province},#{city},#{district},#{detailAddress},#{isDefault},now())")
    void addUserAddress(UserAddress userAddress);

    @Update("UPDATE user_address set receiver_name=#{receiverName},receiver_phone=#{receiverPhone},province=#{province}," +
            "city=#{city},district=#{district},detail_address=#{detailAddress},is_default=#{isDefault} " +
            "where id=#{id} and user_id=#{userId}")
    void updateUserAddressById(UserAddress userAddress,@Param("id") Integer id,@Param("userId")Integer userId);
    @Delete("delete from user_address where id=#{id} and user_id=#{userId}")
    void deleteUserAddress(@Param("id") Integer id, @Param("userId") Integer userId);

    @Select("select * from user_address where id=#{id} and user_id=#{userId}")
    UserAddress selectUserDetailAddress(@Param("id") Integer id,@Param("userId")Integer userId);

    @Select("select * from user_address where user_id=#{userId} order by id")
    List<UserAddress> selectUserAddress(@Param("userId") Integer userId);

}
