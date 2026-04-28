[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-junit-core/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-junit-core/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-junit-core/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-junit-core/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-junit-core&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-junit-core)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-junit-core&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-junit-core)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.junit.core.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.junit.core)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.junit.core/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.junit.core%22)&#32;[![junit](https://sling.apache.org/badges/group-junit.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/groups/junit.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling JUnit Core

This module is part of the [Apache Sling](https://sling.apache.org) project.

Runs JUnit tests in an OSGi framework and provides the JUnit/Hamcrest APIs used by remote test bundles.

## What this bundle provides

- Test discovery and execution inside a running Sling/OSGi runtime.
- Servlet-based test endpoints with HTML, JSON, XML, and text renderers.
- Runtime support for JUnit 4, plus optional JUnit 5 (Jupiter/Platform) integration when JUnit 5 classes are available.
- Exported JUnit and Hamcrest packages for remote test bundles.

## Build and test

Use Maven from the repository root:

```bash
mvn -B -ntp clean package
```

```bash
mvn -B -ntp test
```

```bash
mvn -B -ntp clean verify
```

Notes:

- `verify` is heavier than `test` because it also runs Maven Invoker integration tests from `src/it`.
- Running `src/it/annotations-it` directly requires `-Dannotations.bundle.version=<version>`.

## Repository structure

- `src/main/java` - Bundle APIs and implementation.
- `src/main/resources` - Static resources (for example `junit.css`).
- `src/test/java` - Unit tests (JUnit 4 and JUnit 5).
- `src/it/annotations-it` - Integration tests (Maven Invoker / Pax Exam).
- `bnd.bnd` - OSGi manifest instructions, including exported JUnit/Hamcrest packages.

## Key dependencies and runtime behavior

- Uses `jakarta.json-api` (migration from `javax.json`).
- Uses `org.apache.commons:commons-lang3` 3.18.0.
- JUnit 4.13.2 and Hamcrest 1.3 are exported for remote test compatibility.
- JUnit 5 support is optional at runtime and activated when platform classes are present.
