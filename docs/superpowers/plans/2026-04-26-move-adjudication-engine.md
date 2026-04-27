# Move Adjudication Engine Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot-based Java move-phase adjudication engine that reads `metadata/boards/board.yaml` and a move-phase game-state YAML, produces an `adjudicated_state` matching the regression corpus, and produces a `next_state` for the retreat phase.

**Architecture:** Use a small Spring Boot application as the composition root, but keep adjudication logic in plain domain services with no framework coupling. Represent board/state YAML with Jackson-backed immutable DTOs, normalize and validate move orders into a domain model, adjudicate using deterministic pure services, and serialize both `adjudicated_state` and `next_state` back to YAML. Use the regression corpus as the primary acceptance suite.

**Tech Stack:** Java 21, Spring Boot 3.x, Maven, Jackson YAML, JUnit 5, AssertJ

---

## Chunk 1: Project Scaffold and YAML Model

### Task 1: Create the Maven + Spring Boot scaffold

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/ai/madpolicy/diplomacy/DiplomacyApplication.java`
- Create: `src/main/resources/application.yaml`
- Create: `src/test/java/ai/madpolicy/diplomacy/SmokeTest.java`

- [ ] **Step 1: Write the failing scaffold test**

```java
package ai.madpolicy.diplomacy;

import org.junit.jupiter.api.Test;

class SmokeTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run test to verify the project does not exist yet**

Run: `mvn test -Dtest=SmokeTest`
Expected: FAIL because `pom.xml` and source files do not exist yet

- [ ] **Step 3: Create the Spring Boot scaffold**

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>ai.madpolicy</groupId>
  <artifactId>diplomacy</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <properties>
    <java.version>21</java.version>
    <spring-boot.version>3.3.0</spring-boot.version>
  </properties>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.dataformat</groupId>
      <artifactId>jackson-dataformat-yaml</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

```java
package ai.madpolicy.diplomacy;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DiplomacyApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(DiplomacyApplication.class, args);
    }
}
```

- [ ] **Step 4: Run test to verify the scaffold passes**

Run: `mvn test -Dtest=SmokeTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/ai/madpolicy/diplomacy/DiplomacyApplication.java src/main/resources/application.yaml src/test/java/ai/madpolicy/diplomacy/SmokeTest.java
git commit -m "chore: scaffold spring boot adjudicator project"
```

### Task 2: Add YAML DTOs for board and game state

**Files:**
- Create: `src/main/java/ai/madpolicy/diplomacy/model/BoardDocument.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/model/BoardProvince.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/model/BoardPosition.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/model/GameStateDocument.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/model/GamePhase.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/model/NationState.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/model/UnitState.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/model/OrderState.java`
- Create: `src/test/java/ai/madpolicy/diplomacy/model/YamlRoundTripTest.java`

- [ ] **Step 1: Write the failing YAML parse test**

```java
package ai.madpolicy.diplomacy.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class YamlRoundTripTest {
    @Test
    void parsesBoardAndStateFixtures() {
        var board = TestYaml.load(Path.of("metadata/boards/board.yaml"), BoardDocument.class);
        var state = TestYaml.load(Path.of("metadata/states/1901sm.yaml"), GameStateDocument.class);

        assertThat(board.kind()).isEqualTo("board");
        assertThat(state.phase().step()).isEqualTo("move");
    }
}
```

- [ ] **Step 2: Run test to verify DTOs/helpers are missing**

Run: `mvn test -Dtest=YamlRoundTripTest`
Expected: FAIL with missing classes/methods

- [ ] **Step 3: Implement DTOs and a test YAML helper**

```java
public record OrderState(
    String type,
    String status,
    String to,
    String from,
    Boolean viaConvoy,
    String comment
) {}
```

- [ ] **Step 4: Run the fixture parse test**

Run: `mvn test -Dtest=YamlRoundTripTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ai/madpolicy/diplomacy/model src/test/java/ai/madpolicy/diplomacy/model
git commit -m "feat: add yaml document models"
```

### Task 3: Build board indexes and movement helpers

