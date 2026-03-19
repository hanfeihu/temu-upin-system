package com.tminos.productscene;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ProductSceneApplication {
    public static void main(String[] args) {

        SpringApplication.run(ProductSceneApplication.class, args);
    }
}
