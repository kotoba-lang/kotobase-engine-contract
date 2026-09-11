(ns kotobase.engine.frontier
  "Rollback- and equivocation-aware acceptance for verified logical commits.

  Block fetching, CID recomputation, signature verification, and secure local
  persistence stay injected. This namespace owns the state transition: a
  candidate head may replace a last-seen frontier only when a verified,
  epoch-contiguous parent path proves that it is a descendant."
  (:require [kotobase.engine.identity :as identity]))

(def frontier-format "kotobase.frontier/v1")

(def frontier-keys
  #{:format :database-id :logical-commit-root :epoch})

(defn- nonblank-string? [value]
  (and (string? value) (not= "" value)))

(defn- reject! [problem message data]
  (throw (ex-info message
                  (assoc data :type :kotobase.frontier/rejected
                              :problem problem))))

(defn frontier
  "Validate the small value a client persists in secure local storage."
  [{:keys [format database-id logical-commit-root epoch] :as value}]
  (when-not (= frontier-keys (set (keys value)))
    (reject! :invalid-frontier "frontier has missing or unknown v1 fields"
             {:expected frontier-keys :actual (set (keys value))}))
  (when-not (= frontier-format format)
    (reject! :invalid-frontier "unsupported frontier format" {:format format}))
  (when-not (nonblank-string? database-id)
    (reject! :invalid-frontier "frontier requires database-id"
             {:database-id database-id}))
  (when-not (nonblank-string? logical-commit-root)
    (reject! :invalid-frontier "frontier requires logical commit root"
             {:logical-commit-root logical-commit-root}))
  (when-not (and (integer? epoch) (not (neg? epoch)))
    (reject! :invalid-frontier "frontier requires a non-negative epoch"
             {:epoch epoch}))
  value)

(defn- entry-value [{:keys [root commit] :as entry} verify-entry]
  (when-not (nonblank-string? root)
    (reject! :invalid-proof "frontier proof entry requires root" {:entry entry}))
  (let [commit (identity/logical-commit commit)]
    (when-not (true? (verify-entry entry))
      (reject! :unverified-commit
               "frontier proof entry failed CID/signature verification"
               {:root root}))
    {:root root :commit commit}))

(defn- candidate-frontier [root commit]
  {:format frontier-format
   :database-id (:database-id commit)
   :logical-commit-root root
   :epoch (:epoch commit)})

(defn- validate-step! [child parent-root parent-epoch database-id]
  (let [{:keys [root commit]} child]
    (when-not (= database-id (:database-id commit))
      (reject! :database-mismatch "commit path crosses database identities"
               {:root root :expected database-id
                :actual (:database-id commit)}))
    (when-not (= [parent-root] (:parents commit))
      (reject! :not-descendant "commit path does not reach the last-seen frontier"
               {:root root :expected-parent parent-root
                :actual-parents (:parents commit)}))
    (when-not (= (dec (:epoch commit)) parent-epoch)
      (reject! :noncontiguous-epoch "commit path epochs are not contiguous"
               {:root root :child-epoch (:epoch commit)
                :parent-epoch parent-epoch}))))

(defn accept-head
  "Accept a candidate logical head against an optional last-seen frontier.

  `path` is ordered candidate-first and contains the candidate plus every
  descendant commit down to (but excluding) `last-seen`. Each entry has
  `:root`, `:commit`, and any signature/proof material needed by
  `verify-entry`. The callback MUST recompute/bind the root and verify the
  authorized signature; only literal true is accepted.

  With no `last-seen`, exactly the verified candidate is accepted as TOFU.
  Persist the returned `:frontier` before trusting mutable discovery again."
  [{:keys [last-seen candidate-root path verify-entry]}]
  (when-not (nonblank-string? candidate-root)
    (reject! :invalid-candidate "candidate root must be a nonblank string"
             {:candidate-root candidate-root}))
  (when-not (vector? path)
    (reject! :invalid-proof "frontier proof path must be a vector" {:path path}))
  (when-not (fn? verify-entry)
    (reject! :invalid-verifier "frontier acceptance requires verify-entry" {}))
  (let [last-seen (some-> last-seen frontier)]
    (if (= candidate-root (:logical-commit-root last-seen))
      (do
        (when (seq path)
          (reject! :invalid-proof "unchanged head requires an empty proof path"
                   {:candidate-root candidate-root}))
        {:status :unchanged :distance 0 :frontier last-seen})
      (do
        (when (empty? path)
          (reject! :invalid-proof "new candidate requires a proof path"
                   {:candidate-root candidate-root}))
        (let [entries (mapv #(entry-value % verify-entry) path)
              candidate (first entries)
              candidate-commit (:commit candidate)]
          (when-not (= candidate-root (:root candidate))
            (reject! :invalid-proof "proof does not start at candidate root"
                     {:candidate-root candidate-root
                      :proof-root (:root candidate)}))
          (when-not (= (count entries) (count (set (map :root entries))))
            (reject! :cycle "frontier proof contains a repeated root" {}))
          (if-not last-seen
            (do
              (when-not (= 1 (count entries))
                (reject! :invalid-proof
                         "TOFU initialization accepts the candidate entry only"
                         {:path-count (count entries)}))
              {:status :initialized :distance 0
               :frontier (candidate-frontier candidate-root candidate-commit)})
            (let [last-root (:logical-commit-root last-seen)
                  last-epoch (:epoch last-seen)
                  database-id (:database-id last-seen)
                  candidate-epoch (:epoch candidate-commit)]
              (when-not (= database-id (:database-id candidate-commit))
                (reject! :database-mismatch "candidate belongs to another database"
                         {:expected database-id
                          :actual (:database-id candidate-commit)}))
              (cond
                (< candidate-epoch last-epoch)
                (reject! :rollback "candidate epoch precedes last-seen frontier"
                         {:last-seen-epoch last-epoch
                          :candidate-epoch candidate-epoch})

                (= candidate-epoch last-epoch)
                (reject! :equivocation
                         "different roots claim the last-seen epoch"
                         {:last-seen-root last-root
                          :candidate-root candidate-root
                          :epoch candidate-epoch})

                :else
                (do
                  (doseq [[child parent] (partition 2 1 entries)]
                    (validate-step! child (:root parent)
                                    (get-in parent [:commit :epoch]) database-id))
                  (let [tail (last entries)]
                    (validate-step! tail last-root last-epoch database-id))
                  (let [distance (- candidate-epoch last-epoch)]
                    (when-not (= distance (count entries))
                      (reject! :noncontiguous-epoch
                               "proof length does not match epoch distance"
                               {:distance distance :path-count (count entries)}))
                    {:status :advanced :distance distance
                     :frontier (candidate-frontier candidate-root
                                                   candidate-commit)}))))))))))
