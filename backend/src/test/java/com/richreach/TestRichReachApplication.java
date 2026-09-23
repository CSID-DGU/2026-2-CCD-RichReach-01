package com.richreach;

import org.springframework.boot.SpringApplication;

public class TestRichReachApplication {

    public static void main(String[] args) {
        SpringApplication.from(RichReachApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
