package com.example.microservice1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Microservice1Application {
    public static void main(String[] args) {
        SpringApplication.run(Microservice1Application.class, args);
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello from Microservice 1!";
    }
}
