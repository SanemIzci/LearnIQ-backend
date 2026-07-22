package com.learniq.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.learniq.auth", "com.learniq.common"})
public class LearnIQAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearnIQAuthApplication.class, args);
    }
}
