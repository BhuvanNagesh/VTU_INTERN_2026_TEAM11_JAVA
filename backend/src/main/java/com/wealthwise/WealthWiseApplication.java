package com.wealthwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class WealthWiseApplication {
    public static void main(String[] args) {
        SpringApplication.run(WealthWiseApplication.class, args);
    }
}
