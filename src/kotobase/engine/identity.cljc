(ns kotobase.engine.identity
  "Engine-independent Kotobase logical identity and engine-specific
  publication envelopes. Cryptographic hashing and signing remain injected;
  this namespace fixes the values and domains they receive."
  (:require [kotobase.engine.canonical :as canonical]))

(def logical-commit-format "kotobase.logical-commit/v1")
(def physical-publication-format "kotobase.physical-publication/v1")
(def signature-format "kotobase.logical-signature/v1")

(def logical-commit-keys
  #{:format :database-id :epoch :parents :tx-root
    :logical-checkpoint-root :schema-root :model-contract-root
    :admission-policy-root})

(def physical-publication-keys
  #{:format :logical-commit-root :engine-profile :engine-format
    :physical-root})

(defn- nonblank-string? [x]
  (and (string? x) (not= "" x)))

(defn- invalid! [message data]
  (throw (ex-info message (assoc data :type :kotobase.engine/invalid-identity))))

(defn logical-commit
  "Validate and normalize a v1 logical commit.

  V1 deliberately admits zero or one parent. The currently implemented
  publication rule is a linear CAS chain; accepting multiple parents before
  merge semantics exist would make a DAG-shaped object with undefined database
  semantics. A future merge-capable format may widen this invariant."
  [{:keys [format database-id epoch parents tx-root logical-checkpoint-root
           schema-root model-contract-root admission-policy-root]
    :as commit}]
  (when-not (= logical-commit-keys (set (keys commit)))
    (invalid! "logical commit has missing or unknown v1 fields"
              {:expected logical-commit-keys :actual (set (keys commit))}))
  (when-not (= logical-commit-format format)
    (invalid! "unsupported logical commit format" {:format format}))
  (when-not (nonblank-string? database-id)
    (invalid! "logical commit requires database-id" {:database-id database-id}))
  (when-not (and (integer? epoch) (not (neg? epoch)))
    (invalid! "logical commit requires a non-negative epoch" {:epoch epoch}))
  (when-not (and (vector? parents) (<= (count parents) 1)
                 (every? nonblank-string? parents))
    (invalid! "v1 logical commit requires zero or one parent" {:parents parents}))
  (doseq [[field value]
          [[:tx-root tx-root]
           [:logical-checkpoint-root logical-checkpoint-root]
           [:schema-root schema-root]
           [:model-contract-root model-contract-root]
           [:admission-policy-root admission-policy-root]]]
    (when-not (nonblank-string? value)
      (invalid! "logical commit root must be a nonblank string"
                {:field field :value value})))
  commit)

(defn logical-commit-string [commit]
  (canonical/canonical-domain-string :logical-commit (logical-commit commit)))

(defn signature-payload
  "Domain-separated canonical string signed for one logical commit root.
  Issuer and key id are included so a valid signature cannot be transplanted
  between authorities or interpreted under an ambient key choice."
  [{:keys [logical-commit-root issuer key-id]}]
  (doseq [[field value] [[:logical-commit-root logical-commit-root]
                         [:issuer issuer]
                         [:key-id key-id]]]
    (when-not (nonblank-string? value)
      (invalid! "logical signature field must be a nonblank string"
                {:field field :value value})))
  (canonical/canonical-domain-string
   :signature
   {:format signature-format
    :logical-commit-root logical-commit-root
    :issuer issuer
    :key-id key-id}))

(defn physical-publication
  "Validate the engine-specific availability witness for a logical commit.
  This value is intentionally not the logical database identity."
  [{:keys [format logical-commit-root engine-profile engine-format physical-root]
    :as publication}]
  (when-not (= physical-publication-keys (set (keys publication)))
    (invalid! "physical publication has missing or unknown v1 fields"
              {:expected physical-publication-keys
               :actual (set (keys publication))}))
  (when-not (= physical-publication-format format)
    (invalid! "unsupported physical publication format" {:format format}))
  (when-not (nonblank-string? logical-commit-root)
    (invalid! "physical publication requires logical commit root" {}))
  (when-not (keyword? engine-profile)
    (invalid! "physical publication requires engine profile" {}))
  (when-not (and (integer? engine-format) (pos? engine-format))
    (invalid! "physical publication requires positive engine format" {}))
  (when-not (nonblank-string? physical-root)
    (invalid! "physical publication requires physical root" {}))
  publication)

(defn physical-publication-string [publication]
  (canonical/canonical-domain-string
   :physical-publication
   (physical-publication publication)))
