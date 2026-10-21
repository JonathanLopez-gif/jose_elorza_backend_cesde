package com.joselorza.app;

import org.springframework.boot.SpringApplication;

public class TestJoselorzaApp {

    public static void main(String[] args) {
        SpringApplication.from(JoselorzaApp::main).with(TestcontainersConfiguration.class).run(args);
    }

}
