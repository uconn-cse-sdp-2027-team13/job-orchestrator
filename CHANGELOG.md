# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Kubernetes Job builder and Fabric8 scheduler with pod labels, workload environment, local data mount, resource/deadline/TTL configuration, idempotent attempt submission, and mock API tests ([#7](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/7)).

- Shared queue and dispatch contracts in `common`, including versioned `JobMessage`, `QueuedJob`, `JobQueue`, `JobScheduler`, `JobRepository`, and `JobCapacity` interfaces ([#6](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/6)).
- Lettuce-based Redis Streams adapter with consumer-group creation, one-entry reads, message encoding and decoding, acknowledgements, and stale-pending reclaim support ([#6](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/6)).
- Consumer dispatch flow that checks capacity before reading, accepts schema version 1, submits through the scheduler interface, persists `DISPATCHED`, and acknowledges only after both dispatch and persistence succeed ([#6](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/6)).
- JDBC job repository for idempotent `QUEUED` to `DISPATCHED` updates and counting active `DISPATCHED` and `RUNNING` jobs, plus a HikariCP data source factory ([#6](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/6)).
- Unit tests for consumer dispatch behavior, message codec validation, MySQL repository updates, and capacity checks ([#6](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/6)).
- Maven dependency management for Lettuce, HikariCP, MySQL Connector/J, and H2 test support ([#6](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/6)).


- Multi-module Java 17 project skeleton with 5 submodules (`common`, `producer`, `consumer`, `status-tracker`, `api`) ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Root parent POM pinning Java compiler release version to 17, configuring multi-module reactor hierarchy, and providing centralized dependency management for JUnit 5 and SLF4J ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Internal module dependency wiring linking `common` into `producer`, `consumer`, `status-tracker`, and `api` ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Shared `JobState` domain model in `common` and initial service classes with unit tests across all submodules ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Portable Maven wrapper (`mvnw`) and `.mvn` configuration for reproducible builds ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Initial `.gitignore` covering Java, Maven, IDEs, and OS-generated files ([#1](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/1)).
- Centralized GitHub Actions CI verification workflow calling reusable pipeline from org `.github` ([#3](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/3)).
