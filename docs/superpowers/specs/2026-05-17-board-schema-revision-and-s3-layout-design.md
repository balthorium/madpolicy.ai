# Board Schema, Revision, And S3 Layout Design

## Summary

Board and game-state documents should distinguish structural format from content edits.
Use `schema` to identify the expected document structure for a given document type, and
use `revision` to identify edits to a specific document instance.

Board definitions should also become explicitly version-addressable repository objects.
A game state should identify both the logical board ID and the specific board revision it
depends on.

This model should work both for the current local repository layout and for a future S3
bucket that serves as the authoritative board-definition repository.

## Goals

- Replace ambiguous `version` fields with explicit `schema` and `revision` fields.
- Keep board identity stable through `id`.
- Make game states refer to an exact board definition, not an implicit latest board.
- Define a storage layout that works cleanly in S3.
- Keep document validation exact and deterministic.

## Non-Goals

- Support for resolving “latest” board definitions.
- Global schema numbering shared across all document kinds.
- In-place mutation of previously published board revisions.
- Implementation of S3 integration in this phase.

## Document Contract

### Board Document

Board YAML should use:

```yaml
kind: board
schema: 1
id: standard
revision: 3
provinces:
  ...
adjacency:
  ...
```

Field meanings:

- `kind`: document type discriminator
- `schema`: expected board-document structure version
- `id`: stable logical board identifier
- `revision`: content revision of this board definition

### Game State Document

Game-state YAML should use:

```yaml
kind: game_state
schema: 1
revision: 12
board: standard
board_revision: 3
phase:
  ...
nations:
  ...
standoffs: []
```

Field meanings:

- `kind`: document type discriminator
- `schema`: expected game-state document structure version
- `revision`: content revision of this specific game-state document
- `board`: logical board identifier
- `board_revision`: exact board-definition revision used by the state

## Naming Principles

- `schema` is about file structure, validation, and parsing expectations.
- `revision` is about edits to one concrete document’s content.
- `board_revision` is kept distinct from game-state `revision` because they refer to
  different artifacts.
- `board` remains the concise domain name in Java and YAML, consistent with the rest of
  the model style in this repository.

## Local Repository Layout

The local repo should mirror the future S3 addressing model as closely as practical.

Recommended layout:

- `data/boards/<board-id>/<board-revision>.yaml`

Example:

- `data/boards/standard/3.yaml`

Validation rules:

- directory name must match YAML `id`
- filename stem must match YAML `revision`
- YAML `kind` must be `board`
- YAML `schema` must be supported by the loader

## S3 Layout

The S3 bucket should store board definitions under:

- `boards/<board-id>/<board-revision>.yaml`

Example:

- `boards/standard/3.yaml`

This keeps storage location aligned with lookup inputs:

- board ID
- board revision

No “latest” indirection should be supported. Every board lookup must specify both values.

## Resolution Contract

To resolve a board for a game state:

1. read `board`
2. read `board_revision`
3. resolve storage object `boards/<board>/<board_revision>.yaml`
4. parse the board document
5. validate:
   - YAML `id == board`
   - YAML `revision == board_revision`
   - YAML `schema` is supported

Failure conditions should be hard errors:

- board object missing
- unsupported schema
- mismatched board ID
- mismatched board revision
- malformed YAML

## Loader And Validation Guidance

Board loaders should not infer identity from content alone or path alone. They should use
both and require agreement.

Game-state loaders should treat `board` and `board_revision` as required fields.

Board and game-state `schema` should be interpreted independently. A `schema: 2` board
document and a `schema: 1` game-state document are not inherently inconsistent.

## Migration Implications

This contract implies replacing current `version` usage in both board and game-state
documents.

Migration targets:

- board `version` -> `schema`
- game-state `version` -> `schema`
- add board `revision`
- add game-state `revision`
- add game-state `board_revision`

Any Java model or test code currently using `version` as a top-level document field will
need to be updated to the new split.

## Testing Strategy

Add or update tests for:

- board YAML parsing with `schema`, `id`, and `revision`
- game-state YAML parsing with `schema`, `revision`, `board`, and `board_revision`
- board lookup by `(board, board_revision)`
- failure on path/content board ID mismatch
- failure on path/content board revision mismatch
- failure on unsupported schema
- regression harness behavior with exact board revision resolution

## Risks

- This is a schema migration, not just a naming change, so many fixtures will change.
- Existing code may implicitly treat top-level `version` as both schema and content
  revision; those assumptions must be removed carefully.
- Moving local board fixtures into per-board subdirectories will change loader behavior
  and tests at the same time.
