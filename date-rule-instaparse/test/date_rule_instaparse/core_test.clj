(ns date-rule-instaparse.core-test
  (:require [clojure.test :refer :all]
            [date-rule-instaparse.core :refer :all])
  (:import [java.time Year Period LocalDateTime]
           java.util.Date))

(deftest parseYear-test
  (testing "parse '1999'"
           (is (= (parseYear "1999") (Year/parse "1999"))))
         (testing "parse '1783'"
           (is (= (parseYear "1783") (Year/parse "1783"))))
         (testing "parse '15'"
                  (is (thrown? java.time.format.DateTimeParseException (Year/parse "15")))))

(deftest threedays-adder-test
         (testing
          (is (=
               (threedays-adder (LocalDateTime/parse "2018-03-28T10:18:06.689"))
               (java.time.LocalDateTime/parse "2018-03-31T10:18:06.689")))))
