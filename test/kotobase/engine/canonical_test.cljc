(ns kotobase.engine.canonical-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotobase.engine.canonical :as canonical]))

(defn- problem-of [thunk]
  (try
    (thunk)
    nil
    (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) error
      (:problem (ex-data error)))))

(deftest logical-identities-are-versioned-and-domain-separated
  (let [payload [[:db/add "e" :a 1]]
        tx (canonical/transaction-string payload)
        checkpoint (canonical/canonical-domain-string :checkpoint payload)]
    (is (re-find #"kotobase\.logical/v1" tx))
    (is (re-find #":transaction" tx))
    (is (not= tx checkpoint))))

(deftest transaction-v1-has-a-pinned-cross-runtime-golden-form
  (is (= "[:kotobase.logical/envelope [:format \"kotobase.logical/v1\"] [:domain :transaction] [:value [:vector [[:map [[[:keyword nil \"a\"] [:keyword nil \"a\"]] [[:keyword nil \"e\"] [:string \"e\"]] [[:keyword nil \"op\"] [:keyword nil \"assert\"]] [[:keyword nil \"v\"] [:integer 1]]]]]]]]"
         (canonical/transaction-string [[:db/add "e" :a 1]]))))

(deftest logical-values-have-portable-explicit-scalar-tags
  (let [value {:enabled true
               :owner 'person/alice
               :tags #{:b :a}
               :items [nil "x" 42]}
        canonical-value (canonical/canonical-value value)]
    (is (= value (canonical/restore-canonical-value canonical-value)))
    (is (= (canonical/canonical-string value)
           (canonical/canonical-string
            {:items [nil "x" 42]
             :tags #{:a :b}
             :owner 'person/alice
             :enabled true})))))

(deftest ambiguous-or-host-specific-numbers-fail-closed
  (testing "a future format may add explicit wrappers without moving v1"
    (is (= :floating-point-requires-explicit-codec
           (problem-of #(canonical/canonical-value 0.5))))
    (is (= :negative-zero
           (problem-of #(canonical/canonical-value -0.0))))
    (is (= :non-finite-number
           (problem-of #(canonical/canonical-value ##Inf))))
    (is (= :integer-out-of-range
           (problem-of #(canonical/canonical-value 9007199254740992))))))

(deftest unknown-domains-fail-closed
  (is (= :kotobase.engine/unknown-logical-domain
         (:type
          (try
            (canonical/canonical-domain-string :typo [])
            (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) error
              (ex-data error)))))))
