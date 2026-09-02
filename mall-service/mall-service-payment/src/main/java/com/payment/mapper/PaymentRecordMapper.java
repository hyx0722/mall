package com.payment.mapper;

import com.payment.entity.PaymentRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface PaymentRecordMapper {

    @Insert("insert into payment_record(pay_order_id,pay_no,transaction_id,notify_type,notify_content,handle_status,created_time) " +
            "values(#{payOrderId},#{payNo},#{transactionId},#{notifyType},#{notifyContent},#{handleStatus},now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRecord(PaymentRecord record);
}
