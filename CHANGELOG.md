# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Multi-module Java 17 project skeleton with 5 submodules (`common`, `producer`, `consumer`, `status-tracker`, `api`) ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Root parent POM pinning Java compiler release version to 17, configuring multi-module reactor hierarchy, and providing centralized dependency management for JUnit 5 and SLF4J ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Internal module dependency wiring linking `common` into `producer`, `consumer`, `status-tracker`, and `api` ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Shared `JobState` domain model in `common` and initial service classes with unit tests across all submodules ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Portable Maven wrapper (`mvnw`) and `.mvn` configuration for reproducible builds ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Initial `.gitignore` covering Java, Maven, IDEs, and OS-generated files ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).

