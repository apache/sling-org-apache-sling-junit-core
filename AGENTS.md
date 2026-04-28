# Project overview
`org.apache.sling.junit.core` is an Apache Sling OSGi bundle that discovers and executes JUnit tests inside a running Sling/OSGi runtime, exposes servlet-based test endpoints/renderers, and ships JUnit/Hamcrest APIs for remote test bundles. The code is a single Maven module with Java sources in `src/main/java`, unit tests in `src/test/java`, and Maven Invoker-driven integration tests in `src/it`.

# Core commands
- Build/compile bundle: `mvn -B -ntp clean package`
- Full test suite (unit + integration/invoker tests): `mvn -B -ntp clean verify`
- Unit tests only: `mvn -B -ntp test`
- Single unit test class: `mvn -B -ntp -Dtest=TestsManagerImplTest test`
- Single unit test method: `mvn -B -ntp -Dtest=TestsManagerImplTest#waitForSystemStartupTimeout test`
- Integration test module directly (when needed): `mvn -B -ntp -f src/it/annotations-it/pom.xml -Dannotations.bundle.version=1.2.1-SNAPSHOT verify`
- Lint/static checks/type safety (via Maven lifecycle and parent plugins): `mvn -B -ntp -DskipTests verify`
- Coverage report (if `jacoco` goals are available from parent/plugin setup): `mvn -B -ntp clean verify jacoco:report`
- Dev server: no standalone dev server is defined in this repository; deploy the built bundle from `target/` to an external Sling runtime.

# Project layout
- `pom.xml` — single-module Maven build, dependencies, invoker integration-test wiring.
- `bnd.bnd` — OSGi bundle manifest instructions (exports/imports, activator, embedded Jacoco agent classes).
- `src/main/java/org/apache/sling/junit/` — public APIs and core contracts.
- `src/main/java/org/apache/sling/junit/impl/` — internal implementation, test execution strategies, service wiring.
- `src/main/java/org/apache/sling/junit/impl/servlet/` — servlet endpoints and output renderers (`html`, `json`, `xml`, text).
- `src/main/java/org/apache/sling/junit/jupiter/osgi/` — JUnit 5 OSGi parameter resolver support.
- `src/main/resources/` — static resources (for example `junit.css`).
- `src/test/java/` — unit tests (JUnit 4 + JUnit 5 vintage/jupiter mix).
- `src/it/annotations-it/` — Maven Invoker/Pax Exam integration test project.
- `Jenkinsfile` — CI entrypoint (`slingOsgiBundleBuild()` shared pipeline).

# Development patterns & constraints
- Language/style: Java with `4`-space indentation, same-line braces, `lowerCamelCase` for methods/fields, `UPPER_SNAKE_CASE` for constants, package names under `org.apache.sling.junit`.
- Module system: OSGi bundle; prefer OSGi DS annotations (for example `@Component`, `@Activate`, `@Deactivate`) and keep manifest behavior aligned with `bnd.bnd`.
- Imports: avoid wildcard imports; keep static imports only where they improve test readability.
- API boundaries: treat `impl` packages as internal; keep reusable contracts in non-`impl` packages.
- CSS/UI: minimal static CSS (`src/main/resources/junit.css`); no frontend build pipeline/framework in this repo.
- Licensing: preserve ASF license headers on source files and follow Apache contribution requirements in [`CONTRIBUTING.md`](https://sling.apache.org/contributing.html).

# Git workflow
- Base branch: `master` (align with current upstream default branch).
- Branch naming: use issue-focused names (examples seen: `feature/...`, `issue/...`, `dependabot/...`).
- Commit messages: prefer Sling issue prefix format such as `SLING-12345 - <imperative summary>`.
- Pull requests: open PRs against `master`, keep CI green, and include issue context/testing notes; follow Apache Sling contribution policy in [`CONTRIBUTING.md`](https://sling.apache.org/contributing.html).

# Testing guidelines
- Frameworks: JUnit 4, JUnit 5 (`jupiter` + `vintage`), Mockito, Sling OSGi mocks; integration tests use Pax Exam in `src/it/annotations-it`.
- Test placement: unit tests in `src/test/java` mirroring production package structure; integration tests and their build config stay under `src/it/annotations-it`.
- Naming: test classes end with `Test` and should keep descriptive method names.
- Coverage: run `mvn -B -ntp clean verify jacoco:report`; CI also publishes coverage badges/results via Sonar/Jenkins.

# Gotchas
- `mvn verify` is heavier than typical modules here because `maven-invoker-plugin` runs `src/it` scenarios; use `mvn test` for quick local loops.
- `src/it/annotations-it/pom.xml` expects `-Dannotations.bundle.version=...` when run standalone (`MUST_BE_SET_BY_INVOKER` by default).
- Runtime behavior can switch between JUnit 4 and JUnit 5 execution based on optional class availability; avoid hard-coding one strategy in changes.
- `TestsManagerImpl` waits for bundle startup (`sling.junit.core.SystemStartupTimeoutSeconds`, default `40` seconds), which can affect timing-sensitive tests.
- The bundle exports JUnit/Hamcrest packages via `bnd.bnd`; changing dependency versions may affect OSGi exports/baseline checks.
