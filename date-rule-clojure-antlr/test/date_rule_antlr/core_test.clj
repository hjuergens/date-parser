(ns date-rule-antlr.core-test
  (:require [clojure.test :refer :all]
            ;[clojure.test.check.generators :as gen]
            [date-rule-antlr.core :refer :all]
            [clojure.string :as str])
  (:import [java.time Year Period LocalDate LocalDateTime DayOfWeek Month]
           [java.time.temporal Temporal TemporalAdjusters TemporalAdjuster]))

#_(
(require '[clojure.test :refer [run-tests]])
(require 'date-rule-antlr.core-test :reload)
(run-tests 'date-rule-antlr.core-test)
)

(deftest aaa-test
  (testing "parse 'aAAaA a'"
           (is  (= (list :aaa "aAAaA" "a") (aaa "aAAaA a"))))
  (testing "parse '[1,2,,3,]'"
           (is (thrown? clj_antlr.ParseError (aaa "[1,2,,3,]")))))

(deftest wednesday-test
  (testing "nextWednesday"
           (def ^Temporal d (LocalDateTime/of 2016 12 3 15 18 22 65))
           (is  (= (LocalDateTime/of 2016 12 7 15 18 22 65) (.adjustInto nextWednesday d)))
           (is (=  (LocalDateTime/of 2016 12 21 15 18 22 65) (nth (iterate #(.adjustInto nextWednesday %) d) 3)))))

(deftest nextDayOfWeekAdjuster-test
  (testing "nextDayOfWeekAdjuster"
           (let [^Temporal d (LocalDateTime/of 2016 12 3 12 18 22 65)]
             (is (= (LocalDateTime/of 2016 12 28 12 18 22 65) (.adjustInto (nextDayOfWeekAdjuster DayOfWeek/WEDNESDAY 4) d)))))
  (testing "" (let [^Temporal d (LocalDateTime/of 2018 6 10 12 18 22 65)]
                (is (= (LocalDateTime/of 2018 6 17 12 18 22 65) (.adjustInto (nextDayOfWeekAdjuster DayOfWeek/SUNDAY 1) d))))))

#_(deftest loop-test
   (testing "loop"
            (is (= (LocalDateTime/of 2016 12 21 12 18 22 65)  (loop [c fn-coll t anyDate] (let [[f & rest] c] (if (empty? c) t (recur rest (f t)))))))))


(deftest adjust-day-of-week-m-test
  (testing "next wednesday"
           (let [^Temporal anyDate (LocalDateTime/of 2018 07 07 10 56 22)]
             (is (= (LocalDateTime/of 2018 07 11 10 56 22)
                    ((adjust-day-of-week-m TemporalAdjusters/next DayOfWeek/WEDNESDAY) anyDate)))))
  (testing "previous or same saturday"
           (let [^Temporal anyDate (LocalDateTime/of 2018 07 07 10 56 22)]
             (is (= (LocalDateTime/of 2018 07 07 10 56 22)
                    ((adjust-day-of-week-m TemporalAdjusters/previousOrSame DayOfWeek/SATURDAY) anyDate))))))

(deftest adjust-day-of-week-expr-fn-test
  (testing "adjust-day-of-week-expr-fn"
             (is (instance? clojure.lang.PersistentList
                      (adjust-day-of-week-expr-fn 'TemporalAdjusters/next 'DayOfWeek/WEDNESDAY))))
  (testing "next wednesday"
           (let [^Temporal anyDate (LocalDateTime/of 2018 07 07 10 56 22)]
             (is (= (LocalDateTime/of 2018 07 11 10 56 22)
                    (binding [*ns* *ns*] (in-ns 'date-rule-antlr.core)
                      ((eval (adjust-day-of-week-expr-fn 'TemporalAdjusters/next 'DayOfWeek/WEDNESDAY)) anyDate))))))
  (testing "previous or same saturday"
           (let [^Temporal anyDate (LocalDateTime/of 2018 07 07 10 56 22)]
             (is (= (LocalDateTime/of 2018 07 07 10 56 22)
                    (binding [*ns* *ns*] (in-ns 'date-rule-antlr.core)
                      ((eval (adjust-day-of-week-expr-fn 'TemporalAdjusters/previousOrSame 'DayOfWeek/SATURDAY)) anyDate)))))))

(deftest loop-adjust-day-of-week-expr-fn-test
   (testing "dow-adjusters-prs"
            (let [rule "<<<=sunday"
                  result (parse-rule rule)
                  dow-adjusters-prs (let [[x [u & direction] [d dayOfWeek]] result]
                                     (reverse (map list (map dowadjuster-string-keys direction)
                                               (repeat (dow-string-keys dayOfWeek)))))]
              (is (=
                   '(TemporalAdjusters/previousOrSame DayOfWeek/SUNDAY)
                   (first dow-adjusters-prs)))
              (is (=
                   "((TemporalAdjusters/previousOrSame DayOfWeek/SUNDAY) (TemporalAdjusters/previous DayOfWeek/SUNDAY) (TemporalAdjusters/previous DayOfWeek/SUNDAY))"
                   (str dow-adjusters-prs)))))
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
                                      (map list (map dowadjuster-string-keys direction)
                                       (repeat (dow-string-keys dayOfWeek))))
                  fn-coll (map #(eval (apply adjust-day-of-week-expr-fn %)) dow-adjusters-prs)
                  ^Temporal anyDate (LocalDateTime/of 2018 7 7 12 18 22)]
              (is (= (LocalDateTime/of 2018 6 17 12 18 22)
                     (binding [*ns* *ns*] (in-ns 'date-rule-antlr.core)
                       (loop [c (reduce conj '() fn-coll) t anyDate]
                         (let [[f & rest] c]
                           (if (empty? c) t (recur rest (f t))))))))))

  (testing "2.) <<<=sunday"
           (defn fn-coll-fn
             [dow-adjusters-prs]
             (map #(apply adjust-day-of-week-expr-fn %) dow-adjusters-prs))
           (defn loop-adjust-day-of-week-expr-fn
             [fn-coll anyDate](binding [*ns* (in-ns 'date-rule-antlr.core)]
                               (loop [c (reduce conj '() fn-coll) t anyDate] (let [[f & rest] c] (if (empty? c) t (recur rest (f t)))))))
           (let [rule "<<<=sunday"
                 fn-coll (fn-coll-fn (parse-to-adjuster-3 rule))
                 ^Temporal anyDate (LocalDateTime/of 2018 7 7 12 18 22)]
             (is (= (LocalDateTime/of 2018 6 17 12 18 22)
                    (loop-adjust-day-of-week-expr-fn (map eval fn-coll) anyDate)))))
  #_(testing "beforeOrSame sunday"
           (let [rule "<=sunday"
                 fn  (binding [*ns* (in-ns 'date-rule-antlr.core)]
                                    (map eval (map #(apply adjust-day-of-week-expr-fn-2 %) (parse-to-adjuster-3 rule))))]
             (is (= (LocalDate/of 2018 7 1)
                    (fn (LocalDate/of 2018 7 7))))
             (is (= (LocalDate/of 2018 7 8)
                    (fn (LocalDate/of 2018 7 8))))
             (is (= (LocalDate/of 2018 7 8)
                    (fn (LocalDate/of 2018 7 9))))))
  #_(testing "next monday"
           (let [rule ">monday"
                 fn (binding [*ns* (in-ns 'date-rule-antlr.core)]
                                    (map eval (map #(apply adjust-day-of-week-expr-fn %) (parse-to-adjuster-3 rule))))]
             (is (= (LocalDate/of 2018 7 9)
                    (fn (LocalDate/of 2018 7 8))))
             (is (= (LocalDate/of 2018 7 16)
                    (fn (LocalDate/of 2018 7 9))))
             (is (= (LocalDate/of 2018 7 16)
                    (fn (LocalDate/of 2018 7 10)))))))

(deftest rule-to-fm-test
  (testing "raw:next monday"
         (let [rule ">monday"
               fn (eval (rule-to-fm rule))]
           (is (= (LocalDate/of 2018 7 9)
                  (fn (LocalDate/of 2018 7 8))))
           (is (= (LocalDate/of 2018 7 16)
                  (fn (LocalDate/of 2018 7 9))))
           (is (= (LocalDate/of 2018 7 16)
                  (fn (LocalDate/of 2018 7 10))))))
  (testing "<<=sunday"
    (let [adjuster-fn (rule-to-fn "<<=sunday")]
      (is (= (LocalDate/of 2018 8 12) (adjuster-fn (LocalDate/of 2018 8 19)))))))

(deftest temporal-adjuster-fn-test
  (testing "simple"
    (let [ta (temporal-adjuster-fn "<<=sunday")]
    (binding [*ns* (in-ns 'date-rule-antlr.core)]
    (is (instance? TemporalAdjuster ta))
    (is (= (LocalDate/of 2018 8 12) (.adjustInto ta (LocalDate/of 2018 8 19))))
    (is (= (LocalDate/of 2018 8 12) (.with (LocalDate/of 2018 8 19) ta)))))))

(deftest loop-fm-fn-tests
  (testing "translate"
    (is (not-empty (loop-fm-fn (parse-to-adjuster-3 ">=>sunday")))))
  (testing "evaluated is function"
      (let [fm (loop-fm-fn (parse-to-adjuster-3 ">=>sunday"))]
      (is (function? (eval fm)))))
  (testing "apply function"
      (let [fn (eval (loop-fm-fn (parse-to-adjuster-3 ">=>sunday")))]
      (is (= (LocalDate/of 2018 9 30)
        (fn (LocalDate/of 2018 9 23))))
      (is (= (LocalDate/of 2018 10 7)
        (fn (LocalDate/of 2018 9 24))))))
  (testing "apply another function"
      (let [fn (eval (loop-fm-fn (parse-to-adjuster-3 "<<<<<<<<friday")))]
      (is (= (LocalDate/of 2018 8 3)
        (fn (LocalDate/of 2018 9 23))))))
  (testing "apply another function"
      (let [fn (eval (loop-fm-fn (parse-to-adjuster-4 ">december")))]
      (is (= (LocalDate/of 2018 12 23)
        (fn (LocalDate/of 2018 9 23))))))
  #_(testing "apply another function"
      (let [fn (eval (loop-fm-fn (parse-to-adjuster-4 ">november")))]
      (is (thrown? java.lang.NullPointerException (fn (LocalDate/of 2018 9 23)))))))

(deftest nextMonthAdjuster-tests
  (testing "is function"
    (is (function? nextMonthAdjuster)))
  (testing "same year"
      (let [d  (LocalDate/of 2018 3 31)]
      (is (= (LocalDate/of 2018 6 30) (.adjustInto (nextMonthAdjuster Month/JUNE) d)))))
  (testing "same year/month"
      (let [d  (LocalDate/of 2018 6 30)]
      (is (= (LocalDate/of 2019 6 30) (.adjustInto (nextMonthAdjuster Month/JUNE) d)))))
  (testing "next year"
    (let [d  (LocalDate/of 2018 10 31)]
    (is (= (LocalDate/of 2019 6 30) (.adjustInto (nextMonthAdjuster Month/JUNE) d))))))

(deftest nextOrSameMonthAdjuster-tests
  (testing "is function"
    (is (function? nextOrSameMonthAdjuster)))
  (testing "same year"
      (let [d  (LocalDate/of 2018 3 31)]
      (is (= (LocalDate/of 2018 6 30) (.adjustInto (nextOrSameMonthAdjuster Month/JUNE) d)))))
  (testing "same year/month"
      (let [d  (LocalDate/of 2018 6 30)]
      (is (= (LocalDate/of 2018 6 30) (.adjustInto (nextOrSameMonthAdjuster Month/JUNE) d)))))
  (testing "next year"
    (let [d  (LocalDate/of 2018 10 31)]
    (is (= (LocalDate/of 2019 6 30) (.adjustInto (nextOrSameMonthAdjuster Month/JUNE) d))))))

(deftest previousMonthAdjuster-tests
  (testing "is function"
    (is (function? previousMonthAdjuster)))
  (testing "previous year"
      (let [d  (LocalDate/of 2018 3 31)]
      (is (= (LocalDate/of 2017 6 30) (.adjustInto (previousMonthAdjuster Month/JUNE) d)))))
  (testing "previous year/same month"
      (let [d  (LocalDate/of 2018 6 30)]
      (is (= (LocalDate/of 2017 6 30) (.adjustInto (previousMonthAdjuster Month/JUNE) d)))))
  (testing "same year"
    (let [d  (LocalDate/of 2018 10 31)]
    (is (= (LocalDate/of 2018 6 30) (.adjustInto (previousMonthAdjuster Month/JUNE) d))))))

(deftest previousOrSameMonthAdjuster-tests
  (testing "is function"
    (is (function? previousOrSameMonthAdjuster)))
  (testing "previous year"
      (let [d  (LocalDate/of 2018 3 31)]
      (is (= (LocalDate/of 2017 6 30) (.adjustInto (previousOrSameMonthAdjuster Month/JUNE) d)))))
  (testing "previous year/same month"
      (let [d  (LocalDate/of 2018 6 30)]
      (is (= (LocalDate/of 2018 6 30) (.adjustInto (previousOrSameMonthAdjuster Month/JUNE) d)))))
  (testing "same year"
    (let [d  (LocalDate/of 2018 10 31)]
    (is (= (LocalDate/of 2018 6 30) (.adjustInto (previousOrSameMonthAdjuster Month/JUNE) d))))))
