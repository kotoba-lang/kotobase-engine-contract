(ns kotobase.engine.conformance-test
  (:require [clojure.test :refer [deftest is]]
            [kotobase.engine.conformance :as conformance]
            [kotobase.engine.memory :as memory]))

(defn test-digest [s] (str "test-digest:" s))

(deftest memory-oracle-satisfies-the-shared-contract
  (let [report (conformance/verify (memory/memory-engine test-digest))]
    (is (:passed? report))
    (is (= 9 (count (:checks report))))))

