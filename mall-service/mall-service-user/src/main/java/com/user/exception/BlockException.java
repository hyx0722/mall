package com.user.exception;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.model.bean.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.PrintWriter;

public class BlockException implements BlockExceptionHandler {
    private ObjectMapper objectMapper=new ObjectMapper();
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       String resourceName, com.alibaba.csp.sentinel.slots.block.BlockException e) throws Exception {
        response.setContentType("application/json;chartset=utf-8");
        PrintWriter writer = response.getWriter();
        R error = R.error(500, resourceName + "被Sentinel限制了,原因:" + e.getClass());
        String json=objectMapper.writeValueAsString(error);
        writer.write(json);
        writer.flush();
        writer.close();
    }
}
