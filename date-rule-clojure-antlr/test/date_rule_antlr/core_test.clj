(ns date-rule-antlr.core-test
  (:require [clojure.test :refer :all]
            ;[clojure.test.check.generators :as gen]
            [date-rule-antlr.core :refer :all])
  (:import [java.time LocalDateTime DayOfWeek]
           [java.time.temporal Temporal TemporalAdjusters TemporalAdjuster]))

(deftest aaa-test
  (testing "parse 'aAAaA a'"
           (is  (= (list :aaa "aAAaA" "a") (aaa "aAAaA a"))))
  (testing "parse '[1,2,,3,]'"
           (is (thrown? clj_antlr.ParseError (aaa "[1,2,,3,]")))))

(deftest wednesday-test
  (testing "nextWednesday"
           (def ^Temporal d (LocalDateTime/of 2016 12 3 15 18 22 65))
           (is  (= (LocalDateTime/of 2016 12 7 15 18 22 65) (.adjustInto nextWednesday d) ))
           (is (=  (LocalDateTime/of 2016 12 21 15 18 22 65) (nth (iterate #(.adjustInto nextWednesday %) d) 3) ))))

(deftest nextDayOfWeekAdjuster-test
  (testing "nextDayOfWeekAdjuster"
           (let [^Temporal d (LocalDateTime/of 2016 12 3 12 18 22 65)]
             (is (= (LocalDateTime/of 2016 12 28 12 18 22 65) (.adjustInto (nextDayOfWeekAdjuster DayOfWeek/WEDNESDAY 4) d))))
           )
  (testing "" (let [^Temporal d (LocalDateTime/of 2018 6 10 12 18 22 65)]
                (is (= (LocalDateTime/of 2018 6 17 12 18 22 65) (.adjustInto (nextDayOfWeekAdjuster DayOfWeek/SUNDAY 1) d))))))

(deftest parse-to-adjuster-test
  (testing "parse-to-adjuster"
           (is (= (list 'nextDayOfWeekAdjuster 'DayOfWeek/WEDNESDAY 2) (parse-to-adjuster ">>wednesday")))
           (is (= (list 'nextDayOfWeekAdjuster 'DayOfWeek/SATURDAY 4) (parse-to-adjuster ">>>>saturday")))
           (is (= (list 'nextDayOfWeekAdjuster 'DayOfWeek/MONDAY 1) (parse-to-adjuster ">=monday")))
           (is (= (list 'nextDayOfWeekAdjuster 'DayOfWeek/THURSDAY -2) (parse-to-adjuster "<<thursday")))))

