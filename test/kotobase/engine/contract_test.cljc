(ns kotobase.engine.contract-test
  (:require [clojure.test :refer [deftest is]]
            [kotobase.engine.contract :as contract]
            [kotobase.engine.memory :as memory]))

(defn test-digest [s] (str "test-digest:" s))

(deftest receipts-separate-logical-and-physical-identity
  (let [eng (memory/memory-engine test-digest)
        s0 (contract/empty-state eng "db/a")
        {:keys [state receipt]}
        (contract/transact eng s0 {:database-id "db/a" :request-id "1"
                                   :tx-data [["e" "a" "v"]]})
        checkpoint (contract/checkpoint eng (contract/open-snapshot eng state))]
    (is (string? (:tx-root receipt)))
    (is (string? (:physical-root receipt)))
    (is (string? (:logical-checkpoint-root checkpoint)))
    (is (not= (:physical-root receipt) (:logical-checkpoint-root checkpoint)))))

(deftest request-id-conflict-is-loud
  (let [eng (memory/memory-engine test-digest)
        s0 (contract/empty-state eng "db/a")
        s1 (:state (contract/transact
                    eng s0 {:database-id "db/a" :request-id "same"
                            :tx-data [["e" "a" "v1"]]}))]
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
         #"request-id"
         (contract/transact eng s1 {:database-id "db/a" :request-id "same"
                                    :tx-data [["e" "a" "v2"]]})))))
