(ns kotobase.engine.canonical
  "Canonical logical forms shared by engine receipts and conformance checks.

  This namespace intentionally does not hash. A caller supplies a digest
  function so the contract does not impose a codec or crypto implementation on
  every engine. Physical encodings and their CIDs remain engine-specific.")

(defn canonical-value
  "Turn arbitrary EDN data into a recursively ordered value suitable for a
  stable `pr-str`. Maps and sets are tagged so their type is not confused with
  an ordinary sequential value."
  [x]
  (cond
    (map? x)
    [:map (->> x
               (map (fn [[k v]] [(canonical-value k) (canonical-value v)]))
               (sort-by (comp pr-str first))
               vec)]

    (set? x)
    [:set (->> x (map canonical-value) (sort-by pr-str) vec)]

    (vector? x)
    [:vector (mapv canonical-value x)]

    (sequential? x)
    [:seq (mapv canonical-value x)]

    :else x))

(defn canonical-string [x]
  (pr-str (canonical-value x)))

(defn restore-canonical-value
  "Inverse of `canonical-value` for the EDN value domain."
  [x]
  (if (and (vector? x) (= 2 (count x)))
    (let [[tag body] x]
      (case tag
        :map (into {} (map (fn [[k v]] [(restore-canonical-value k)
                                        (restore-canonical-value v)])) body)
        :set (into #{} (map restore-canonical-value) body)
        :vector (mapv restore-canonical-value body)
        :seq (map restore-canonical-value body)
        x))
    x))

(defn- tx-map->datom [m]
  (when-not (and (contains? m :e) (contains? m :a) (contains? m :v))
    (throw (ex-info "transaction map requires :e, :a and :v"
                    {:type :kotobase.engine/invalid-datom :datom m})))
  {:e (:e m)
   :a (:a m)
   :v (:v m)
   :op (cond
         (contains? m :op) (:op m)
         (contains? m :added) (if (:added m) :assert :retract)
         :else :assert)})

(defn normalize-datom
  "Normalize the common Datom transaction forms into
  `{:e e :a a :v v :op :assert|:retract}`."
  [x]
  (let [d (cond
            (map? x) (tx-map->datom x)
            (and (vector? x) (= 4 (count x)) (= :db/add (first x)))
            {:e (nth x 1) :a (nth x 2) :v (nth x 3) :op :assert}
            (and (vector? x) (= 4 (count x)) (= :db/retract (first x)))
            {:e (nth x 1) :a (nth x 2) :v (nth x 3) :op :retract}
            (and (vector? x) (= 3 (count x)))
            {:e (nth x 0) :a (nth x 1) :v (nth x 2) :op :assert}
            :else
            (throw (ex-info "unsupported transaction datom"
                            {:type :kotobase.engine/invalid-datom :datom x})))]
    (when-not (#{:assert :retract} (:op d))
      (throw (ex-info "datom :op must be :assert or :retract"
                      {:type :kotobase.engine/invalid-datom :datom x})))
    d))

(defn normalize-tx [tx-data]
  (when-not (sequential? tx-data)
    (throw (ex-info ":tx-data must be sequential"
                    {:type :kotobase.engine/invalid-transaction})))
  (mapv normalize-datom tx-data))

(defn logical-datom-key [{:keys [e a v]}]
  (canonical-string [e a v]))

(defn canonical-datoms [datoms]
  (->> datoms
       (map #(select-keys % [:e :a :v :t :added]))
       (sort-by (juxt logical-datom-key :t :added))
       vec))

(defn checkpoint-datoms
  "Canonical visible-state projection used for cross-engine logical roots.
  Transaction coordinates are audit metadata and are deliberately excluded."
  [datoms]
  (->> datoms
       (map #(select-keys % [:e :a :v]))
       (sort-by logical-datom-key)
       vec))
