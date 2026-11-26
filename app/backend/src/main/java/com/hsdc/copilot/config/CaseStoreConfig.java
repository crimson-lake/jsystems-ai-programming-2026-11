package com.hsdc.copilot.config;

import com.hsdc.copilot.cases.CaffeineCaseStore;
import com.hsdc.copilot.cases.CaseStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The in-memory case store with the idle TTL {@code app.case-ttl} (ADR-002 section 3.4). */
@Configuration(proxyBeanMethods = false)
class CaseStoreConfig {

    @Bean
    CaseStore caseStore(AppProperties app) {
        return new CaffeineCaseStore(app.caseTtl());
    }
}
