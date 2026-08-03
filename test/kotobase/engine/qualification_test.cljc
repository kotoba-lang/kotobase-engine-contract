(ns kotobase.engine.qualification-test
  (:require [clojure.test :refer [deftest is]]
            [kotobase.engine.profile :as profile]
            [kotobase.engine.qualification :as qualification]))

(deftest semantic-failure-blocks-default-selection
  (let [result (qualification/evaluate
                {:engine profile/prolly
                 :semantic (zipmap qualification/semantic-gates (repeat true))
                 :resilience {}
                 :performance {}})]
    (is (= :qualifying (:qualification/status result)))
    (is (empty? (:qualification/missing-semantic result))))
  (let [result (qualification/evaluate
                {:engine profile/prolly
                 :semantic {:add-query true}
                 :resilience {}
                 :performance {}})]
    (is (= :blocked (:qualification/status result)))
    (is (contains? (:qualification/missing-semantic result)
                   :typed-value-roundtrip))))

