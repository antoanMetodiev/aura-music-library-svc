package com.aura.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LibrarySvcApplication {

	public static void main(String[] args) {
		SpringApplication.run(LibrarySvcApplication.class, args);
	}

}
