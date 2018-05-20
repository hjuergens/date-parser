(#_  "
=> (use 'date-rule-instaparse.experimental-test :reload)
=> (ns date-rule-instaparse.experimental-test)
=> (run-tests)
")
(ns date-rule-instaparse.experimental-test
  (:require [clojure.test :refer :all]
            [date-rule-instaparse.experimental :refer :all])
  (:import [java.time Year Period LocalDate]
           java.util.Date)
  (:use java-time))


(deftest adjustIntoThirdWednesdayInMonth-test
  (testing
   (is
     (=
      (adjustIntoThirdWednesdayInMonth (local-date 2018 3 28))
      (local-date 2018 3 21)))))

(deftest adjustIntoNextThirdWednesdayInMonth-test
  (testing "adjustIntoNextThirdWednesdayInMonth"
   (is
    (=
     (adjustIntoNextThirdWednesdayInMonth (local-date 2018 3 20))
     (local-date 2018 3 21)))
   (is
    (=
     (adjustIntoNextThirdWednesdayInMonth (local-date 2018 3 21))
     (local-date 2018 4 18)))
   (is
    (=
     (adjustIntoNextThirdWednesdayInMonth (local-date 2018 3 22))
     (local-date 2018 4 18))))
  (testing "stream"
    (is
     (=
      (list (local-date 2018 11 21) (local-date 2018 12 19) (local-date 2019 1 16))
      (take 3 (clojure.core/iterate adjustIntoNextThirdWednesdayInMonth (local-date 2018 11 21)))))))