**Files:**
- Create: `src/main/java/ai/madpolicy/diplomacy/domain/BoardIndex.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/domain/PositionType.java`
- Create: `src/test/java/ai/madpolicy/diplomacy/domain/BoardIndexTest.java`

- [ ] **Step 1: Write the failing board index test**

```java
package ai.madpolicy.diplomacy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BoardIndexTest {
    @Test
    void resolvesProvinceAndAdjacencyLookups() {
        BoardIndex board = TestFixtures.boardIndex();

        assertThat(board.provinceIdForPosition("tri_c")).isEqualTo("tri");
        assertThat(board.areAdjacent("tri_c", "adr_s")).isTrue();
        assertThat(board.provinceHasCoast("vie")).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify helpers are missing**

Run: `mvn test -Dtest=BoardIndexTest`
Expected: FAIL

- [ ] **Step 3: Implement board indexes**

```java
public final class BoardIndex {
    public boolean areAdjacent(String left, String right) { ... }
    public String provinceIdForPosition(String positionId) { ... }
    public boolean provinceHasCoast(String provinceId) { ... }
    public boolean isLandPosition(String positionId) { ... }
    public boolean isSeaPosition(String positionId) { ... }
    public Set<String> positionsForProvince(String provinceId) { ... }
}
```

- [ ] **Step 4: Run the board index test**

Run: `mvn test -Dtest=BoardIndexTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ai/madpolicy/diplomacy/domain src/test/java/ai/madpolicy/diplomacy/domain
git commit -m "feat: add board lookup helpers"
```

## Chunk 2: Order Normalization, Validation, and Core Adjudication

### Task 4: Normalize game state into domain orders and units

**Files:**
- Create: `src/main/java/ai/madpolicy/diplomacy/domain/OrderKind.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/domain/NormalizedOrder.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/domain/NormalizedUnit.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/domain/GameStateIndex.java`
- Create: `src/test/java/ai/madpolicy/diplomacy/domain/GameStateIndexTest.java`

- [ ] **Step 1: Write the failing normalization test**

```java
package ai.madpolicy.diplomacy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameStateIndexTest {
    @Test
    void indexesUnitsByPositionAndNation() {
        GameStateIndex state = TestFixtures.stateIndex("metadata/states/1901sm.yaml");

        assertThat(state.unitAt("lon_c")).isPresent();
        assertThat(state.unitAt("lon_c").orElseThrow().order().kind()).isEqualTo(OrderKind.HOLD);
    }
}
```

- [ ] **Step 2: Run test to verify domain state is missing**

Run: `mvn test -Dtest=GameStateIndexTest`
Expected: FAIL

- [ ] **Step 3: Implement state indexing and normalization**

```java
public final class GameStateIndex {
    public Optional<NormalizedUnit> unitAt(String positionId) { ... }
    public List<NormalizedUnit> allUnits() { ... }
}
```

- [ ] **Step 4: Run the normalization test**

Run: `mvn test -Dtest=GameStateIndexTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ai/madpolicy/diplomacy/domain src/test/java/ai/madpolicy/diplomacy/domain
git commit -m "feat: normalize game state into domain structures"
```

### Task 5: Implement order validation and failure comment selection

**Files:**
- Create: `src/main/java/ai/madpolicy/diplomacy/adjudication/FailureComment.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/adjudication/ValidationResult.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/adjudication/OrderValidator.java`
- Create: `src/test/java/ai/madpolicy/diplomacy/adjudication/OrderValidatorTest.java`

- [ ] **Step 1: Write failing validator tests for the spec-critical cases**

```java
package ai.madpolicy.diplomacy.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderValidatorTest {
    @Test
    void rejectsDirectMoveWithoutAdjacency() {
        var result = TestFixtures.validator().validateMove("lon_l", "kie_l", false);
        assertThat(result.isLegal()).isFalse();
        assertThat(result.failureComment()).isEqualTo(FailureComment.ILLEGAL_ORDER);
    }

    @Test
    void allowsSupportToProvinceNotExactPosition() {
        var result = TestFixtures.validator().validateSupport("nth_s", "bel_l", "bel_l");
        assertThat(result.isLegal()).isTrue();
    }
}
```

- [ ] **Step 2: Run validator tests to verify behavior is missing**

Run: `mvn test -Dtest=OrderValidatorTest`
Expected: FAIL

- [ ] **Step 3: Implement validation per spec**

```java
public final class OrderValidator {
    public ValidationResult validate(NormalizedUnit unit, GameStateIndex state, BoardIndex board) { ... }
}
```

- [ ] **Step 4: Run validator tests**

Run: `mvn test -Dtest=OrderValidatorTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ai/madpolicy/diplomacy/adjudication src/test/java/ai/madpolicy/diplomacy/adjudication
git commit -m "feat: implement move-phase order validation"
```

### Task 6: Implement pure adjudication services

**Files:**
- Create: `src/main/java/ai/madpolicy/diplomacy/adjudication/AdjudicationResult.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/adjudication/MoveResolution.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/adjudication/SupportResolution.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/adjudication/ConvoyResolution.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/adjudication/MoveAdjudicator.java`
- Create: `src/test/java/ai/madpolicy/diplomacy/adjudication/MoveAdjudicatorMicroTest.java`

- [ ] **Step 1: Write focused failing micro-tests**

```java
package ai.madpolicy.diplomacy.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MoveAdjudicatorMicroTest {
    @Test
    void failedMoveDefendsOriginWithStrengthOne() { ... }

    @Test
    void supportToHoldRequiresActualHoldOrder() { ... }

    @Test
    void dislodgedUnitHasNoFurtherEffect() { ... }
}
```

- [ ] **Step 2: Run micro-tests to verify adjudication logic is missing**

Run: `mvn test -Dtest=MoveAdjudicatorMicroTest`
Expected: FAIL

- [ ] **Step 3: Implement pure adjudication logic**

```java
public final class MoveAdjudicator {
    public AdjudicationResult adjudicate(BoardIndex board, GameStateIndex state) { ... }
}
```

- [ ] **Step 4: Run micro-tests**

Run: `mvn test -Dtest=MoveAdjudicatorMicroTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ai/madpolicy/diplomacy/adjudication src/test/java/ai/madpolicy/diplomacy/adjudication
git commit -m "feat: implement core move adjudication"
```

## Chunk 3: Serialization, CLI/API Surface, and Regression Harness

### Task 7: Build adjudicated-state and next-state serializers

**Files:**
- Create: `src/main/java/ai/madpolicy/diplomacy/io/YamlMapperFactory.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/io/GameStateWriter.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/adjudication/NextStateBuilder.java`
- Create: `src/test/java/ai/madpolicy/diplomacy/io/NextStateBuilderTest.java`

- [ ] **Step 1: Write the failing next-state test**

```java
package ai.madpolicy.diplomacy.io;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NextStateBuilderTest {
    @Test
    void resetsOrdersAndAdvancesToRetreatStep() {
        var nextState = TestFixtures.nextStateFromRegression("metadata/regression/test6.yaml");

        assertThat(nextState.phase().step()).isEqualTo("retreat");
        assertThat(nextState.nations().get("england").units().getFirst().order().type()).isEqualTo("hold");
        assertThat(nextState.nations().get("england").units().getFirst().order().status()).isEqualTo("new");
    }
}
```

- [ ] **Step 2: Run test to verify output builders are missing**

Run: `mvn test -Dtest=NextStateBuilderTest`
Expected: FAIL

- [ ] **Step 3: Implement output builders**

```java
public final class NextStateBuilder {
    public GameStateDocument build(GameStateDocument original, AdjudicationResult result, BoardIndex board) { ... }
}
```

- [ ] **Step 4: Run next-state test**

Run: `mvn test -Dtest=NextStateBuilderTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ai/madpolicy/diplomacy/io src/main/java/ai/madpolicy/diplomacy/adjudication/NextStateBuilder.java src/test/java/ai/madpolicy/diplomacy/io
git commit -m "feat: build adjudicated and next-state outputs"
```

### Task 8: Add a file-based Spring Boot entrypoint

**Files:**
- Create: `src/main/java/ai/madpolicy/diplomacy/app/AdjudicationService.java`
- Create: `src/main/java/ai/madpolicy/diplomacy/app/AdjudicationRunner.java`
- Create: `src/test/java/ai/madpolicy/diplomacy/app/AdjudicationServiceTest.java`

- [ ] **Step 1: Write the failing service test**

```java
package ai.madpolicy.diplomacy.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdjudicationServiceTest {
    @Test
    void returnsBothArtifacts() {
        var result = TestFixtures.service().adjudicate(
            Path.of("metadata/boards/board.yaml"),
            Path.of("metadata/states/1901sm.yaml")
        );

        assertThat(result.adjudicatedState()).isNotNull();
        assertThat(result.nextState()).isNotNull();
    }
}
```

- [ ] **Step 2: Run service test to verify app layer is missing**

Run: `mvn test -Dtest=AdjudicationServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement the application service and runner**

