# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Service application entrypoints and environment configuration loaders for `consumer` (`ConsumerApplication`, `ConsumerConfig`) and `producer` (`ProducerApplication`, `ProducerConfig`), with support for one-shot and scheduled generation, graceful shutdown hooks, and fallback environment variable resolution (`JDBC_URL` / `MYSQL_URL`, `REDIS_URI` / `REDIS_HOST`) ([#11](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/11)).
- Executable fat JAR packaging via `maven-shade-plugin` across `consumer`, `producer`, and `status-tracker` modules ([#11](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/11)).
- Unit tests for consumer, producer, and status-tracker configuration parsing and application service construction ([#11](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/11)).
- `StorageClient` contract in `common` with `open`, `exists`, and `write` for job input files ([#2](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/2)).
- Synthetic CSV data generator conforming to Section 6.1 schema (`account_number,email,payment_due_date,payment_amount`) with streaming buffered writer, support for demonstration synthetic generation mode and production formatting mode ([#8](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/8)).
- Producer coordination service that creates synthetic CSV files, inserts initial `QUEUED` job records into MySQL via `JobRepository.createJob`, and dispatches version 1 `JobMessage` to Redis Streams queue, with recurring scheduled execution support ([#8](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/8)).
- Initial job row insertion (`createJob`) and state query (`findState`) methods on `JobRepository` and `MysqlJobRepository` ([#8](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/8)).
- Promoted Redis Streams adapter (`RedisJobQueue`, `RedisJobQueueConfig`, `JobMessageCodec`, `InvalidQueueMessageException`) to `common.queue` for shared broker publishing and dequeuing across producer and consumer ([#8](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/8)).
- Comprehensive unit tests covering CSV schema layout, synthetic data constraints, decimal precision, idempotency, scheduled execution, and failure boundaries ([#8](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/8)).
- Status tracker service that lists then watches labelled job pods through the Fabric8 Kubernetes client, moves jobs through `RUNNING`, `SUCCEEDED`, `RETRY_WAIT`, and `DEAD_LETTERED` using configurable retry and exit-code policies, records `pod_event` and `job_attempt` rows in MySQL, reconciles missed events on startup and reconnect, and reads its configuration from environment variables ([#9](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/9)).
- Unit tests for pod mapping, event processing, reconciliation, watch reconnects, and retry policies, plus H2-backed repository tests against the platform-infra schema; Maven dependency management for the Fabric8 Kubernetes client and the SLF4J simple binding ([#9](https://github.com/uconn-cse-sdp-2027-team13/job-orchestrator/issues/9)).
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
