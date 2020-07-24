(ns chrono.new-ops-test
  (:require [chrono.new-ops :as sut]
            [matcho.core :as matcho]
            [clojure.test :as t]))

(t/deftest range-test
  (def base60   (:min  (sut/rules {})))
  (def months   (:month (sut/rules {})))
  (def years    (:year (sut/rules {})))
  (def am-hours (:hour (sut/rules ^:am-pm{})))

  (t/testing "process-sequence"
    (matcho/match (sut/process-sequence base60)
                  [{:start 0, :step 1, :end 59}])

    (matcho/match (sut/process-sequence months)
                  [:jan :feb :mar :apr :may :jun :jul :aug :sep :oct :nov :dec])

    (matcho/match (sut/process-sequence years)
                  [{:start ##-Inf, :step 1, :end -1}
                   {:start 1, :step 1, :end ##Inf}])

    (matcho/match (sut/process-sequence am-hours)
                  [12 {:start 1, :step 1, :end 11}]))

  (t/testing "sequence-contains?"
    (matcho/match (map (partial sut/sequence-contains? base60 nil)
                       [##-Inf -1 0 59 60 ##Inf])
                  [false false true true false false])
    (matcho/match (map (partial sut/sequence-contains? years nil)
                       [##-Inf -31337 -1 0 1 31337 ##Inf])
                  [true true true false true true true]))

  (t/testing "get-next-unit-value"
    (matcho/match (take-while some? (iterate (partial sut/get-next-unit-value base60 nil) 0))
                  (range 60))

    (matcho/match (take-while some? (iterate (partial sut/get-next-unit-value months nil) :jan))
                  [:jan :feb :mar :apr :may :jun :jul :aug :sep :oct :nov :dec])

    (matcho/match (take 51 (iterate (partial sut/get-next-unit-value years nil) 1970))
                  (range 1970 2021))

    (matcho/match (take-while some? (iterate (partial sut/get-next-unit-value am-hours nil) 12))
                  [12 1 2 3 4 5 6 7 8 9 10 11]))

  (t/testing "get-prev-unit-value"
    (matcho/match (take-while some? (iterate (partial sut/get-prev-unit-value base60 nil) 59))
                  (range 59 -1 -1))

    (matcho/match (take-while some? (iterate (partial sut/get-prev-unit-value months nil) :dec))
                  [:dec :nov :oct :sep :aug :jul :jun :may :apr :mar :feb :jan])

    (matcho/match (take 51 (iterate (partial sut/get-prev-unit-value years nil) 1970))
                  (range 1970 1920))

    (matcho/match (take-while some? (iterate (partial sut/get-prev-unit-value am-hours nil) 11))
                  [11 10 9 8 7 6 5 4 3 2 1 12]))

  (t/testing "get-next-unit"
    (let [value ^:am-pm{:hour 12, :period :am}]
      (matcho/match [{:hour 12, :period :am}
                     {:hour 1, :period :am}
                     {:hour 2, :period :am}
                     {:hour 3, :period :am}
                     {:hour 4, :period :am}
                     {:hour 5, :period :am}
                     {:hour 6, :period :am}
                     {:hour 7, :period :am}
                     {:hour 8, :period :am}
                     {:hour 9, :period :am}
                     {:hour 10, :period :am}
                     {:hour 11, :period :am}
                     {:hour 12, :period :pm}
                     {:hour 1, :period :pm}
                     {:hour 2, :period :pm}
                     {:hour 3, :period :pm}
                     {:hour 4, :period :pm}
                     {:hour 5, :period :pm}
                     {:hour 6, :period :pm}
                     {:hour 7, :period :pm}
                     {:hour 8, :period :pm}
                     {:hour 9, :period :pm}
                     {:hour 10, :period :pm}
                     {:hour 11, :period :pm}]
                    (take 24 (iterate (partial sut/inc-unit :hour) value))))

    (let [value ^:am-pm{:hour 11, :period :pm}]
      (matcho/match [{:hour 11, :period :pm}
                     {:hour 10, :period :pm}
                     {:hour 9, :period :pm}
                     {:hour 8, :period :pm}
                     {:hour 7, :period :pm}
                     {:hour 6, :period :pm}
                     {:hour 5, :period :pm}
                     {:hour 4, :period :pm}
                     {:hour 3, :period :pm}
                     {:hour 2, :period :pm}
                     {:hour 1, :period :pm}
                     {:hour 12, :period :pm}
                     {:hour 11, :period :am}
                     {:hour 10, :period :am}
                     {:hour 9, :period :am}
                     {:hour 8, :period :am}
                     {:hour 7, :period :am}
                     {:hour 6, :period :am}
                     {:hour 5, :period :am}
                     {:hour 4, :period :am}
                     {:hour 3, :period :am}
                     {:hour 2, :period :am}
                     {:hour 1, :period :am}
                     {:hour 12, :period :am}]
                    (take 24 (iterate (partial sut/dec-unit :hour) value))))))