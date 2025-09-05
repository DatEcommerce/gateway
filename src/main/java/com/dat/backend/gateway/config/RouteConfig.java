package com.dat.backend.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.*;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.adaptCachedBody;

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
//                .filter(rateLimit(c->c
//                        .setCapacity(100)
//                        .setPeriod(Duration.ofMinutes(1))
//                        .setKeyResolver(request -> request.servletRequest().getUserPrincipal().getName())))
                .filter(retry(c -> c
                        .setRetries(3)
                        .setSeries(Set.of(HttpStatus.Series.SERVER_ERROR))
//                        .setExceptions(Set.of(AuthenticationException.class))
                        .setCacheBody(true)))
                .filter(adaptCachedBody())
                .filter(lb("product-service"))
                .build()
                .and(route("forward_route")
                        .route(path("/fallback/products"), http())
                        .before(uri("http://localhost:8082/fallback/products")) // route to fallback service
                        .before(fallbackHeaders()) // add headers to fallback service
                        .build());
    }
}
