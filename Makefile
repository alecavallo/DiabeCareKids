# Zero-host developer toolchain (INV-007): all Gradle commands run inside the
# Docker builder; hosts only need Docker + GNU Make.
COMPOSE := docker compose -f docker/docker-compose.yml
SERVICE := kmp-builder
TF_IMAGE := hashicorp/terraform:1.9
TF_DIR := infra/terraform

# Extra command-line goals (e.g. `make gradle wrapper --version`) are forwarded
# to Gradle. `gradle` itself is filtered out so it is not duplicated.
GRADLE_ARGS := $(filter-out gradle,$(MAKECMDGOALS))

.PHONY: build test lint infra-validate gradle

## Build the debug APK inside the container, output on the host filesystem.
build:
	$(COMPOSE) run --rm $(SERVICE) ./gradlew :composeApp:assembleDebug

## Run the Gradle unit test suites inside the container.
test:
	$(COMPOSE) run --rm $(SERVICE) ./gradlew :composeApp:testDebugUnitTest :shared:testDebugUnitTest

## Run Android lint inside the container.
lint:
	$(COMPOSE) run --rm $(SERVICE) ./gradlew :composeApp:lintDebug

## Validate Terraform (init + fmt check + validate). Plan-only, never applies.
infra-validate:
	docker run --rm -v "$(CURDIR)/$(TF_DIR):/workspace" -w /workspace $(TF_IMAGE) init -input=false -backend=false
	docker run --rm -v "$(CURDIR)/$(TF_DIR):/workspace" -w /workspace $(TF_IMAGE) fmt -check -recursive
	docker run --rm -v "$(CURDIR)/$(TF_DIR):/workspace" -w /workspace $(TF_IMAGE) validate

## Run Gradle inside the container with forwarded arguments.
gradle:
	$(COMPOSE) run --rm $(SERVICE) ./gradlew $(GRADLE_ARGS)

# Catch-all: absorb extra goals that are really Gradle arguments (so
# `make gradle wrapper --version` forwards cleanly).
%:
	@:
