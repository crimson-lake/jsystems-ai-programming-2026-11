package com.hsdc.copilot.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Executor for turn pipelines: one virtual thread per task, closed (and awaited) when the context closes. */
@Configuration(proxyBeanMethods = false)
class ExecutorConfig {

    @Bean(destroyMethod = "close")
    ExecutorService turnExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
