package com.meow;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MeowApplication {
  public static void main(String[] args) throws Exception {
    Files.createDirectories(Path.of("instance"));
    SpringApplication.run(MeowApplication.class, args);
  }
}
