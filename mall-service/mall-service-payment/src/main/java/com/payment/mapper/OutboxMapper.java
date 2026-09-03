package com.payment.mapper;

import com.model.bean.Outbox;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OutboxMapper {

    // 事务性发件箱：与支付落库同事务写入（由 @Transactional 方法调用）
    @Insert("insert into outbox(exchange,routing_key,payload,status,retry_count,delay_ms,created_time) " +
            "values(#{exchange},#{routingKey},#{payload},0,0,#{delayMs},now())")
    int insertOutbox(Outbox outbox);

    // relay 领取待发送行：for update skip locked 多实例安全（须在事务内执行）
    @Select("select id,exchange,routing_key,payload,status,retry_count,delay_ms,created_time,sent_time " +
            "from outbox where status=0 order by id limit #{limit} for update skip locked")
    List<Outbox> selectPending(@Param("limit") int limit);

    @Update("update outbox set status=1, sent_time=now() where id=#{id} and status=0")
    int markSent(@Param("id") Long id);

    @Update("update outbox set retry_count=retry_count+1 where id=#{id}")
    int markRetried(@Param("id") Long id);
}
