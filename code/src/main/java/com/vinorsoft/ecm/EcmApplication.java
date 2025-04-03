package com.vinorsoft.ecm;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import com.vinorsoft.ecm.domain.FileECM;

@SpringBootApplication
@EnableAsync
public class EcmApplication {
	@Bean
	public BlockingQueue<FileECM> fileCompressQueue() {
		return new LinkedBlockingQueue<>();
	}

	public static void main(String[] args) {
		SpringApplication.run(EcmApplication.class, args);
	}

}
