package com.hypeflow;


import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class HypeflowApp {

    private static final Logger log = LoggerFactory.getLogger(HypeflowApp.class);

    public static void main(String[] args) {
        String userDir = System.getProperty("user.dir");
        log.info("Loading .env file from {}", userDir);

        Dotenv dotenv = Dotenv.configure()
                .directory(userDir)
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        log.info(".env loading complete ({} entries)", dotenv.entries().size());

        SpringApplication.run(HypeflowApp.class, args);
    }
}
