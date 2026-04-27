# Move Adjudication Engine Design

## Context

Diplomacy is a simultaneous-order strategy game. During a move phase, all nations submit orders at the same time, and those orders are then resolved together. That resolution step is called adjudication.

This spec covers only move-phase adjudication. It defines how submitted move-phase orders are resolved into successes, failures, dislodgements, standoffs, and a post-move state suitable for the retreat phase.

## Design Intent

This document is intended to be implementation-oriented rather than tutorial-oriented.

The core sources of truth are:

- the board model, which is authoritative for topology, coasts, and occupiable positions
- the move-state schema, which is authoritative for unit placement and submitted orders
- the regression corpus, which is the primary behavioral oracle for expected adjudication outcomes

This spec defines required behavior and output semantics. It does not prescribe a specific algorithm, data structure strategy, or programming language.

## Purpose

Define a language-agnostic move-phase adjudication engine for Diplomacy that:

- reads the board model and a move-phase game state
- produces an `adjudicated_state` with resolved move orders
- produces a `next_state` for the retreat phase

This spec covers adjudication behavior only. It does not prescribe any specific implementation strategy or programming language.

## Inputs

The engine accepts:

- a board document in the `metadata/boards/board.yaml` schema
- a game-state document in the existing state schema with:
  - `kind: game_state`
  - `phase.step: move`
  - every unit carrying an order
  - every order beginning with `status: new`

## Outputs

The engine produces two artifacts.

### `adjudicated_state`

This is the same logical move-state document that was provided as input.

Rules:

- original move orders are preserved
- every order `status` is changed from `new` to either `success` or `failure`
- every failed order receives exactly one valid failure comment
- dislodged units receive `dislodged_by`
- top-level `standoffs` is populated
- no other game-state data is changed semantically

### `next_state`

This is a new game-state document representing the board immediately after move adjudication and immediately before retreat adjudication.

Rules:

- `phase.step` must be `retreat`
- units are relocated to their resolved post-move positions
- dislodged units remain present and retain `dislodged_by`
- top-level `standoffs` is preserved
- every unit order is reset to:
  - `type: hold`
  - `status: new`
- move-phase failure comments must not remain
- move-phase-only order fields such as `to`, `from`, and `via_convoy` must not remain

Presence of `dislodged_by` in `next_state` means the unit must either retreat or be disbanded during the retreat phase.

## Scope

This spec covers only move-phase adjudication.

Included:

- `hold`, `move`, `support`, and `convoy` orders
- movement contests
- supports and support cutting
- convoy routing and convoy failure
- standoffs
- dislodgement
- self-dislodgement restrictions

Excluded:

- diplomacy or order-writing workflow
- retreat adjudication
- build/disband adjudication
- supply-center ownership updates
- victory detection
- phase progression beyond producing `next_state`

## State Semantics

The board model distinguishes between province ids and position ids.

- a `province id` is a logical province identifier such as `tri`, `eng`, or `bul`
- a `position id` is a concrete occupiable position such as `tri_c`, `tri_l`, `eng_s`, `bul_ec`, or `bul_sc`

The adjudicator uses position ids as the primary movement graph.

Rules:

- each unit occupies exactly one position id
- each position belongs to exactly one province
- multiple positions may belong to the same province
- at most one non-dislodged unit may occupy a province after move adjudication
- a dislodged unit may temporarily coexist with the successful occupying unit in the same province, or even the same position, in `next_state`
- province ids are used for province-level effects such as `standoffs` and `dislodged_by`

## Order Types

Recognized move-phase order kinds are:

- `hold`
- `move`
- `support`
- `convoy`

Encoding rules:

- support-to-hold is encoded as `support` with `from == to`
- support-to-move is encoded as `support` with `from != to`
- convoy endpoints `from` and `to` must be land positions
- convoying fleets must occupy sea positions
- a convoyed army move with `via_convoy: true` is a land-to-land move between provinces that both contain at least one coast position

## Order Validation

Validation is order-type specific.

### Hold Validation

A `hold` order is legal if:

- `type` is `hold`
- the issuing unit occupies a valid board position
- no additional order fields are present

A `hold` order is illegal if any of those conditions fail.

### Move Validation

A `move` order is legal if:

- `type` is `move`
- `to` is present
- `via_convoy` is present
- the issuing unit occupies a valid board position
- `to` names a valid board position

Then legality branches by move kind.

Direct move legality:

- if `via_convoy` is `false`, the move is legal only if there exists a board adjacency containing both the unit's current position and `to`

Convoyed army move legality:

- if `via_convoy` is `true`, the issuing unit must be an army
- the army's current position must be a land position
- `to` must be a land position
- the army's origin province must contain at least one coast position
- the destination province must contain at least one coast position

