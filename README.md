# madpolicy.ai

Java 21 + Spring Boot + Maven implementation of a Diplomacy move-phase adjudication engine.

## Scope

This repo currently covers move-phase adjudication only. It reads a board plus move-phase game state and produces:

- an `adjudicated_state`
- a `next_state` with `phase.step: retreat`

The primary behavioral oracle is the regression corpus in `src/test/resources/regression`.

## Requirements

- Java 21
- Maven

Check that your shell is already using Java 21:

```bash
which java
java -version
```

If it is not, set Java 21 explicitly:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Use a workspace-local Maven repo:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 ...
```

## Build

Compile the project:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 compile
```

Build the jar:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 package
```

## Test

Run focused adjudication tests:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=MoveAdjudicatorMicroTest
```

Run the move-phase regression corpus:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RegressionCorpusTest
```

Run the full test suite:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test
```

## Manual Testing

Start the service locally for manual testing:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 spring-boot:run
```

In another shell, call the YAML adjudication endpoint with a regression request fixture:

```bash
curl -H 'Content-Type: application/yaml' \
     -H 'Accept: application/yaml' \
     --data-binary @src/test/resources/regression/test1_requested.yaml \
     http://localhost:8080/adjudicate \
     -o /tmp/test1_actual.yaml
```

Compare the response to the expected fixture with `dyff`:

```bash
dyff between /tmp/test1_actual.yaml src/test/resources/regression/test1_expected.yaml
```

If `dyff` reports no differences, the manual adjudication result matches the expected fixture semantically.

## Project Layout

- `src/main/java/ai/madpolicy/diplomacy/adjudication/`
  Core move adjudication logic.
- `src/main/java/ai/madpolicy/diplomacy/domain/`
  Board and game-state indexing plus normalized order/unit structures.
- `src/main/java/ai/madpolicy/diplomacy/io/`
  Builders for `adjudicated_state` and `next_state`.
- `src/test/java/ai/madpolicy/diplomacy/adjudication/`
  Focused adjudication and validator tests.
- `src/test/java/ai/madpolicy/diplomacy/regression/`
  Regression harness and corpus test.
- `data/boards/standard.yaml`
  Board topology.
- `src/test/resources/regression/`
  Move-phase expected results.

## Important Rules

- Only move-phase adjudication is in scope.
- Support-to-hold matches supported units ordered to `hold`, `support`, or `convoy`.
- Support-to-move matching is province-based at the destination side.
- Same-position move orders are invalid.
- Convoy endpoints are land positions in coastal provinces.
- A dislodged unit does not defend its own province against the move that dislodges it, but may still affect adjudication elsewhere.
- A fleet attacked by a convoyed army does not have its support cut when:
  - the convoy has exactly one possible route
  - and the support could alter the success of that route by supporting a convoying fleet or an attack on a convoying fleet

## Docs

- Spec: `docs/superpowers/specs/2026-04-26-move-adjudication-engine-design.md`
- Plan: `docs/superpowers/plans/2026-04-26-move-adjudication-engine.md`
