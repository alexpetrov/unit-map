(ns unit-map.core-test
  (:require [unit-map.core :as sut]
            [clojure.test :as t]
            [matcho.core :as matcho]))


(defn good-sys? [defsys-fn]
  (t/is
    (= :good
       (try (defsys-fn)
            :good
            (catch AssertionError e
              :bad)))))

(defn bad-sys? [defsys-fn]
  (t/is
    (= :bad
       (try (defsys-fn)
            :good
            (catch AssertionError e
              :bad)))))


(t/deftest defseq-defsys
  (sut/defseq :a #unit-map/seq[0 1 -> :b])
  (sut/defseq :b #unit-map/seq[0 1 -> :c])
  (sut/defseq :c #unit-map/seq[0 1 -> :d])
  (sut/defseq :d #unit-map/seq[0 1])

  (sut/defseq :b2 #unit-map/seq[:b <=> -2 -1 0 -> :c2])
  (sut/defseq :c2 #unit-map/seq[-2 -1 0 -> :d])

  (sut/defseq :b3 #unit-map/seq[:b2 <=> 2 1 0 -> :c3])
  (sut/defseq :c3 #unit-map/seq[2 1 .. ##Inf])

  (sut/defseq :b4 #unit-map/seq[:b <=> 2 1 0 -> :c4])
  (sut/defseq :c4 #unit-map/seq[2 1 .. ##Inf])

  (sut/defseq :b5 #unit-map/seq[2 1 0 -> :c5])
  (sut/defseq :c5 #unit-map/seq[2 1 .. ##Inf])

  (sut/defseq :b6 #unit-map/seq[:b <=> 2 1 0 -> :c6])
  (sut/defseq :c6 #unit-map/seq[:c <=> 2 1 0 -> :d])

  (t/testing "valid systems"
    (t/is (sut/sys-continuous? [:a :b :c :d]))
    (t/is (sut/sys-continuous? [:a :b2 :c2 :d]))
    (t/is (sut/sys-continuous? [:a :b2 :c2]))
    (t/is (sut/sys-continuous? [:a :b3 :c3]))
    (t/is (sut/sys-continuous? [:a :b4 :c4]))
    (t/is (sut/sys-continuous? [:b5 :c5]))
    (t/is (sut/sys-continuous? [:a :b6 :c6 :d]))
    (t/is (sut/sys-continuous? [:a :b6 :c6]))
    (t/is (sut/sys-continuous? [:a :b :c6 :d])))

  (t/testing "invalid systems"
    (t/is (not (sut/sys-continuous? [:d :c :b :a])))
    (t/is (not (sut/sys-continuous? [:a :b2 :c])))
    (t/is (not (sut/sys-continuous? [:a :b3 :c3 :d])))))


(do ;;NOTE: seqs
  (def si-prefixes
    {:Y  24
     :Z  21
     :E  18
     :P  15
     :T  12
     :G  9
     :M  6
     :k  3
     :h  2
     :da 1
     :_  0
     :d  -1
     :c  -2
     :m  -3
     :μ  -6
     :n  -9
     :p  -12
     :f  -15
     :a  -18
     :z  -21
     :y  -24})


  (defn leap-year? [{:keys [year]}]
    (and (zero? (rem year 4))
         (or (pos? (rem year 100))
             (zero? (rem year 400)))))


  (defn days-in-month [{:as date, :keys [month]}]
    (condp contains? month
      #{:jan :mar :may :jul :aug :oct :dec} 31
      #{:apr :jun :sep :nov}                30
      #{:feb}                               (if (leap-year? date) 29 28)
      ##Inf))


  (defn weekday [{:keys [weekday]}]
    (condp contains? weekday
      #{:mon :tue :wed :thu :fri} :workday
      #{:sat :sun}                :weekend))


  (defn season [{:keys [month]}]
    (condp contains? month
      #{:dec :jan :feb} :winter
      #{:mar :apr :may} :spring
      #{:jun :jul :aug} :summer
      #{:sep :oct :nov} :autumn))

  (sut/defseq :ns   #unit-map/seq[0 1 .. 999999999 -> :sec])

  (sut/defseq :ns   #unit-map/seq[0 1 .. 999999 -> :ms])
  (sut/defseq :ms   #unit-map/seq[0 1 .. 999 -> :sec])
  (sut/defseq :sec  #unit-map/seq[0 1 .. 59 -> :min])
  (sut/defseq :min  #unit-map/seq[0 1 .. 59 -> :hour])
  (sut/defseq :hour #unit-map/seq[0 1 .. 23 -> :day])

  (sut/defseq :am-pm/hour   #unit-map/seq[:hour <=> 12 1 2 .. 11 -> :am-pm/period])
  (sut/defseq :am-pm/period #unit-map/seq[:am :pm -> :day])

  (sut/defseq :day   #unit-map/seq[1 2 .. days-in-month -> :month])
  (sut/defseq :month #unit-map/seq[:jan :feb  :mar :apr :may  :jun :jul :aug  :sep :oct :nov  :dec -> :year])
  (sut/defseq :year  #unit-map/seq[##-Inf .. -2 -1 1 2 .. ##Inf])

  (sut/defseq :weekday  #unit-map/seq[:day <=> :mon :tue :wed :thu :fri :sat :sun -> :week])
  (sut/defseq :week     #unit-map/seq[1 2 .. 52])
  (sut/defseq :weekpart #unit-map/seq[:weekday <=> weekday])
  (sut/defseq :season   #unit-map/seq[:month <=> season])

  (sut/defseq :mil  #unit-map/seq[0 1 .. 999  -> :inch])
  (sut/defseq :inch #unit-map/seq[0 1 .. 11   -> :foot])
  (sut/defseq :foot #unit-map/seq[0 1 .. 5279 -> :mile])
  (sut/defseq :mile #unit-map/seq[0 1 .. ##Inf])

  (sut/defseq :mm #unit-map/seq[0 1 .. 9   -> :cm])
  (sut/defseq :cm #unit-map/seq[0 1 .. 99  -> :m])
  (sut/defseq :m  #unit-map/seq[0 1 .. 999 -> :km])
  (sut/defseq :km #unit-map/seq[0 1 .. ##Inf]))

(do ;;NOTE: systems
  (sut/defsys imperial [:mil :inch :foot :mile])

  (sut/defsys metric   [:mm :cm :m :km])

  (sut/defsys ms-hour    [:ms :sec :min :hour])
  (sut/defsys ns-hour    [:ns :sec :min :hour])
  (sut/defsys ns-ms-hour [:ns :ms :sec :min :hour])

  (sut/defsys timestamp    [:ms])
  (sut/defsys ns-timestamp [:ns])

  (sut/defsys seconds [:ns :ms :sec])
  (sut/defsys ns-seconds [:ns :sec])

  (sut/defsys ms-day    [:ms :sec :min :hour :day])
  (sut/defsys ns-day    [:ns :sec :min :hour :day])
  (sut/defsys ns-ms-day [:ns :ms :sec :min :hour :day])

  (sut/defsys ms-day-am-pm    [:ms :sec :min :am-pm/hour :am-pm/period :day])
  (sut/defsys ns-day-am-pm    [:ns :sec :min :am-pm/hour :am-pm/period :day])
  (sut/defsys ns-ms-day-am-pm [:ns :ms :sec :min :am-pm/hour :am-pm/period :day])

  (sut/defsys date       [:day :month :year])
  (sut/defsys month-year [:month :year])

  (sut/defsys ms-year    [:ms :sec :min :hour :day :month :year])
  (sut/defsys ns-year    [:ns :sec :min :hour :day :month :year])
  (sut/defsys ns-ms-year [:ns :ms :sec :min :hour :day :month :year])

  (sut/defsys ms-year-am-pm    [:ms :sec :min :am-pm/hour :am-pm/period :day :month :year])
  (sut/defsys ns-year-am-pm    [:ns :sec :min :am-pm/hour :am-pm/period :day :month :year])
  (sut/defsys ns-ms-year-am-pm [:ns :ms :sec :min :am-pm/hour :am-pm/period :day :month :year]))


(t/deftest sys-detection
  (matcho/match
    (sut/guess-sys {:min 30, :hour 15})
    [ms-hour])

  (matcho/match
    (sut/guess-sys {:min 30, :am-pm/hour 3, :am-pm/period :pm})
    [ms-day-am-pm])

  (matcho/match
    (sut/guess-sys {:ns 1, :ms 1, :sec 1, :min 1, :hour 1, :day 1})
    [ns-ms-day])

  (matcho/match
    (sut/guess-sys {:ns 1, :ms 1, :sec 1, :min 1, :hour 25})
    [ns-ms-hour])

  (matcho/match
    (sut/guess-sys {:ns 1, :ms 1, :sec 1, :min 1501})
    [ns-ms-hour])

  (matcho/match
    (sut/guess-sys {:ns 1, :ms 1, :sec 90061})
    [seconds])

  (matcho/match
    (sut/guess-sys {:ns 1, :ms 90061001})
    [seconds])

  (matcho/match
    (sut/guess-sys {:ns 90061001000001})
    [ns-timestamp])


  (matcho/match
    (sut/guess-sys {:ns 1000001, :sec 1, :min 1, :hour 1, :day 1})
    [ns-day])

  (matcho/match
    (sut/guess-sys {:ns 1000001, :sec 1, :min 1, :hour 25})
    [ns-hour])

  (matcho/match
    (sut/guess-sys {:ns 1000001, :sec 1, :min 1501})
    [ns-hour])

  (matcho/match
    (sut/guess-sys {:ns 1000001, :sec 90061})
    [ns-seconds])

  (matcho/match
    (sut/guess-sys {:ns 90061001000001})
    [ns-timestamp])

  (matcho/match
    (sut/guess-sys {:ns 1, :sec 1, :min 1, :hour 1, :day 1 :delta {:ns 1}})
    [ns-day])


  (matcho/match
    (sut/guess-sys {:ms 1, :sec 1, :min 1, :hour 1, :day 1})
    [ms-day])

  (matcho/match
    (sut/guess-sys {:ms 1, :sec 1, :min 1, :hour 25})
    [ms-hour])

  (matcho/match
    (sut/guess-sys {:ms 1, :sec 1, :min 1501})
    [ms-hour])

  (matcho/match
    (sut/guess-sys {:ms 1, :sec 90061})
    [seconds])

  (matcho/match
    (sut/guess-sys {:ms 90061001})
    [timestamp])

  (matcho/match
    (sut/guess-sys {:ms 1, :sec 1, :min 1, :hour 1, :day 1 :delta {:ms 1}})
    [ms-day]))


#_(t/deftest parameter-sets
  (def v {:ns 1, :sec 1, :min 1, :am-pm/hour 1, :am-pm/period 1, :day 1})
  (def d {:delta {:hour 5}})

  (sut/guess-sys v)
  (sut/guess-sys d))