
(#_"
=> (ns date-rule-antlr.core)
=> (use 'date-rule-antlr.core :reload)
")
(ns date-rule-antlr.core
  (:gen-class); for -main method in uberjar
  (:import [java.nio.file Files Paths])
  (:require [clj-antlr.core :as antlr]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]])
  (:import [java.time Year Period LocalDate LocalDateTime DayOfWeek]
           [java.time.temporal Temporal TemporalAdjusters TemporalAdjuster])
  (:use clojure.pprint))

(def aaa (antlr/parser "grammar Aaa;
                           aaa : AA+;
                           AA : [Aa]+ ;
                           WS : ' ' -> channel(HIDDEN) ;"))

(#_ "for documentation
=> (use 'clojure.repl)
=> (doc antlr/parser)
")

;(def url (io/resource "Json.g4" (clojure.lang.RT/baseLoader)))
;(def grammarTxt (String/join "\n" (Files/readAllLines (Paths/get (.toURI (-> "Json.g4" io/resource))))))
;(println grammarTxt)
;(def json (antlr/parser grammarTxt))
;(->> "[1,,3]" (antlr/parse json {:throw? false}) pprint)
;(try (json "[1,2,,3,]") (catch clj_antlr.ParseError e (pprint @e)))
(def rule ">>>=wednesday")
(def grammarTxt (String/join "\n" (Files/readAllLines (Paths/get (.toURI (-> "SelectorByName.g4" io/resource))))))
(def selByName (antlr/parser grammarTxt))
(def result (->> rule (antlr/parse selByName {:throw? false}) ))

;(defn counter [^String l] (reduce + (filter identity (map {\> 1 \< -1} l))))
(defn counter [l] (reduce + (map {"<" -1 ">" 1 "<=" -1 ">=" 1} l)))

(let [[x [u & direction] z] result] (reduce + (map {">" 1 "<" -1 ">=" 0} direction)))

(def nextWednesdayAdjuster (TemporalAdjusters/next DayOfWeek/WEDNESDAY))
;(defn nextDayOfWeek [^DayOfWeek dayOfWeek] (^TemporalAdjuster TemporalAdjusters/next dayOfWeek))

;(def nextWednesday (nextDayOfWeek DayOfWeek/WEDNESDAY))
;TODO prefer with over adjustInto


(def ^TemporalAdjuster nextWednesday (reify TemporalAdjuster (adjustInto [this t] (.adjustInto (TemporalAdjusters/next DayOfWeek/WEDNESDAY) t))))
(instance? TemporalAdjuster nextWednesday)

(def ^Temporal anyDate (LocalDateTime/of 2016 12 3 12 18 22 65))

(.adjustInto nextWednesday anyDate)
(.with anyDate nextWednesday)

(take 3 (iterate #(.adjustInto nextWednesday %) anyDate))
(take 3 (iterate  (fn [^Temporal t] (.with t nextWednesday)) anyDate))

(def dow-string-keys {
                       "monday" 'DayOfWeek/MONDAY
                       "tuesday" 'DayOfWeek/TUESDAY
                       "wednesday" 'DayOfWeek/WEDNESDAY
                       "thursday" 'DayOfWeek/THURSDAY
                       "friday" 'DayOfWeek/FRIDAY
                       "saturday" 'DayOfWeek/SATURDAY
                       "sunday" 'DayOfWeek/SUNDAY
                       })

(def dow-adjuster-string-keys {
                                ">" 'TemporalAdjusters/next
                                ">=" 'TemporalAdjusters/nextOrSame
                                "<" 'TemporalAdjusters/previous
                                "<=" 'TemporalAdjusters/previousOrSame
                                })

(let [[x [u & direction] [d dayOfWeek]] result] (reverse (map list (map dow-adjuster-string-keys direction) (repeat (dow-string-keys dayOfWeek)))))
(def dow-adjusters-prs (let [[x [u & direction] [d dayOfWeek]] result] (reverse (map list (map dow-adjuster-string-keys direction) (repeat (dow-string-keys dayOfWeek))))))
(println rule " produces list "dow-adjusters-prs)

(eval (list 'TemporalAdjusters/next (dow-string-keys "wednesday")))

(let [[x [u & direction] [d dayOfWeek]] result]
  (list (counter direction) 'TemporalAdjusters/next (dow-string-keys dayOfWeek)))

(nth (iterate #(.adjustInto (TemporalAdjusters/next DayOfWeek/WEDNESDAY) %) anyDate) 3)

((eval (list 'fn ['x] (list 'dow-string-keys 'x))) "wednesday")

((eval (list 'fn ['x] (list '.adjustInto (list 'TemporalAdjusters/next 'DayOfWeek/WEDNESDAY) 'x))) anyDate)


(defmacro adjust-day-of-week-m
  [adjuster ^DayOfWeek dayOfWeek]
  (let [d (gensym)] `(fn [~d] ('.adjustInto (~adjuster ~dayOfWeek ) ~d))))
((adjust-day-of-week-m TemporalAdjusters/next DayOfWeek/WEDNESDAY) anyDate)

; TODO use this ..
(eval (let [adjuster 'TemporalAdjusters/next dayOfWeek 'DayOfWeek/WEDNESDAY] (list 'fn ['d] (list '.adjustInto (with-meta (list adjuster dayOfWeek) {:tag 'TemporalAdjuster}) 'd))))
; TODO to correct this
(defn adjust-day-of-week-fn
  [adjuster ^DayOfWeek dayOfWeek]
  (fn [d] (.adjustInto ^TemporalAdjuster ('adjuster 'dayOfWeek) d)))
(println "the function " adjust-day-of-week-fn " should return adjuster functions")

(map #(apply adjust-day-of-week-fn %) dow-adjusters-prs)
(def fncts ( map #(apply adjust-day-of-week-fn %) dow-adjusters-prs))
;(reduce (fn [x y] (y x)) anyDate fncts)

; TODO continue here

(let [dow (dow-string-keys "sunday")] (list 'fn* ['x] (list '.adjustInto (list 'TemporalAdjusters/next dow) 'x)))

(let [dow (dow-string-keys "wednesday")
      i 3]
  (list 'nth (list 'iterate (list 'fn* ['x] (list '.adjustInto (list 'TemporalAdjusters/next dow) 'x)) 'anyDate) i))

;(defmacro next-day-of-week [dow i] (let [x (gensym)] `(iterate (fn*[~x] (.adjustInto (TemporalAdjusters/next ~dow) ~x)) anyDate)))

(defmacro next-day-of-week [dow c] (let [x (gensym) d (with-meta (gensym) { :tag 'Temporal})] `(fn* [~d] (nth (iterate (fn*[~x] (.adjustInto (TemporalAdjusters/next ~dow) ~x)) ~d) ~c))))

(macroexpand-1 '(next-day-of-week DayOfWeek/WEDNESDAY 4))

((next-day-of-week DayOfWeek/WEDNESDAY 4) anyDate)

(#_"")

(defmacro nextDayOfWeekAdjuster [dow i] (let [t (gensym)] `(reify TemporalAdjuster (adjustInto ^Temporal [this ^Temporal ~t] ((next-day-of-week ~dow ~i) ~t)))))

(.adjustInto (nextDayOfWeekAdjuster DayOfWeek/WEDNESDAY 4) anyDate)


(def parsedAdjuster (let [[x [u & direction] [d dayOfWeek]] result] (list 'nextDayOfWeekAdjuster (dow-string-keys dayOfWeek) (counter direction))))
(.adjustInto ^TemporalAdjuster (eval parsedAdjuster) anyDate)
(.with anyDate (eval parsedAdjuster))


(defn parse-to-adjuster
  [rule]
  (let [r (->> rule (antlr/parse selByName {:throw? false}))
        [x [u & direction] [d dayOfWeek]] r]
    (list 'nextDayOfWeekAdjuster (dow-string-keys dayOfWeek) (counter direction))))


(#_"defn thirdWednesday
  [^Temporal t]
  (let [^Temporal w (.next TemporalAdjusters DayOfWeek/WEDNESDAY)]
                 (.adjustInto w t))")

(#_ "TemporalAdjuster thirdWednesday = new TemporalAdjuster() {
  private TemporalAdjuster w = TemporalAdjusters.next(DayOfWeek.WEDNESDAY);
  @Override
  public Temporal adjustInto(Temporal temporal) {
    Temporal rtnTemporal = temporal;
    for (int i = 0; i < 3; i++) {
      rtnTemporal = w.adjustInto(rtnTemporal);
    }
    return rtnTemporal;
  }
};")


;; https://github.com/clojure/tools.cli
(defn -main [& args]
  "I don't do a whole lot...yet."
  (let [options (parse-opts args [["-f", "--file FILE", "file name"]])
        {:keys [key user file]} (:options options)]
    (println (String/join "\n" (Files/readAllLines (Paths/get (.toURI (-> file io/resource))))))))

