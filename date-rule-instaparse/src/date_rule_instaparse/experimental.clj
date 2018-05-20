(#_  "
=> (use 'date-rule-instaparse.experimental :reload)
=> (ns date-rule-instaparse.experimental)

require loads libs (that aren't already loaded), use does the same plus it refers to their namespaces with
clojure.core/refer (so you also get the possibility of using :exclude etc like with clojure.core/refer). Both
are recommended for use in ns rather than directly.
")
(ns date-rule-instaparse.experimental
  (:refer-clojure :exclude [range iterate format max min contains? zero?])
  (:import [java.time Year Period LocalDate LocalDateTime DayOfWeek]
           java.util.Date
           [java.time.temporal Temporal TemporalAdjusters])
  (:use java-time))

;(refer-clojure :exclude [range iterate format max min])
;(use 'java-time)

(interval (offset-date-time 2015 1 1) (zoned-date-time 2016 1 1))


(def now (local-date))

(plus now (days 1))

(def q (quarter now))

(adjust now :first-in-month :monday)

;(.adjustInto now q)

;(.with now (org.threeten.extra.Quarter/from now))

;;(Temporal#with(TemporalAdjuster))

(fn [ldt] (adjust ldt :next-or-same-day-of-week :wednesday))

(adjust now :next-or-same-day-of-week :wednesday)

((fn [ldt] (adjust ldt :next-or-same-day-of-week :wednesday)) now)

#(adjust % :next-or-same-day-of-week :wednesday)

(#_ "for documentation
=> (use 'clojure.repl)
=> (doc iterate)
")
(take 3 (java-time/iterate adjust now :next-or-same-day-of-week :wednesday))

(take 3 (iterate plus now (days 1))) ;; note `java-time/iterate`

(defn thirdWednesdayInMonth
  [ld]
  (plus (adjust ld :first-in-month :wednesday) (weeks 2)))

(defn adjustIntoThirdWednesday
  "shift a local date to third wednesday later (Selector/parse >>>wednesday"
  [^LocalDate ldt]
  (plus (adjust ldt :next-day-of-week :wednesday)) (weeks 2))


(defn adjustIntoThirdWednesdayInMonth
  "shift a local date to third wednesday in the month"
  [^LocalDate ldt]
  (adjust ldt :day-of-week-in-month 3 :wednesday))

(defn adjustIntoNextThirdWednesdayInMonth
  "shift a local date to third wednesday in the month"
  [^LocalDate ldt]
  (let [^LocalDate thirdWednesdayInMonth (adjust ldt :day-of-week-in-month 3 :wednesday)]
    (if (> (.compareTo thirdWednesdayInMonth ldt) 0)
      thirdWednesdayInMonth
      (adjust (adjust ldt :first-day-of-next-month) :day-of-week-in-month 3 :wednesday))))

(defn adjustIntoNextThirdWednesdayInMonth
  "shift a local date to third wednesday in the month"
  [^LocalDate ldt]
  (let [^LocalDate thirdWednesdayInMonth (adjust ldt :day-of-week-in-month 3 :wednesday)]
    (if (> (.compareTo thirdWednesdayInMonth ldt) 0)
      thirdWednesdayInMonth
      (adjust (adjust ldt :first-day-of-next-month) :day-of-week-in-month 3 :wednesday))))



(local-date-time 2015 10)
(format "MM/dd" (zoned-date-time 2015 9 28))

(defn as-localdate
  "Parse a string into an integer, or `nil` if the string cannot be parsed."
  [^String s]
  (try
    (local-date "yyyy-MM-dd" s)
    (catch NumberFormatException _ nil)))


;;(def nt-adjusters {:next-third-wednesday-in-month [#(adjustIntoNextThirdWednesdayInMonth %) 0]})

;;(def predefined-adjusters (merge predefined-adjusters nt-adjusters))





(year-quarter now)

;;YearQuarter lastQuarter = YearQuarter.now (zone).minusQuarters(1);
;;LocalDate lastDayOfLastQuarter = lastQuarter.atEndOfQuarter ();

;; next third wednesday of quarter : (date)=>date

;;(def my-adjusters {:first-day-of-month [(TemporalAdjusters/firstDayOfMonth) 0]
;;                   :last-day-of-month [(TemporalAdjusters/lastDayOfMonth) 0]
;;                   :first-day-of-next-month [(TemporalAdjusters/firstDayOfNextMonth) 0]})


