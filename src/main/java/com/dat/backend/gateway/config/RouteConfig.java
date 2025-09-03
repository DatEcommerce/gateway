package com.dat.backend.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.fallbackHeaders;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
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
                .filter(circuitBreaker(config -> {
                    config.setId("slow")
                            .setFallbackUri(URI.create("forward:/fallback/products"))
                            .setStatusCodes("500", "404", "503");
                }))
                //.filter(lb("product-service"))
                .build()
                .and(route("forward_route")
                        .route(path("/fallback/products"), http())
                        .before(uri("http://localhost:8082/fallback/products")) // route to fallback service
                        .before(fallbackHeaders()) // add fallback headers
                        .build());
    }
}
