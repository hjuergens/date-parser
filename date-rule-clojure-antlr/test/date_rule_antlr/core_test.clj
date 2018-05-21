(ns date-rule-antlr.core-test
  (:require [clojure.test :refer :all]
            ;[clojure.test.check.generators :as gen]
            [date-rule-antlr.core :refer :all]))

(deftest aaa-test
  (testing "parse 'aAAaA a'"
           (is  (= (list :aaa "aAAaA" "a") (aaa "aAAaA a"))))
  (testing "parse '[1,2,,3,]'"
           (is (thrown? clj_antlr.ParseError (aaa "[1,2,,3,]")))))
