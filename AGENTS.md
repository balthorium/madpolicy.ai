# AGENTS.md

## Purpose
This repository contains a Java 21 + Spring Boot + Maven implementation of a Diplomacy move-phase adjudication engine.

The current goal is:
- keep the move-phase adjudicator passing the move-phase regression corpus in `metadata/regression`
- produce both:
  - an `adjudicated_state`
  - a `next_state` with `phase.step: retreat`

## Environment
- Repository root: `/Users/adb/Desktop/madpolicy.ai`
- Java version target: 21
- Local Java 21 home:
  - `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- Maven should be run with a workspace-local repo:
  - `mvn -Dmaven.repo.local=$PWD/.m2 ...`

Recommended shell setup before Java/Maven work:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

## Key Docs
- Spec:
  - `docs/superpowers/specs/2026-04-26-move-adjudication-engine-design.md`
- Plan:
  - `docs/superpowers/plans/2026-04-26-move-adjudication-engine.md`

## Important Rule Decisions
- Only move-phase adjudication is in scope for this engine.
- Support-to-hold matches supported units ordered to `hold`, `support`, or `convoy`.
- Support-to-move matching is province-based on the destination side, not exact-position based.
- A dislodged unit:
  - does not defend its own province against the move that dislodges it
  - may still affect adjudication elsewhere in the turn
  - may still cut support in another province, as long as that support is not contributing to the dislodgement
- A fleet attacked by a convoyed army does not have its support cut if:
  - the convoy has exactly one possible route
  - and that support could alter the success of that route by supporting a convoying fleet or an attack on a convoying fleet
- In that single-route convoy exception, the protected support may still apply even if the supporter is dislodged by the convoyed attack.
- Same-position move orders are invalid.
- Convoy endpoints are land positions in coastal provinces.
- Convoy orders must be validated against whether the specific fleet can lie on a possible convoy route.

## Main Source Areas
- App entry:
  - `src/main/java/ai/madpolicy/diplomacy/DiplomacyApplication.java`
- Models:
  - `src/main/java/ai/madpolicy/diplomacy/model/`
- Board/state indexing:
  - `src/main/java/ai/madpolicy/diplomacy/domain/BoardIndex.java`
  - `src/main/java/ai/madpolicy/diplomacy/domain/GameStateIndex.java`
- Adjudication:
  - `src/main/java/ai/madpolicy/diplomacy/adjudication/OrderValidator.java`
  - `src/main/java/ai/madpolicy/diplomacy/adjudication/MoveAdjudicator.java`
  - `src/main/java/ai/madpolicy/diplomacy/adjudication/FailureComment.java`
- Output builders:
  - `src/main/java/ai/madpolicy/diplomacy/io/AdjudicatedStateBuilder.java`
  - `src/main/java/ai/madpolicy/diplomacy/io/NextStateBuilder.java`
- Service layer:
  - `src/main/java/ai/madpolicy/diplomacy/app/AdjudicationService.java`

## Main Test Areas
- Micro adjudication tests:
  - `src/test/java/ai/madpolicy/diplomacy/adjudication/MoveAdjudicatorMicroTest.java`
- Validator tests:
  - `src/test/java/ai/madpolicy/diplomacy/adjudication/OrderValidatorTest.java`
- Regression harness:
  - `src/test/java/ai/madpolicy/diplomacy/regression/RegressionHarness.java`
  - `src/test/java/ai/madpolicy/diplomacy/regression/RegressionCorpusTest.java`

## Regression Harness Behavior
The regression harness:
1. reads each move-phase fixture in `metadata/regression`
2. normalizes it back to pre-adjudicated state by:
   - setting all order statuses to `new`
   - removing order comments
   - removing `dislodged_by`
   - clearing `standoffs`
3. adjudicates it
4. rebuilds the `adjudicated_state`
5. compares semantic equality with the original fixture

## Current Status
As of 2026-04-27:
- focused micro adjudication tests are passing
- the move-phase regression corpus is passing
- the adjudicator is producing both `adjudicated_state` and `next_state`

Last verified focused command:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=MoveAdjudicatorMicroTest
```

Last verified regression command:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RegressionCorpusTest
```

## Fixture Updates Already Made
Several regression fixtures were updated to match the agreed spec/rules. Do not blindly revert them.

Notable examples include:
- `metadata/regression/datc_6q.yaml`
- `metadata/regression/datc_6r.yaml`
- `metadata/regression/datc_6s.yaml`
- `metadata/regression/datc_6t.yaml`
- `metadata/regression/datc_1e.yaml`
- `metadata/regression/datc_1g.yaml`
- `metadata/regression/2ed_example2.yaml`
- `metadata/regression/datc_2m.yaml`
- `metadata/regression/datc_6k.yaml`
- `metadata/regression/datc_7b.yaml`
- `metadata/regression/datc_7d.yaml`
- `metadata/regression/datc_7g.yaml`
- `metadata/regression/datc_7k_alt.yaml`
- `metadata/regression/datc_8i.yaml`
- `metadata/regression/test1.yaml`

## Working Notes
- Prefer adding a focused microtest before changing adjudication logic.
- Treat the spec as authoritative when the corpus is clearly inconsistent, but verify carefully before changing fixtures.
- The current adjudicator behavior depends on iterative resolution for convoy paradox cases; preserve that structure unless there is strong reason to replace it.
- If Maven cannot resolve dependencies, that is likely an environment/network limitation rather than a known project regression.

## Notes for the Next Agent
- Use `apply_patch` for edits.
- Do not revert unrelated user changes.
- Do not change any regression `status` field without explicit user confirmation first.
