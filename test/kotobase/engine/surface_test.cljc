(ns kotobase.engine.surface-test
  (:require [clojure.test :refer [deftest is]]
            [kotobase.engine.surface :as surface]))

(deftest physical-semantics-and-extensions-have-distinct-owners
  (is (= :engine-state (surface/operation-layer :scan)))
  (is (= :semantic-query (surface/operation-layer :pull)))
  (is (= :provider-diagnostics (surface/operation-layer :db-stats)))
  (is (= :projection (surface/operation-layer :view)))
  (is (= :archive (surface/operation-layer :archive-put)))
  (is (= :anchor (surface/operation-layer :anchor-checkpoint)))
  (is (nil? (surface/operation-layer :unknown))))

(deftest only-layout-independent-reads-are-portable
  (doseq [operation [:scan :history :datoms :q :pull :as-of :basis-t]]
    (is (surface/portable-read-operation? operation) (str operation)))
  (doseq [operation [:transact :db-stats :view :archive-get
                     :anchor-checkpoint]]
    (is (not (surface/portable-read-operation? operation)) (str operation))))

(deftest duplicated-operation-ownership-is-rejected
  (is (thrown-with-msg?
       #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
       #"exactly one owning layer"
       (surface/validate-operation-layers
        {:engine-state #{:scan} :projection #{:scan}}))))
