package com.dat.backend.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallBack {
    @GetMapping("/fallback/products")
    public String productFallBackMethod() {
        return "Fallback product service from gateway";
    }

    @GetMapping("/user" )
    public String user() {
        return "user";
    }
}
