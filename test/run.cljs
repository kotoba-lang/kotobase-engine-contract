(ns run
  (:require [cljs.test :as test]
            [kotobase.engine.archive-test]
            [kotobase.engine.conformance-test]
            [kotobase.engine.contract-test]
            [kotobase.engine.profile-test]
            [kotobase.engine.surface-test]
            [kotobase.engine.qualification-test]))

(let [result (test/run-all-tests #"kotobase\.engine\..*-test")]
  (when (pos? (+ (:fail result 0) (:error result 0)))
    (js/process.exit 1)))
