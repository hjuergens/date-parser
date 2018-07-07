(ns date-rule-antlr.core-test
  (:require [clojure.test :refer :all]
            ;[clojure.test.check.generators :as gen]
            [date-rule-antlr.core :refer :all])
  (:import [java.time Year Period LocalDate LocalDateTime DayOfWeek]
           [java.time.temporal Temporal TemporalAdjusters TemporalAdjuster]))

; (list 'repeat 2 (list 'TemporalAdjusters/next 'DayOfWeek/TUESDAY))
; (require '[clojure.test :refer [run-tests]])
; (require 'date-rule-antlr.core-test :reload)
; (run-tests 'date-rule-antlr.core-test)

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
             (is (= (LocalDateTime/of 2016 12 28 12 18 22 65) (.adjustInto (nextDayOfWeekAdjuster DayOfWeek/WEDNESDAY 4) d)))))
  (testing "" (let [^Temporal d (LocalDateTime/of 2018 6 10 12 18 22 65)]
                (is (= (LocalDateTime/of 2018 6 17 12 18 22 65) (.adjustInto (nextDayOfWeekAdjuster DayOfWeek/SUNDAY 1) d))))))

(deftest parse-to-adjuster-test
  (testing "parse-to-adjuster"
           (is (= (list 'nextDayOfWeekAdjuster 'DayOfWeek/WEDNESDAY 2) (parse-to-adjuster ">>wednesday")))
           (is (= (list 'nextDayOfWeekAdjuster 'DayOfWeek/SATURDAY 4) (parse-to-adjuster ">>>>saturday")))
           ;(is (= (list 'nextDayOfWeekAdjuster 'DayOfWeek/MONDAY 1) (parse-to-adjuster ">=monday")))
           (is (= (list 'nextDayOfWeekAdjuster 'DayOfWeek/THURSDAY -2) (parse-to-adjuster "<<thursday")))))

(deftest dow-adjusters-prs-test
  (testing "parse-to-adjuster"
           (is (= (list '(TemporalAdjusters/nextOrSame DayOfWeek/WEDNESDAY) '(TemporalAdjusters/next DayOfWeek/WEDNESDAY) '(TemporalAdjusters/next DayOfWeek/WEDNESDAY))
                  dow-adjusters-prs))))

#_(deftest loop-test
  (testing "loop"
           (is (= (LocalDateTime/of 2016 12 21 12 18 22 65)  (loop [c fn-coll t anyDate] (let [[f & rest] c] (if (empty? c) t (recur rest (f t)))))))))

(deftest parse-to-adjuster-2-test
  (testing "parse-to-adjuster-2"
           (is (= (list (list 'repeat 2 '(TemporalAdjusters/next DayOfWeek/TUESDAY)))
                  (parse-to-adjuster-2 ">>tuesday")))
           (is (= (list (list 'repeat 1 '(TemporalAdjusters/next DayOfWeek/SATURDAY)))
                  (parse-to-adjuster-2 ">saturday")))
           (is (= (list (list 'repeat 1 '(TemporalAdjusters/nextOrSame DayOfWeek/SATURDAY)))
                  (parse-to-adjuster-2 ">=saturday")))
           (is (= (list (list 'repeat 1 '(TemporalAdjusters/nextOrSame DayOfWeek/SATURDAY)) (list 'repeat 1 '(TemporalAdjusters/next DayOfWeek/SATURDAY)))
                  (parse-to-adjuster-2 ">>=saturday")))
           (is (= (list (list 'repeat 4 '(TemporalAdjusters/previous DayOfWeek/MONDAY)))
                  (parse-to-adjuster-2 "<<<<monday")))))

(deftest adjust-day-of-week-m-test
  (testing "next wednesday"
           (let [^Temporal anyDate (LocalDateTime/of 2018 07 07 10 56 22)]
             (is (= (LocalDateTime/of 2018 07 11 10 56 22)
                    ((adjust-day-of-week-m TemporalAdjusters/next DayOfWeek/WEDNESDAY) anyDate)))))
  (testing "previous or same saturday"
           (let [^Temporal anyDate (LocalDateTime/of 2018 07 07 10 56 22)]
             (is (= (LocalDateTime/of 2018 07 07 10 56 22)
                    ((adjust-day-of-week-m TemporalAdjusters/previousOrSame DayOfWeek/SATURDAY) anyDate))))))

(deftest adjust-day-of-week-fn-test
  (testing "next wednesday"
           (let [^Temporal anyDate (LocalDateTime/of 2018 07 07 10 56 22)]
             (is (= (LocalDateTime/of 2018 07 11 10 56 22)
                    (binding [*ns* *ns*] (in-ns 'date-rule-antlr.core)
                      ((eval (adjust-day-of-week-fn 'TemporalAdjusters/next 'DayOfWeek/WEDNESDAY)) anyDate))))))
  (testing "previous or same saturday"
           (let [^Temporal anyDate (LocalDateTime/of 2018 07 07 10 56 22)]
             (is (= (LocalDateTime/of 2018 07 07 10 56 22)
                    (binding [*ns* *ns*] (in-ns 'date-rule-antlr.core)
                      ((eval (adjust-day-of-week-fn 'TemporalAdjusters/previousOrSame 'DayOfWeek/SATURDAY)) anyDate)))))))


(deftest loop-adjust-day-of-week-fn-test
   (testing "6j"
            (let [rule "<<<=sunday"
                  result (parse-rule rule)
                  dow-adjusters-prs (let [[x [u & direction] [d dayOfWeek]] result] (reverse (map list (map dowadjuster-string-keys direction) (repeat (dow-string-keys dayOfWeek)))))]
              (is (= '(TemporalAdjusters/previousOrSame DayOfWeek/SUNDAY) (first dow-adjusters-prs)))))
   (testing "parse-to-adjuster-3 \"<<<=sunday\""
            (let [rule "<<<=sunday"
                  result (parse-rule rule)]
              (is (= '((TemporalAdjusters/previous DayOfWeek/SUNDAY)
                       (TemporalAdjusters/previous DayOfWeek/SUNDAY)
                       (TemporalAdjusters/previousOrSame DayOfWeek/SUNDAY))
                     (parse-to-adjuster-3 rule)))))
   (testing "1.) <<<=sunday"
            (let [rule "<<<=sunday"
                  result (parse-rule rule)
                  dow-adjusters-prs (let [[x [u & direction] [d dayOfWeek]] result]
                                      (map list (map dowadjuster-string-keys direction) (repeat (dow-string-keys dayOfWeek))))
                  fn-coll (map #(eval (apply adjust-day-of-week-fn %)) dow-adjusters-prs)
                  ^Temporal anyDate (LocalDateTime/of 2018 07 07 12 18 22)]
              (is (= (LocalDateTime/of 2018 06 17 12 18 22)
                     (binding [*ns* *ns*] (in-ns 'date-rule-antlr.core)
                       (loop [c (reduce conj '() fn-coll) t anyDate]
                         (let [[f & rest] c]
                           (if (empty? c) t (recur rest (f t))))))))))
  (testing "2.) <<<=sunday"
           (defn fn-coll-fn
             [dow-adjusters-prs]
             (map #(apply adjust-day-of-week-fn %) dow-adjusters-prs))
           (defn loop-adjust-day-of-week-fn
             [fn-coll anyDate](binding [*ns* *ns*] (in-ns 'date-rule-antlr.core)
                 (loop [c (reduce conj '() fn-coll) t anyDate] (let [[f & rest] c] (if (empty? c) t (recur rest (f t)))))))
           (let [rule "<<<=sunday"
                 result (parse-rule rule)
                 fn-coll (fn-coll-fn (parse-to-adjuster-3 rule))
                 ^Temporal anyDate (LocalDateTime/of 2018 07 07 12 18 22)]
             (is (= (LocalDateTime/of 2018 06 17 12 18 22)
                    (loop-adjust-day-of-week-fn (map eval fn-coll) anyDate))))))

