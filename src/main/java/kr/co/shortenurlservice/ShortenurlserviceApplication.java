package kr.co.shortenurlservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ShortenurlserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortenurlserviceApplication.class, args);
    }

}
