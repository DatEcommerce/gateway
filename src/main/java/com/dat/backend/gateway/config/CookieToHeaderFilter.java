package com.dat.backend.gateway.config;

import jakarta.servlet.http.Cookie;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.function.Function;

public class CookieToHeaderFilter {

    public static Function<ServerRequest, ServerRequest> convert() {
        return request -> {
            Cookie[] cookies = request.servletRequest().getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("accessToken".equals(cookie.getName())) {
                        //System.out.println(cookie.getValue());
                        return ServerRequest.from(request)
                                .header("Authorization", "Bearer " + cookie.getValue())
                                .build();
                    }
                }
            }
            return request;
        };
    }
}
