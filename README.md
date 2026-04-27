# madpolicy.ai

Java 21 + Spring Boot + Maven implementation of a Diplomacy move-phase adjudication engine.

## Scope

This repo currently covers move-phase adjudication only. It reads a board plus move-phase game state and produces:

- an `adjudicated_state`
- a `next_state` with `phase.step: retreat`

The primary behavioral oracle is the regression corpus in `metadata/regression`.

## Requirements

- Java 21
- Maven

Recommended shell setup:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Use a workspace-local Maven repo:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 ...
```

## Key Commands

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
- `metadata/boards/board.yaml`
  Board topology.
- `metadata/regression/`
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
