package com.learniq.exam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.learniq.exam", "com.learniq.common"})
public class LearnIQExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearnIQExamApplication.class, args);
    }
}
