package com.hypeflow.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DotenvConfig implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvConfig.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String directory = findEnvDirectory();
        if (directory == null) {
            log.info("No .env file found, skipping");
            return;
        }

        log.info("Loading .env from {}", directory);

        Dotenv dotenv = Dotenv.configure()
                .directory(directory)
                .load();

        Map<String, Object> envMap = new HashMap<>();
        dotenv.entries().forEach(entry -> envMap.put(entry.getKey(), entry.getValue()));

        environment.getPropertySources()
                .addFirst(new MapPropertySource("dotenvProperties", envMap));

        log.info("Loaded {} environment variables from .env", envMap.size());
    }

    private String findEnvDirectory() {
        if (Files.exists(Path.of(".env"))) {
            return ".";
        }
        if (Files.exists(Path.of("../.env"))) {
            return "..";
        }
        return null;
    }
}
