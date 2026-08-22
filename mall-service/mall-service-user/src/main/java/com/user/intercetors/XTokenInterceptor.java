package com.user.intercetors;

import feign.RequestInterceptor;
import feign.RequestTemplate;

import java.util.UUID;

public class XTokenInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        template.header("X-Token", UUID.randomUUID().toString());
    }
}