A `move` order is illegal if any of the above conditions fail.

A legal move order may still fail later during adjudication because of:

- standoff
- insufficient strength
- failed convoy
- self-dislodgement restrictions
- other normal resolution rules

### Support Validation

A `support` order is legal if:

- `type` is `support`
- `from` is present
- `to` is present
- the issuing unit occupies a valid board position
- `from` names a valid board position
- `to` names a valid board position
- the supporting unit could legally move to at least one position in the province containing `to`

Support kind:

- if `from == to`, it is support-to-hold
- if `from != to`, it is support-to-move

A `support` order is illegal if any of the above conditions fail.

Notes:

- support legality does not require that a unit actually exist at `from`
- support is province-based at the destination side, not exact-position-based
- a fleet may support movement into a coastal province if it could legally move to that province's coast

### Convoy Validation

A `convoy` order is legal if:

- `type` is `convoy`
- `from` is present
- `to` is present
- the issuing unit occupies a valid board position
- the issuing unit occupies a sea position
- `from` names a valid board position
- `to` names a valid board position
- `from` is a land position
- `to` is a land position
- the `from` province contains at least one coast position
- the `to` province contains at least one coast position

A `convoy` order is illegal if any of the above conditions fail.

Notes:

- convoy legality does not require that an army actually exist at `from`
- convoy legality does not require that a complete route already exist

## Adjudication Outcomes

For each unit, adjudication determines:

- whether its order succeeds or fails
- which failure comment applies, if it fails
- whether the unit is dislodged
- if dislodged, the province id recorded in `dislodged_by`

For the state as a whole, adjudication determines:

- the set of province ids where standoffs occurred
- the resolved post-move placements used to construct `next_state`

The adjudicator must resolve interdependent order effects in a way that is independent of processing order and produces a stable final outcome.

## Move Contest Resolution

A move succeeds only if:

- the move order is legal
- any required convoy route succeeds
- its effective attack strength strictly exceeds the strength opposing it at the destination
- it is not negated by self-dislodgement rules

A move fails if any of those conditions is not met.

Rules:

- destination resolution is province-based
- at most one non-dislodged unit may emerge as the successful occupant of a province
- a unit ordered to move that does not successfully leave its province defends its origin province with strength 1, plus any valid support-to-hold that applies to it
- such a unit may therefore prevent another move into its origin province and may itself need to be dislodged in order for an attacker to enter

## Standoffs

A province is a standoff province when:

- two or more moves into that province have equal highest effective strength
- no move into that province has strictly greater effective strength than all others

Effects of standoff:

- no contending move into that province succeeds
- the province id is added to `standoffs`
- a standoff does not itself dislodge a unit already in the province

## Dislodged Units

A dislodged unit does not contribute defensive strength in its own province and does not prevent the move that dislodges it.

However, a dislodged unit may still affect adjudication elsewhere in the same turn if that effect does not oppose the attack that dislodged it.

In particular:

- it does not contribute to defense in its own province once dislodged
- it does not create a standoff against the move that dislodges it
- it may still cut support in another province
- it may still have other effects outside the province interaction that dislodged it, if allowed by the normal move-phase rules
- it may still cause a standoff in a different province even if it is dislodged this turn

## Support Semantics

A support order contributes strength only if all of the following are true:

- the support order is legal
- a unit exists at `from`
- the supported unit issued the matching order
- the support is not cut
- the support is not negated by self-dislodgement restrictions

Support-to-hold:

- if `from == to`, the support applies only if the unit at `from` issued a `hold`, `support`, or `convoy` order
- valid support-to-hold adds 1 defensive strength to that unit in its origin province

Support-to-move:

- if `from != to`, the support applies only if the unit at `from` issued the exact move `from -> to`
- valid support-to-move adds 1 attacking strength to that move

A support order fails if any requirement above is not met.

### Support Cutting

A support is cut if:

- the supporting unit is attacked from any province other than the province that is the target of the supported action

A support is not cut if:

- the only attack on the supporting unit comes from the province that is the target of the supported action

A support is also cut if:

- the supporting unit is dislodged by an attack other than the one from the province that is the target of the supported action

Notes:

- a dislodged unit may still cut support elsewhere in the turn
- but a unit cannot use its own dislodgement to cut support for the attack that dislodges it
- if a convoyed attack targets the supporting fleet's province, the support is not cut when:
  - the convoy has exactly one possible route
  - and the support could alter the survival of that route by supporting a convoying fleet or an attack on a convoying fleet
- in that single-route convoy exception, the protected support may still apply even if the supporter is dislodged by the convoyed attack

Mismatch is not the same as support cutting:

- support-to-hold fails if the supported unit did not order `hold`, `support`, or `convoy`
- support-to-move fails if the supported unit did not issue the exact supported move

