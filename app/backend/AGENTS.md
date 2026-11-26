# Backend (Spring Boot 4.2, Java 25, Maven)

Scoped to `app/backend`. Root `AGENTS.md` rules still apply. Specification: `docs/ADR/002-backend.md`.

## Commands (run from `app/backend`; verified 2026-11-26 in T10)

| Purpose | Command | Verified result |
|---|---|---|
| Toolchain | `./mvnw -v` | Maven 3.10.1, Java 25.0.5.1 |
| Unit tests | `./mvnw -Pno-frontend test` | Surefire: 1 test (`BuildSanityTest`) |
| Unit + integration | `./mvnw -Pno-frontend verify` | Surefire 1 + Failsafe 3 (`ApplicationContextIT` 1, `EnvNotLoadedUnderTestIT` 2); `TagExclusionGuardIT` not run; JaCoCo check passes |
| Real-API tests | `./mvnw -Pno-frontend verify -Dtest.excludedGroups= -Dgroups=eval` (or `=capture`) | Only `TagExclusionGuardIT` runs (1 test) |
| Run | `./mvnw -Pno-frontend spring-boot:run` | Port 8080, `/actuator/health` returns UP |

If `mvnw` is not executable, use `sh mvnw`. Always check the test counts: zero tests looks green.

## Test discovery

- `*Test` = unit, run by Surefire. `*IT` = integration, run by Failsafe (declared in the POM; without it ITs never run).
- Tags `capture` and `eval` are excluded by default via property `test.excludedGroups`.
- Spring tests use `@ActiveProfiles("test")` (`application-test.yml`, dummy values). Tests tagged `capture`/`eval` use profile `real-api`.
- `.env` is imported only when profile `test` is NOT active (`application.yml`, second document). `EnvNotLoadedUnderTestIT` guards this.
- Never read, print or create `.env` files. Do not put real keys in any file.
- JaCoCo requires 100% branch coverage only for `decision/Guardrails*`, `RuleIdValidator*`, `RuleIdStreamFilter*`.

## Boot 4 rules

- Jackson 3: packages are `tools.jackson.*` (annotations stay `com.fasterxml.jackson.annotation`). Do not add Jackson 2 yourself. Jackson 2 arrives transitively through `openai-java`, so never use it for app JSON.
- `@SpringBootTest` alone gives no MockMvc: add `@AutoConfigureMockMvc` and use `MockMvcTester`.
- Use `RestTestClient`, not `TestRestTemplate`/`RestTemplate`.
- Use `spring-boot-starter-webmvc`, never `spring-boot-starter-web`.
- Look up library APIs through Context7 and match the version in `pom.xml`.
