package com.maraberto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarAbertoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarAbertoApplication.class, args);
	}

}
