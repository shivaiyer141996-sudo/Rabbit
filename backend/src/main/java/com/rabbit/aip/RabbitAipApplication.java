package com.rabbit.aip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RabbitAipApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitAipApplication.class, args);
    }
}
