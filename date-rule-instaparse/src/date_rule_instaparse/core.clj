(ns date-rule-instaparse.core
  (:require [instaparse.core :as insta])
  (:gen-class)
  (:import [java.time Year Period LocalDateTime DayOfWeek]
           java.util.Date
           [java.time.temporal Temporal TemporalAdjusters]))

(def as-and-bs
  (insta/parser
    "S = AB*
     AB = A B
     A = 'a'+
     B = 'b'+"))

(def arithmetic
  (insta/parser
    "expr = add-sub
     <add-sub> = mul-div | add | sub
     add = add-sub <'+'> mul-div
     sub = add-sub <'-'> mul-div
     <mul-div> = term | mul | div
     mul = mul-div <'*'> term
     div = mul-div <'/'> term
     <term> = number | <'('> add-sub <')'>
     number = #'[0-9]+'"))

(Year/parse "2003")

(def yearparser
  (insta/parser
    "year = #'[1-9]\\d{3}' | twodigityear
    twodigityear = #'[0-9]{2}'"))

(insta/transform
  {:year #(Year/parse %1)}
  (yearparser "1999"))

(defn parseYear
  "Parse string to year."
  [^String s]
  (insta/transform
    {:year #(Year/parse %1)}
    ((insta/parser "year = #'[1-9]\\d{3}'") s)))

(defn period
  "shift time date"
  [^String s]
  s)

(defn make-period-adder [period]
  (let [p period]
    (fn [ldt] (.plus ldt p))))

(def threedays-adder (make-period-adder (Period/parse "P3D")))

(def twomonth-adder (make-period-adder (Period/parse "P2M")))

(threedays-adder (LocalDateTime/parse "2018-03-28T10:18:06.689"))



(comment"
@Test
public void testThirdWednesday() throws Exception {
                                                    DateTimeAdjuster adjuster = SchedulerFactory.parseSchedule(">>>wednesday").next();

                                                    DateTime referenceDateTime = new DateTime(2016, 12, 5, 15, 18, 22, 65);
                                                    DateTime actual = adjuster.adjustInto(referenceDateTime);
                                                    DateTime expected = referenceDateTime;
                                                    while(expected.getDayOfWeek() != DateTimeConstants.WEDNESDAY)
                                                    expected = expected.plusDays(1);
                                                    expected = expected.plusWeeks(2);
                                                    expected = expected.withTime(zero);

                                                    assertEquals( actual, expected, adjuster.toString() );
                                                    }
  ")

(defn -main
    "I don't do a whole lot...yet."
    [& args]
    (insta/visualize (as-and-bs "aaabbab")
                     :output-file "vizexample1.png"
                      :options {:dpi 63}))
