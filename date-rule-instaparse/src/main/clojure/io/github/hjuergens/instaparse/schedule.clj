(ns io.github.hjuergens.instaparse.schedule
  "The shared \"schedule\" example parsed with instaparse. parse returns the rule (a ScheduleSemantics$Rule): a function
  of the context (base date, market and events) that is evaluated later. The parse tree is transformed bottom-up into
  the objects of ScheduleSemantics, the helper all JVM modules share."
  (:require [clojure.java.io :as io]
            [instaparse.core :as insta])
  (:import (io.github.hjuergens.instaparse ScheduleSemantics)
           (java.time LocalDate)))

(def parser
  (insta/parser (io/resource "io/github/hjuergens/instaparse/schedule.ebnf") :auto-whitespace :standard))

(defn- int-of [s] (Integer/parseInt s))

(defn- date-of [y m d] (ScheduleSemantics/literal (LocalDate/of (int-of y) (int-of m) (int-of d))))

;; one function per tag; a shift becomes a function from expression to expression
(def ^:private transforms
  {:schedule       identity
   :items          (fn [& exprs] (ScheduleSemantics/list (vec exprs)))
   :range          (fn [first step last] (ScheduleSemantics/range first step last))
   :item           (fn [expr & weekdays] (if weekdays (ScheduleSemantics/onlyOn expr (vec weekdays)) expr))
   :expr           (fn [date & shifts] (reduce (fn [expr shift] (shift expr)) date shifts))
   :plus           (fn [period] #(ScheduleSemantics/plus % period))
   :minus          (fn [period] #(ScheduleSemantics/minus % period))
   :imm            (fn [] #(ScheduleSemantics/nextImm %))
   :spot           (fn [& [pair]] #(ScheduleSemantics/spot % pair))
   :roll           (fn [convention] #(ScheduleSemantics/roll % convention))
   :next           (fn [anchor] #(ScheduleSemantics/next % anchor))
   :prev           (fn [anchor] #(ScheduleSemantics/previous % anchor))
   :observance     (fn [& shifts] #(ScheduleSemantics/observe % (vec shifts)))
   :observe        (fn [weekday sign period] (ScheduleSemantics/shift weekday period (if (= sign "+") 1 -1)))
   :weekday-anchor (fn [weekday] (ScheduleSemantics/weekday weekday))
   :bd-anchor      (fn [] ScheduleSemantics/BUSINESS_DAY)
   :imm-anchor     (fn [] ScheduleSemantics/IMM)
   :annual-anchor  (fn [annual] (ScheduleSemantics/annual annual))
   :event-anchor   (fn [event] (ScheduleSemantics/eventAnchor (subs event 1)))
   :base           (fn [] ScheduleSemantics/BASE)
   :tom            (fn [] (ScheduleSemantics/plus ScheduleSemantics/BASE (ScheduleSemantics/period 1 "B")))
   :spot-date      (fn [& [pair]] (ScheduleSemantics/spot ScheduleSemantics/BASE pair))
   :iso            (fn [y m d] (date-of y m d))
   :german         (fn [d m y] (date-of y m d))
   :us             (fn [m d y] (date-of y m d))
   :in-year        (fn [annual] (ScheduleSemantics/inYear annual))
   :event          (fn [event] (ScheduleSemantics/event (subs event 1)))
   :month-day      (fn [day month] (ScheduleSemantics/monthDay (int-of day) month))
   :nth-weekday    (fn [n weekday month] (ScheduleSemantics/nthWeekday (int-of n) weekday month))
   :last-weekday   (fn [weekday month] (ScheduleSemantics/lastWeekday weekday month))
   :easter         (fn [] ScheduleSemantics/EASTER)
   :period         (fn [n unit] (ScheduleSemantics/period (int-of n) unit))})

(defn parse
  "the rule of a schedule; evaluate it with (.evaluate rule (ScheduleSemantics$Context. base market events)).
  Throws IllegalArgumentException on a syntax error, DateTimeException on an invalid date."
  [text]
  (let [tree (insta/parse parser text)]
    (when (insta/failure? tree)
      (throw (IllegalArgumentException. (pr-str tree))))
    (insta/transform transforms tree)))
