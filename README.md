# kotobase-engine-contract

Provider- and storage-engine-neutral contracts for Kotobase database engines.
It is the seam that lets the same Datom semantics run over a memory oracle,
Prolly snapshots, Merkle-LSM runs, or a future engine without teaching the API
layer about any physical tree.

The contract deliberately separates three identities:

- `:tx-root` commits to the canonical logical transaction;
- `:logical-checkpoint-root` commits to the visible logical Datom state at a
  checkpoint;
- `:physical-root` names an engine-specific snapshot or manifest.

Physical roots are not expected to match between engines. A Prolly root and a
Merkle-LSM manifest can represent the same logical state while having different
CIDs. Cross-engine equivalence is established by transaction/query/history
answers and the logical checkpoint root.

`checkpoint` and physical maintenance are deliberately different contracts.
A checkpoint computes a logical-state commitment and may leave the physical
layout unchanged. Optional `IMaintenance` performs real compaction/folding and
must report the before/after physical roots, work units, and whether work was
completed or was a no-op. Promotion tooling must never label a checkpoint
duration as compaction evidence.

## Repositories around this seam

| responsibility | owner |
|---|---|
| Datom representation | `kotoba-lang/datom` |
| Datomic-shaped logical semantics | `kotoba-lang/datom-model` |
| storage-free Datalog | `kotoba-lang/datalog` |
| engine contract, restore seam and oracle | this repository |
| immutable Prolly structure | `kotoba-lang/prolly-tree` |
| immutable sorted runs/compaction | `kotoba-lang/merkle-lsm` |
| block/ref/large-object provider contract | `kotoba-lang/kotobase-storage` |
| S3/R2/B2/Filecoin implementations | provider and archive adapters |
| tenant/auth/HTTP deployment | `network-awai/net-kotobase` |

`kotobase.engine.memory` is a small zero-dependency correctness oracle, not a
production database. `kotobase.engine.conformance/verify` runs the same semantic
checks against any `IEngine` implementation. Engine operations return an
immediate value, JVM `CompletionStage`, or JavaScript `Promise`; the same
conformance runner composes all forms.

Semantic PASS alone does not select a default. `kotobase.engine.qualification`
adds independent resilience and workload-specific performance evidence gates;
the initial candidate inventory is in `docs/qualification-matrix.edn`. The
Prolly candidate has passed manifest-only cold mutation and second-process
reopen against real R2 semantics and is ready for server shadow traffic. It is
not yet the production default: production latency distributions and restore
throughput remain required evidence. The
legacy peer Prolly path remains a compatibility baseline because it currently
stringifies every non-Link Datom position and therefore cannot yet claim typed
Datom round-trip conformance.

## Engine profiles

Profiles describe a physical strategy without making it doctrine:

- `:memory`
- `:prolly`
- `:merkle-lsm`
- `:lsm-prolly-checkpoint`

The composite profile is a third experiment, not an assertion that Prolly and
Merkle-LSM must always be deployed together.

Filecoin/FEVM are not engine profiles. `kotobase.engine.archive` models them as
asynchronous archive and anchor lifecycles outside the foreground commit/query
path.

## Operation layers

`kotobase.engine.surface` is the authoritative layer classifier for the public
surface. It distinguishes primitive engine state, storage-independent semantic
queries, provider diagnostics, projections, archives, and anchors.

In particular, `dbStats` is provider diagnostics and `view` is a projection
operation. Neither is a portable IEngine read or a cross-engine promotion
gate. A projection implementation must version its definition and source
commit barrier independently; FEVM anchoring remains asynchronous and cannot
be inserted into the foreground database path.

## Verification

```sh
clojure -M:test
clojure -M:lint
npm install
npm run test:cljs
```
