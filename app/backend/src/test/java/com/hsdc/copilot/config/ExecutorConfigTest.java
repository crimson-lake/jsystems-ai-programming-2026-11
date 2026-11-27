package com.hsdc.copilot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ExecutorConfigTest {

    @Test
    void executorRunsTasksOnVirtualThreadsAndShutsDownWithTheContext() throws Exception {
        ExecutorService executor;
        try (var context = new AnnotationConfigApplicationContext(ExecutorConfig.class)) {
            executor = context.getBean(ExecutorService.class);
            boolean virtual = executor.submit(() -> Thread.currentThread().isVirtual()).get(5, TimeUnit.SECONDS);
            assertThat(virtual).isTrue();
            assertThat(executor.isShutdown()).isFalse();
        }
        assertThat(executor.isShutdown()).as("closed with the context").isTrue();
    }

    @Test
    void caseStoreBeanUsesTheConfiguredTtl() {
        var store = new CaseStoreConfig().caseStore(new AppProperties(Duration.ofMinutes(15), ZoneId.of("UTC")));

        assertThat(store).isInstanceOf(com.hsdc.copilot.cases.CaffeineCaseStore.class);
        assertThat(((com.hsdc.copilot.cases.CaffeineCaseStore) store).ttl()).isEqualTo(Duration.ofMinutes(15));
    }
}
