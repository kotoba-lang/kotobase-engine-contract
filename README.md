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

## Verification

```sh
clojure -M:test
clojure -M:lint
npm install
npm run test:cljs
```
