(#_  "
=> (use 'date-rule-instaparse.core-test :reload)
=> (ns date-rule-instaparse.core-test)
=> (in-ns 'date-rule-instaparse.core-test)
=> (run-tests namespaces...)
")

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

;;(alias aliasname java.time.LocalDateTime/parse)

(deftest make-period-adder-test
  (testing "threedays-adder"
    (let [threedays-adder (make-period-adder (Period/parse "P3D"))]
    (is
      (=
        (threedays-adder (LocalDateTime/parse "2018-03-28T10:18:06.689"))
        (LocalDateTime/parse "2018-03-31T10:18:06.689")))))
  (testing "twomonth-adder"
    (let [twomonth-adder (make-period-adder (Period/parse "P3M"))]
      (is
        (=
          (LocalDateTime/parse "2018-06-28T10:18:06.689")
         (twomonth-adder (LocalDateTime/parse "2018-03-28T10:18:06.689")))))))

(with-test
 (def threedays-adder (make-period-adder (Period/parse "P3D")))
 (is
  (=
   (threedays-adder (LocalDateTime/parse "2018-03-28T10:18:06.689"))
   (LocalDateTime/parse "2018-03-31T10:18:06.689"))))

