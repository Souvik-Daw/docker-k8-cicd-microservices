package com.example.microservice2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Microservice2Application {
    public static void main(String[] args) {
        SpringApplication.run(Microservice2Application.class, args);
        System.out.println("Microservice 2 started");
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello from Microservice 2!";
    }
}
