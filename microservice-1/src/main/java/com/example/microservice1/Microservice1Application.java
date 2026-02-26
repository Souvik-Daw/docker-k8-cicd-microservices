package com.example.microservice1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@RestController
public class Microservice1Application {

    private final RestTemplate restTemplate;

    public Microservice1Application(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(Microservice1Application.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello from Microservice 1!";
    }

    @GetMapping("/call-ms2")
    public String callMicroservice2() {
        // When running in Docker, "microservice-2" is the hostname.
        // If testing locally (outside Docker), use "localhost".
        String url = "http://microservice-2:8082/hello";
        try {
            String response = restTemplate.getForObject(url, String.class);
            return "Microservice 1 called Microservice 2 and got: " + response;
        } catch (Exception e) {
            return "Failed to call Docker hostname, trying localhost..." +
                    restTemplate.getForObject("http://localhost:8082/hello", String.class);
        }
    }
}
