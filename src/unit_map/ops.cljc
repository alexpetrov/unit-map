(ns unit-map.ops
  (:require [unit-map.util :as u]))


;; TODO: refactor constantly repeating calls:
;; - range?
;; - concretize-range


;;;;;;;; range & s ;;;;;;;;
(defn range? [x]
  (and (map? x) (:range (meta x))))


(defn process-range [pprev prev next nnext]
  {:pre [(and (not-every? nil? [pprev nnext])
              (every? some? [prev next]))]}
  ^:range{:start (or pprev prev)
          :step  (if (nil? pprev) (- nnext next) (- prev pprev))
          :end   (or nnext next)})


(defn process-sequence* [s]
  (loop [[pprev prev x next nnext & rest] (concat [nil nil] s [nil nil])

         result []
         buffer []]
    (cond
      (nil? x)  (concat result buffer)
      (= '.. x) (recur (concat [nil nil] rest)
                       (concat result
                               (drop-last 2 buffer)
                               [(process-range pprev prev next nnext)])
                       [])
      :else     (recur (concat [prev x next nnext] rest)
                       result
                       (conj buffer x)))))


(def process-sequence (memoize process-sequence*))


(defn concretize-range [rng value]
  (u/map-v #(u/try-call % value) rng))


(defn dynamic-sequence? [ps]
  (boolean (some (every-pred range? (comp (partial some fn?) vals)) ps)))


(defn static-sequence? [ps]
  (not (dynamic-sequence? ps)))


(defn read-sequence [form]
  (vec (process-sequence form)))


(defn read-definition [form]
  (apply array-map (mapcat (fn [[u s]] [u (read-sequence s)]) (partition 2 form))))


;;;;;;;; contains & length & index ;;;;;;;;
(defn range-contains? [rng value x]
  (let [{:keys [start step end]} (concretize-range rng value)]
    (and (or (<= start x end)
             (>= start x end))
         (or (= start x)
             (= end x)
             (and (not= ##-Inf start)
                  (-> x (- start) (mod step) zero?))
             (and (not= ##Inf end)
                  (-> x (+ end) (mod step) zero?))))))


(defn range-contains-some [rng value & xs]
  (->> (sort xs)
       (filter (partial range-contains? (concretize-range rng value) value))
       first))


(defn sequence-contains-some
  "Returns first (i.e. min) x found in the s"
  [ps value x & xs]
  (let [xs (cons x xs)]
    (some (some-fn (set xs)
                   #(when (range? %) (apply range-contains-some % value xs)))
          ps)))


(defn index-in-range
  "Returns negative index if range start is infinite, 0 index will be end of range."
  [rng value x]
  (let [{:keys [start step end]} (concretize-range rng value)]
    (cond
      (not (range-contains-some rng value x))   nil
      (and (u/infinite? x) (u/infinite? start)) ##-Inf
      (u/infinite? x)                           ##Inf
      (u/infinite? start)                       (- (quot (- end x) step))
      :else                                     (quot (- x start) step))))


(defn range-length [rng value]
  (let [{:keys [start step end]} (concretize-range rng value)]
    (if (some u/infinite? [start end])
      ##Inf
      (-> (- end start) (quot step) inc))))


(defn sequence-length [ps value]
  (->> ps
       (map #(if (range? %) (range-length % value) 1))
       (reduce + 0)))


(defn sequence-first-index [ps value]
  (let [e (first ps)
        r (when (range? e) (concretize-range e value))]
    (cond
      (nil? e)                 nil
      (u/infinite? (:start r)) ##-Inf
      :else                    0)))


(defn sequence-last-index [ps value]
  (let [e (last ps)
        r (when (range? e) (concretize-range e value))]
    (cond
      (nil? e)               nil
      (u/infinite? (:end r)) ##Inf
      :else                  (dec (sequence-length ps value)))))


(defn index-in-sequence [ps value x]
  (loop [i 0, [el & rest-s] ps]
    (when (some? el)
      (or (some-> (cond
                    (= x el)    0
                    (range? el) (index-in-range el value x))
                  (+ i))
          (recur (+ i (if (and (range? el) (u/finite? (:start el)))
                        (range-length el value)
                        1))
                 rest-s)))))


(defn range-nth [rng value index]
  (let [{:keys [start step end]} (concretize-range rng value)]
    (if (u/infinite? start)
      (+ end (* step index))
      (+ start (* step index)))))


(defn sequence-nth [ps value index]
  (loop [i 0, [el & rest-s] ps]
    (when (some? el)
      (let [increment (if (and (range? el) (u/finite? (:start el)))
                        (range-length el value)
                        1)

            result (cond
                     (not (or (<= i index (+ i increment -1))
                              (neg? index)))
                     nil

                     (range? el) (range-nth el value (- index i))
                     :else       el)]
        (if (some? result)
          result
          (recur (+ i increment) rest-s))))))


;;;;;;;; type ;;;;;;;;
(defn no-default-type-exception [value]
  (ex-info "No known unit-map type specified and no :default-type is defined"
           {:value value, :meta (meta value)}))


(defn unit-value-is-incorrect [args]
  (ex-info "Unit-map has incorrect unit value"
           args))


(declare get-type)


(defn delta-type? [t] (= 2 (count t)))


(defn value-type? [t] (= 1 (count t)))


(defn value? [value] (-> value get-type value-type?))


(defn delta? [value] (-> value get-type delta-type?))


(defn dispatch-definition [v]
  (if (value? v)
    (first (get-type v))
    :default))


;; This whole type dispatching system implementation looks ugly
(defmulti definition #'dispatch-definition)


(defn get-type [x]
  (let [[v d]                 (first (meta x)) ;; TODO: maybe use namespaced-keywords instead?
        definitions           (methods definition)
        default-type-defined? (contains? definitions :default-type)
        default-type-delta?   (some? d)
        default-type-value?   (nil? v)
        typed-delta?          (keyword? d)
        defined-type?         (contains? definitions v)]
    (cond
      (and (not defined-type?)
           (not default-type-defined?)
           (or default-type-value?
               default-type-delta?))
      (throw (no-default-type-exception x))

      typed-delta?              [v d]
      defined-type?             [v]
      default-type-value?       [:default-type]
      default-type-delta?       [:default-type v]))) ;; TODO default-type-delta unreachable code


(defn main-type [t] (first t))


(defn get-main-type [x] (main-type (get-type x)))


(defn delta-type [t] (second t))


(defn get-delta-type [x] (delta-type (get-type x)))


(def integer #unit-map/sequence[##-Inf .. -2 -1 0 1 .. ##Inf])


(defn to-delta-definition
  "Does not save functions from the source definition
  result always is a static range s"
  [ps value]
  (let [first-idx (sequence-first-index ps value)
        last-idx  (sequence-last-index ps value)]
    (cond
      (every? u/infinite? [first-idx last-idx])
      integer

      (u/infinite? last-idx)
      (process-sequence [first-idx  (inc first-idx) '.. ##Inf])

      (u/infinite? first-idx)
      (process-sequence [##-Inf '.. (dec last-idx) last-idx])

      :else
      (process-sequence [first-idx (inc first-idx) '.. last-idx]))))


(defmethod definition :default [value] ;; TODO: maybe allow to use not defined types? Will work just as map with some counters
  (let [t             (get-type value)
        is-delta?     (delta-type? t)
        definition-fn (get (methods definition) (first t))]
    (when (nil? definition-fn) (throw (no-default-type-exception value)))
    (reduce-kv (if is-delta?
                 (fn [acc k v] (assoc acc k (-> (process-sequence v) (to-delta-definition value))))
                 (fn [acc k _] (assoc acc k integer)))
               {}
               (definition-fn value))))


(defn unit-definition [value unit]
  (get (definition value) unit))


(defn make-delta-type [value delta-type]
  (or (some-> (ffirst (meta value))
              (hash-map (or delta-type :delta)))
      (some-> delta-type (hash-map true))
      {:delta true}))


(defn get-next-unit [value unit]
  (u/get-next-element (keys (definition value)) unit))


(defn get-prev-unit [value unit]
  (u/get-prev-element (keys (definition value)) unit))


;;;;;;;; inc & dec ;;;;;;;;
(defn get-next-unit-value [ps value x]
  (loop [[el next & rest] ps]
    (let [{:keys [step end]} (if (range? el) (concretize-range el value) {})]
      (cond
        (nil? el)
        nil

        (or (= x el) (= x end))
        (cond-> next (range? next) (-> :start (u/try-call value)))

        (and (range? el)
             (range-contains-some el value x)
             (range-contains-some el value (+ x step)))
        (+ x step)

        :else
        (recur (cons next rest))))))


(defn get-prev-unit-value [ps value x]
  (loop [[prev el & rest] (cons nil ps)]
    (let [{:keys [start step]} (if (range? el) (concretize-range el value) {})]
      (cond
        (nil? el)
        nil

        (or (= x el) (= x start))
        (cond-> prev (range? prev) (-> :end (u/try-call value)))

        (and (range? el)
             (range-contains-some el value x)
             (range-contains-some el value (- x step)))
        (- x step)

        :else
        (recur (cons el rest))))))


(defn get-first-el [ps value]
  (let [start (first ps)]
    (if (range? start)
      (u/try-call (:start start) value)
      start)))


(defn get-min-value [value unit]
  (get-first-el (unit-definition value unit) value))


(defn get-last-el [ps value]
  (let [end (last ps)]
    (if (range? end)
      (u/try-call (:end end) value)
      end)))


(defn get-max-value [value unit]
  (get-last-el (unit-definition value unit) value))


(defn sequence->vector [s value]
  (take-while some? (iterate (partial get-next-unit-value s value) (get-first-el s value))))


(defn ensure-unit [ps value unit-value]
  (cond-> unit-value
    (and (not (sequence-contains-some ps value unit-value)) ;; TODO: what if step changes?
         (number? unit-value)) ;; TODO: make this work for non-number elements
    (-> (max (get-first-el ps value))
        (min (get-last-el ps value)))))


(defn ensure-less-significant-units [value & [unit]]
  (->> (cond->> (->> value definition reverse)
         (some? unit)
         (drop-while (comp not #{unit} key)))
       rest
       (reduce (fn [v [u ps]]
                 (cond-> v
                   (contains? v u)
                   (update u (partial ensure-unit ps value))))
               value)))


(defn inc-unit [unit {unit-value unit, :or {unit-value (get-min-value value unit)}, :as value}]
  (or (some->> unit-value
               (get-next-unit-value (unit-definition value unit) value)
               (assoc value unit))
      (inc-unit (get-next-unit value unit)
                (assoc value unit (get-min-value value unit)))))


(defn dec-unit [unit {unit-value unit, :or {unit-value (get-min-value value unit)} :as value}]
  (or (some->> unit-value
               (get-prev-unit-value (unit-definition value unit) value)
               (assoc value unit))
      (as-> value $
        (dissoc $ unit)
        (dec-unit (get-next-unit $ unit) $)
        (assoc $ unit (get-max-value $ unit)))))


(def add-optimization-treshold 1)


(defn try-use-add-optimization? [x]
  (not (<= (- add-optimization-treshold)
           x
           add-optimization-treshold)))


(defn add-to-unit' [unit value x] ;; TODO: handle unnormalized values
  (cond
    (and (try-use-add-optimization? x)
         (static-sequence? (unit-definition value unit)))
    (let [s            (unit-definition value unit)
          idx          (if-let [v (get value unit)]
                         (index-in-sequence s value v)
                         (sequence-first-index s value))
          sum          (+ idx x)
          modulo       (sequence-length s value)
          result-idx   (cond-> sum (u/finite? modulo) (mod modulo))
          carry-delta  (if (u/infinite? modulo) 0 (u/floor (/ sum modulo)))
          result       (sequence-nth s value result-idx)
          result-value (assoc value unit result)]
      (if (zero? carry-delta)
        result-value
        (recur (get-next-unit value unit)
               result-value
               carry-delta)))

    (neg? x)
    (u/n-times (- x) (partial dec-unit unit) value)

    :else
    (u/n-times x (partial inc-unit unit) value)))


(defn add-to-unit [unit value x]
  (ensure-less-significant-units (add-to-unit' unit value x) unit))


(defn subtract-from-unit [unit value x]
  (add-to-unit unit value (- x)))


;;;;;;;; delta ;;;;;;;;
(defn strip-zeros [delta]
  (let [ks (->> (definition delta)
                keys
                (filter (partial contains? delta)))
        stripped (reduce (fn [acc k]
                           (cond-> acc
                             (zero? (get acc k))
                             (dissoc k)))
                         delta
                         ks)
        most-significant-unit (last ks) ;;TODO: maybe this should be least significant unit?
        most-significant-unit-value (get delta most-significant-unit 0)]
    (if (or (empty? stripped)
            (not (zero? most-significant-unit-value)))
      (assoc stripped most-significant-unit most-significant-unit-value)
      (dissoc stripped most-significant-unit))))


(defn invert [x]
  {:pre [(delta? x)]}
  (reduce-kv (fn [acc k _] (cond-> acc (contains? acc k) (update k -)))
             x
             (definition x)))


(defn assoc-delta [value delta]
  (assoc value (get-delta-type delta) delta))


(defn value->delta [value & [delta-meta]]
  (some->> value
           definition
           reverse
           (reduce
            (fn [acc [k ps]]
              (if-let [v (get value k)]
                (assoc acc k (or (index-in-sequence ps value v)
                                 (- v (get-min-value value k))))
                acc))
            (with-meta {} (make-delta-type value delta-meta)))))


(defn ensure-delta [delta]
  (if (delta? delta)
    delta
    (value->delta delta)))


(defn apply-delta [value delta]
  (if-let [delta (ensure-delta delta)]
    (-> (reduce (fn [acc [k _]]
                  (let [d (get delta k 0)]
                    (if (zero? d) acc (add-to-unit k acc d))))
                value
                (reverse (definition (ensure-delta delta))))
        (assoc-delta delta))
    value))


(defn get-applied-deltas [value]
  (->> value
       (remove (comp (partial contains? (-> value definition keys set)) key))
       (map (fn [[k v]] (with-meta v {(first (get-type value)) k})))))


(defn remove-deltas [value]
  (->> (get-applied-deltas value)
       (map invert)
       (reduce (fn [v d]
                 (let [delta-key (get-delta-type d)]
                   (-> v
                       (dissoc delta-key)
                       (apply-delta d)
                       (dissoc delta-key))))
               value)))


(defn drop-deltas [value]
  (->> (get-applied-deltas value)
       (map get-delta-type)
       (apply dissoc value)))


(defn assoc-deltas [value deltas]
  (reduce assoc-delta value deltas))


(defn apply-deltas [value deltas]
  (reduce apply-delta value (remove empty? deltas)))


(defn to-deltas [value new-deltas]
  (let [current-deltas (get-applied-deltas value)
        has-deltas?    (seq current-deltas)
        new-deltas?    (seq new-deltas)]
    (cond
      (= (apply merge new-deltas) (apply merge current-deltas))
      value

      (and has-deltas? new-deltas?) (-> (remove-deltas value) (apply-deltas new-deltas))
      new-deltas?                   (assoc-deltas value new-deltas)
      has-deltas?                   (drop-deltas value)
      :else                         value)))


(defn to-delta [value delta]
  (to-deltas value [delta]))


(defn try-strip-zeros [x]
  (cond-> x (delta? x) strip-zeros))


(defn process-binary-op-args-deltas
  "If args are deltas, then zeros are stripped,
   otherwhise x's delta applied to y"
  [x y]
  {:pre [(= (get-type x) (get-type y))]}
  [(try-strip-zeros x)
   (-> (try-strip-zeros y)
       (to-deltas (get-applied-deltas x)))])


;;;;;;;; cmp ;;;;;;;;
;; TODO: add zero?


(defn sequence-cmp [ps value x y]
  (cond
    (= x y) 0
    (nil? x) -1
    (nil? y) 1
    (= x (sequence-contains-some ps value x y)) -1
    :else 1))


(defn cmp [x y]
  (let [[x' y'] (process-binary-op-args-deltas x y)]
    (or (->> (definition x')
             reverse
             (map (fn [[unit processed-sequence]] (sequence-cmp processed-sequence x' (get x' unit) (get y' unit))))
             (drop-while zero?)
             first)
        0)))


(defn eq?
  ([_]          true)
  ([x y]        (zero? (cmp x y)))
  ([x y & more] (apply u/apply-binary-pred eq? x y more)))


(def not-eq? (complement eq?))


(defn lt?
  ([_]          true)
  ([x y]        (neg? (cmp x y)))
  ([x y & more] (apply u/apply-binary-pred lt? x y more)))


(defn gt?
  ([_]          true)
  ([x y]        (pos? (cmp x y)))
  ([x y & more] (apply u/apply-binary-pred gt? x y more)))


(defn lte?
  ([_]          true)
  ([x y]        (>= 0 (cmp x y)))
  ([x y & more] (apply u/apply-binary-pred lte? x y more)))


(defn gte?
  ([_]          true)
  ([x y]        (<= 0 (cmp x y)))
  ([x y & more] (apply u/apply-binary-pred gte? x y more)))


;;;;;;;; arithmetic ;;;;;;;;
(defn plus
  "  a   + delta =   a
   delta +   a   =   a
   value + value = error"
  ([] {})
  ([x] x)
  ([x y] {:pre [(some delta? [x y]) (= (get-main-type x) (get-main-type y))]}
   (let [[a b]  (if (delta? y) [x y] [y x])
         result (reduce (fn [a' [k _]]
                          (let [v (get b k 0)]
                            (if (zero? v)
                              a'
                              (add-to-unit k a' v))))
                        a
                        (reverse (definition b)))]
     (cond-> result (delta? result) strip-zeros)))
  ([x y & more] (reduce plus (plus x y) more)))


(defn subtract-delta [x delta]
  (plus x (invert delta)))


(defn difference
  "Difference between two values"
  [x y]
  (->> (cond-> (process-binary-op-args-deltas x y)
         (lt? x y) reverse)
       (map value->delta)
       (apply subtract-delta)))


(defn minus
  "  a   -   a   = delta
   value - delta = value
   delta - value = error"
  ([x] (invert x))
  ([x y]
   {:pre [(or (value? x) (delta? y))]}
   (if (delta? y)
     (subtract-delta x y)
     (cond-> (difference x y)
       (gt? y x) invert)))
  ([x y & more] (reduce minus (minus x y) more)))


(defn normalize [value]
  (let [is-value? (value? value)
        default   (into value
                        (comp (filter (comp (partial contains? value) key))
                           (map (juxt key #(-> % val (sequence-nth value 0)))))
                        (reverse (definition value)))]
    (cond-> (plus default (cond-> value is-value? (-> drop-deltas value->delta)))
      is-value? (assoc-deltas (get-applied-deltas value)))))