```java
@Service
public class AdjudicationService {
    public DualStateResult adjudicate(Path boardPath, Path statePath) { ... }
}
```

- [ ] **Step 4: Run service test**

Run: `mvn test -Dtest=AdjudicationServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ai/madpolicy/diplomacy/app src/test/java/ai/madpolicy/diplomacy/app
git commit -m "feat: add file-based adjudication entrypoint"
```

### Task 9: Implement the regression harness as the acceptance suite

**Files:**
- Create: `src/test/java/ai/madpolicy/diplomacy/regression/RegressionHarness.java`
- Create: `src/test/java/ai/madpolicy/diplomacy/regression/RegressionCorpusTest.java`
- Create: `src/test/resources/.gitkeep`

- [ ] **Step 1: Write the failing regression acceptance test**

```java
package ai.madpolicy.diplomacy.regression;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegressionCorpusTest {
    @Test
    void corpusMatchesExpectedAdjudicatedState() {
        var summary = RegressionHarness.runAll();
        assertThat(summary.failedCases()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the regression suite to capture failures**

Run: `mvn test -Dtest=RegressionCorpusTest`
Expected: FAIL with many failing cases until the harness and engine behavior are complete

- [ ] **Step 3: Implement normalization and corpus comparison**

```java
public final class RegressionHarness {
    public static RegressionSummary runAll() { ... }
}
```

- [ ] **Step 4: Run the full regression suite**

Run: `mvn test -Dtest=RegressionCorpusTest`
Expected: PASS across all regression YAML fixtures

- [ ] **Step 5: Run the full test suite**

Run: `mvn test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/test/java/ai/madpolicy/diplomacy/regression src/test/resources/.gitkeep
git commit -m "feat: add regression corpus acceptance suite"
```

## Chunk 4: Documentation and Developer Workflow

### Task 10: Document the local workflow and outputs

**Files:**
- Create: `README.md`
- Modify: `docs/superpowers/specs/2026-04-26-move-adjudication-engine-design.md`

- [ ] **Step 1: Write the failing documentation expectation**

Add a short checklist in the README draft that must explain:

- how to run a single adjudication
- how to run the regression suite
- where `adjudicated_state` and `next_state` are emitted

- [ ] **Step 2: Write the minimal documentation**

```md
# Diplomacy Move Adjudicator

## Run

`mvn test`

## Regression

`mvn test -Dtest=RegressionCorpusTest`
```

- [ ] **Step 3: Verify the documented commands**

Run:
- `mvn test -Dtest=SmokeTest`
- `mvn test -Dtest=RegressionCorpusTest`

Expected: both commands PASS

- [ ] **Step 4: Commit**

```bash
git add README.md docs/superpowers/specs/2026-04-26-move-adjudication-engine-design.md
git commit -m "docs: add adjudicator usage notes"
```

Plan complete and saved to `docs/superpowers/plans/2026-04-26-move-adjudication-engine.md`. Ready to execute?
