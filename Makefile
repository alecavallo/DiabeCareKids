# Zero-host developer toolchain (INV-007): all Gradle commands run inside the
# Docker builder; hosts only need Docker + GNU Make.
COMPOSE := docker compose -f docker/docker-compose.yml
SERVICE := kmp-builder
TF_IMAGE := hashicorp/terraform:1.9
TF_DIR := infra/terraform

# Extra command-line goals (e.g. `make gradle wrapper --version`) are forwarded
# to Gradle. `gradle` itself is filtered out so it is not duplicated.
GRADLE_ARGS := $(filter-out gradle,$(MAKECMDGOALS))

# CI release build knobs. Overridable on the make command line. Empty
# SIGNING_ARGS means the release build stays unsigned (safe local default).
VERSION_CODE ?= 1
VERSION_NAME ?= 1.0-local
SIGNING_ARGS ?=

.PHONY: build test lint infra-validate gradle assemble-release

## Build the debug APK inside the container, output on the host filesystem.
build:
	$(COMPOSE) run --rm $(SERVICE) ./gradlew :composeApp:assembleDebug

## Build the release APK inside the container (INV-007). Signed when
## SIGNING_ARGS carries the -P release keystore properties (CI), unsigned
## otherwise. Output: composeApp-release.apk (signed) or -unsigned.apk.
assemble-release:
	$(COMPOSE) run --rm $(SERVICE) ./gradlew :composeApp:assembleRelease \
		-PversionCode=$(VERSION_CODE) -PversionName=$(VERSION_NAME) $(SIGNING_ARGS)

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
