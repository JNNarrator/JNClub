package com.jnclub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * JNClub 主应用
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.jnclub")
public class JnclubApplication {

    public static void main(String[] args) {
        SpringApplication.run(JnclubApplication.class, args);
    }
}
