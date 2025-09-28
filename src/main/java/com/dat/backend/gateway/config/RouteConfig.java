    package com.dat.backend.gateway.config;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.*;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

@Configuration
@RequiredArgsConstructor
public class RouteConfig {
    private final UriConfiguration uriConfiguration;

    @Bean
    public RouterFunction<ServerResponse> routerFunction() {
        String PRODUCT_SERVICE_URI = uriConfiguration.getProduct_uri();

        return route("path_route")
                .route(path("/api/v1/products/**"), http())
                .before(uri(PRODUCT_SERVICE_URI))
//                .filter((request, next) -> {
//                            Cookie[] cookies = request.servletRequest().getCookies();
//                            if (cookies != null) {
//                                for (Cookie cookie : cookies) {
//                                    if ("accessToken".equals(cookie.getName())) {
//                                        request.servletRequest().setAttribute(
//                                                "Authorization",
//                                                "Bearer " + cookie.getValue()
//                                        );
//                                    }
//                                }
//                            }
//                            return next.handle(request);
//                        })
//                .filter(rateLimit(c->c
//                        .setCapacity(100)
//                        .setPeriod(Duration.ofMinutes(1))
//                        .setKeyResolver(request -> request.servletRequest().getUserPrincipal().getName())))
//                .filter(retry(c -> c
//                        .setRetries(3)
//                        .setSeries(Set.of(HttpStatus.Series.SERVER_ERROR))
//                        .setExceptions(Set.of(AuthenticationException.class))
//                        .setCacheBody(true)))
//                .filter(adaptCachedBody())
//                .filter(tokenRelay()) // forward the token to the product service
                //.filter(lb("product-service"))
                .filter(circuitBreaker(config -> config.setId("slow")
                        .setFallbackUri(URI.create("forward:/fallback/products"))
                        .setStatusCodes("500", "404", "503")))
                .build()
                .and(route("forward_fallback_service")
                        .route(path("/fallback/products"), http())
                        .before(uri("http://localhost:8082/fallback/products")) // route to fallback service
                        .before(fallbackHeaders()) // add headers to fallback service
                        .build())
                .and(route("forward_auth_service")
                        .route(path("/api/v1/auth/**"), http())
                        .before(uri("http://localhost:8081")) // route to auth service
                        //.filter(tokenRelay())
//                        .filter((request, next) -> {
//                            Cookie[] cookies = request.servletRequest().getCookies();
//                            if (cookies != null) {
//                                for (Cookie cookie : cookies) {
//                                    if ("accessToken".equals(cookie.getName())) {
//                                        return next.handle(
//                                                ServerRequest.from(request)
//                                                        .header("Authorization", "Bearer " + cookie.getValue())
//                                                        .build()
//                                        );
//                                    }
//                                }
//                            }
//                            return next.handle(CookieToHeaderFilter.convert().apply(request));
//                        })
                        .build())
                ;
    }
}