## Convoy Semantics

A convoy order contributes to a convoy route only if all of the following are true:

- the convoy order is legal
- an army exists at `from`
- the unit at `from` issued a matching `move` order with `via_convoy: true`
- the convoying fleet is not dislodged

A convoyed move succeeds only if:

- the move order itself is legal
- at least one complete convoy route exists from the army's origin province to the destination province
- every convoy order in at least one such route contributes successfully
- the move then also succeeds under normal movement contest rules

A convoy route is complete when:

- it starts from a sea position adjacent to a coast of the army's origin province
- it ends at a sea position adjacent to a coast of the destination province
- each fleet in the route occupies a sea position
- each consecutive pair of fleets in the route is adjacent by the board graph
- every fleet in the route issued a matching convoy order for the same `from` and `to`

Effects of convoy failure:

- if no complete surviving route exists, the convoyed army move fails
- a dislodged convoying fleet contributes nothing
- an attack on a convoying fleet that does not dislodge it does not disrupt the convoy
- if multiple possible convoy routes exist, one surviving complete route is sufficient

## Self-Dislodgement

A nation may not dislodge its own unit.

Effects:

- if a move would otherwise succeed but would dislodge a unit belonging to the same nation, that move fails
- support that would cause self-dislodgement does not count
- convoy participation does not override this rule

## Failure Comments

Every failed order must include exactly one failure comment drawn from this closed set:

- `Illegal order.`
- `No unit to support.`
- `Supported unit did not make corresponding order.`
- `No army to convoy.`
- `Convoyed unit did not make corresponding order.`
- `Convoy failed.`
- `Support cut.`
- `Standoff.`
- `Overwhelmed.`
- `Cannot dislodge own unit.`

Successful orders must not include a failure comment.

## Failure Comment Selection

Each failed order receives the single most specific applicable comment from the following priority order.

For all order types:

- `Illegal order.`
  - use when the order fails validation

For `support`:

- `No unit to support.`
  - use when the support order is legal, but no unit exists at `from`
- `Supported unit did not make corresponding order.`
  - use when a unit exists at `from`, but its actual order does not match the supported hold or move
- `Support cut.`
  - use when the support order would otherwise apply, but is cut by attack or dislodgement

For `convoy`:

- `No army to convoy.`
  - use when the convoy order is legal, but no army exists at `from`
- `Convoyed unit did not make corresponding order.`
  - use when an army exists at `from`, but it did not issue the matching convoyed move

For `move`:

- `Cannot dislodge own unit.`
  - use when the move would otherwise succeed, but is negated by self-dislodgement prohibition
- `Convoy failed.`
  - use only on the convoyed army move order when the move required convoy but no complete surviving route exists
- `Standoff.`
  - use when the move fails because its destination province is a standoff province
- `Overwhelmed.`
  - use when a `move` or `hold` order fails because superior opposing force defeats the unit's position and no higher-priority comment applies

Notes:

- `Convoy failed.` applies only to the convoyed army move order
- convoy fleet orders do not receive `Convoy failed.` merely because the overall route failed

## Annotation Rules

### `adjudicated_state`

Rules:

- preserve the original move orders
- replace every `status: new` with the adjudicated terminal status
- add exactly one failure `comment` to every failed order
- add `dislodged_by` to every dislodged unit
- write the final `standoffs` list
- do not otherwise change the game state semantically

### `next_state`

Rules:

- units must be relocated to their post-adjudication positions
- dislodged units remain present and retain `dislodged_by`
- `phase.step` must be `retreat`
- every unit order must be reset to:
  - `type: hold`
  - `status: new`
- move-phase comments must not remain
- move-phase-only order fields must not remain
- `standoffs` must be preserved

## Conformance and Regression Testing

The regression corpus is the acceptance oracle for `adjudicated_state`.

For each regression file:

1. Read the regression file as the expected adjudicated move state.
2. Construct a pre-adjudicated in-memory input state by normalizing that file:
   - set every order status to `new`
   - remove every order `comment`
   - remove every unit `dislodged_by`
   - replace top-level `standoffs` with an empty list
3. Adjudicate the normalized input using the board model.
4. Compare the produced `adjudicated_state` to the original regression file for semantic equality.

Comparison rules:

- field ordering is never significant
- semantic content must match exactly:
  - order status
  - failure comment
  - `dislodged_by`
  - `standoffs`
  - all unchanged original data

`next_state` is conformant if it is semantically consistent with the adjudication result:

- `phase.step` is `retreat`
- unit positions match resolved post-move occupancy
- dislodged units remain present
- dislodged units retain `dislodged_by`
- every unit order is reset to `hold/new`
- move-phase comments are absent
- `standoffs` matches the adjudication result
