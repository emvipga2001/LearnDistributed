package com.omnichannel.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReservationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationEngineApplication.class, args);
    }
}
