# Board ID Resolution Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit board IDs to board and game-state documents, and resolve boards by internal ID from `metadata/boards`.

**Architecture:** Extend the YAML-backed model with required board identity fields, add a small board catalog that scans and validates `metadata/boards`, and route both service and regression execution through that catalog. Keep adjudication logic unchanged and cover the change with focused loader/model tests plus existing regression tests.

**Tech Stack:** Java 21, Spring Boot, Jackson YAML, JUnit 5, Maven

---

## Chunk 1: Model And Fixture Schema

### Task 1: Add board identity to the domain records

**Files:**
- Modify: `src/main/java/ai/madpolicy/diplomacy/model/BoardDocument.java`
- Modify: `src/main/java/ai/madpolicy/diplomacy/model/GameStateDocument.java`
- Test: `src/test/java/ai/madpolicy/diplomacy/model/YamlRoundTripTest.java`

- [ ] **Step 1: Write the failing tests**

Add assertions that parsed board YAML exposes `id`, and parsed game-state YAML exposes `board`.

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=YamlRoundTripTest`
Expected: failure because the records do not yet expose the new fields.

- [ ] **Step 3: Write the minimal implementation**

Add `String id` to `BoardDocument` and `String board` to `GameStateDocument`.

- [ ] **Step 4: Run the targeted test to verify it passes**

Run the same command and expect success.

### Task 2: Update board and game-state fixtures to the new schema

**Files:**
- Modify: `metadata/boards/standard.yaml`
- Modify: `metadata/regression/*.yaml`
- Test: `src/test/java/ai/madpolicy/diplomacy/model/YamlRoundTripTest.java`

- [ ] **Step 1: Update board fixtures with `id`**

Add `id` to each board YAML and ensure the basename matches the internal ID.

- [ ] **Step 2: Update game-state fixtures with `board`**

Add `board` to each regression or sample game-state fixture.

- [ ] **Step 3: Re-run parsing coverage**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=YamlRoundTripTest`
Expected: success with updated fixture parsing.

## Chunk 2: Board Catalog And Resolution

### Task 3: Add a board catalog that scans `metadata/boards`

**Files:**
- Create: `src/main/java/ai/madpolicy/diplomacy/app/BoardCatalog.java`
- Create or Modify: `src/test/java/ai/madpolicy/diplomacy/app/BoardCatalogTest.java`

- [ ] **Step 1: Write the failing tests**

Cover:
- successful lookup by board ID
- missing board ID
- duplicate board IDs
- filename/internal ID mismatch

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=BoardCatalogTest`
Expected: failure because the catalog does not exist yet.

- [ ] **Step 3: Write the minimal implementation**

Implement a catalog that:
- scans `metadata/boards/*.yaml`
- loads each file as `BoardDocument`
- validates filename equals `board.id`
- validates uniqueness of `board.id`
- resolves a board by requested ID

- [ ] **Step 4: Run the targeted tests to verify they pass**

Run the same command and expect success.

## Chunk 3: Service And Regression Integration

### Task 4: Route adjudication through board ID resolution

**Files:**
- Modify: `src/main/java/ai/madpolicy/diplomacy/app/AdjudicationService.java`
- Modify: `src/test/java/ai/madpolicy/diplomacy/app/AdjudicationServiceTest.java`

- [ ] **Step 1: Write the failing service tests**

Add coverage that the service resolves a board from `game_state.board` and no longer depends on a caller-supplied board path.

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=AdjudicationServiceTest`
Expected: failure because the service signature and lookup flow are still path-based.

- [ ] **Step 3: Write the minimal implementation**

Update the service to:
- load the game state
- resolve the board from the catalog using `board`
- adjudicate against the resolved board

- [ ] **Step 4: Run the targeted tests to verify they pass**

Run the same command and expect success.

### Task 5: Update the regression harness to use board IDs

**Files:**
- Modify: `src/test/java/ai/madpolicy/diplomacy/regression/RegressionHarness.java`
- Modify: `src/test/java/ai/madpolicy/diplomacy/regression/RegressionCorpusTest.java`

- [ ] **Step 1: Write or update the failing regression-path tests**

Ensure the harness resolves boards from fixture `board` rather than a hard-coded path.

- [ ] **Step 2: Run the targeted regression test to verify current failure**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RegressionCorpusTest`
Expected: failure until the harness uses the catalog and updated fixtures.

- [ ] **Step 3: Write the minimal implementation**

Update the harness to construct the board catalog once and resolve boards per game state.

- [ ] **Step 4: Run the targeted regression test to verify it passes**

Run the same command and expect success.

## Chunk 4: Full Verification

### Task 6: Run focused and regression verification

**Files:**
- No code changes expected

- [ ] **Step 1: Run focused adjudication tests**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=MoveAdjudicatorMicroTest,BoardCatalogTest,AdjudicationServiceTest,YamlRoundTripTest`
Expected: success.

- [ ] **Step 2: Run regression coverage**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RegressionCorpusTest`
Expected: success.

- [ ] **Step 3: Review diff for unintended adjudication changes**

Inspect the modified files and ensure the change is limited to schema, board lookup, and required fixture updates.
