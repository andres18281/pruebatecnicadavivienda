package com.techdocs.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ProcessingWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessingWorkerApplication.class, args);
    }
}
