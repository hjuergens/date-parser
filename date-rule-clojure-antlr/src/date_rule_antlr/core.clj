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
(defn parse-rule [rule] (->> rule (antlr/parse selByName {:throw? false})))

;(defn counter [^String l] (reduce + (filter identity (map {\> 1 \< -1} l))))
(defn counter [l] (reduce + (map {"<" -1 ">" 1 "<=" -1 ">=" 1} l)))

(def result (parse-rule rule))
(let [[x [u & direction] z] result] (reduce + (map {">" 1 "<" -1 ">=" 0} direction)))

(def nextWednesdayAdjuster (TemporalAdjusters/next DayOfWeek/WEDNESDAY))
;(defn nextDayOfWeek [^DayOfWeek dayOfWeek] (^TemporalAdjuster TemporalAdjusters/next dayOfWeek))

;(def nextWednesday (nextDayOfWeek DayOfWeek/WEDNESDAY))
;TODO prefer with over adjustInto


(def ^TemporalAdjuster nextWednesday
  (reify TemporalAdjuster (adjustInto [this t] (.adjustInto (TemporalAdjusters/next DayOfWeek/WEDNESDAY) t))))
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
                       "sunday" 'DayOfWeek/SUNDAY})


(def dowadjuster-string-keys {
                                ">" 'TemporalAdjusters/next
                                ">=" 'TemporalAdjusters/nextOrSame
                                "<" 'TemporalAdjusters/previous
                                "<=" 'TemporalAdjusters/previousOrSame})



(let [[x [u & direction] [d dayOfWeek]] result] (reverse (map list (map dowadjuster-string-keys direction) (repeat (dow-string-keys dayOfWeek)))))

(def direction (let [[x [u & direction] z] result] direction)) ;(">" ">" ">=")
(frequencies direction) ;{">" 2, ">=" 1}
(map dowadjuster-string-keys direction) ;(TemporalAdjusters/next TemporalAdjusters/next TemporalAdjusters/nextOrSame)
(into '() (for [[k v] (frequencies direction)] (list 'repeat v (dowadjuster-string-keys k)))) ;((repeat 1 TemporalAdjusters/nextOrSame) (repeat 2 TemporalAdjusters/next))

; help function
(defn map-function-on-map-vals [m f]
  (reduce (fn [altered-map [k v]] (assoc altered-map k (f v))) m m))
(defn foo [m f] (into {} (for [[k v] m] [k (f v)]))) ; https://stackoverflow.com/questions/1676891/mapping-a-function-on-the-values-of-a-map-in-clojure

(def dow-adjusters-prs (let [[x [u & direction] [d dayOfWeek]] result] (reverse (map list (map dowadjuster-string-keys direction) (repeat (dow-string-keys dayOfWeek))))))
(println rule " produces list " dow-adjusters-prs)

(eval (list 'TemporalAdjusters/next (dow-string-keys "wednesday")))

(let [[x [u & direction] [d dayOfWeek]] result]
  (list 'repeat (counter direction) (list 'TemporalAdjusters/next (dow-string-keys dayOfWeek))))

(let [direction '(">" ">" ">=") dayOfWeek "wednesday"]
  (into '() (for [[k v] (frequencies direction)] (list 'repeat v (list (dowadjuster-string-keys k) (dow-string-keys dayOfWeek))))))
(println ">" ">" ">=" "wednesday" " produces ")
(prn (let [direction '(">" ">" ">=") dayOfWeek "wednesday"]
       (into '() (for [[k v] (frequencies direction)] (list 'repeat v (list (dowadjuster-string-keys k) (dow-string-keys dayOfWeek)))))))

(defmacro adjust-day-of-week-m
  "TODO"
  [adjuster ^DayOfWeek dayOfWeek]  (let [d (gensym)] `(fn [~d] (.adjustInto (~adjuster ~dayOfWeek ) ~d))))

((adjust-day-of-week-m TemporalAdjusters/next DayOfWeek/WEDNESDAY) anyDate)

; TODO use this ..
(eval (let [adjuster 'TemporalAdjusters/next dayOfWeek 'DayOfWeek/WEDNESDAY]
        (list 'fn ['d] (list '.adjustInto (with-meta (list adjuster dayOfWeek) {:tag 'TemporalAdjuster}) 'd))))
; TODO to correct this
(defn adjust-day-of-week-expr-fn
  "Returns an expression for a function taking one date parameter"
  [adjuster ^DayOfWeek dayOfWeek]
  (let [a adjuster d dayOfWeek t (gensym)] (list 'fn (vector t) (list '.adjustInto (list a d) t))))

(println "the function " adjust-day-of-week-expr-fn " should return adjuster functions")
((eval (adjust-day-of-week-expr-fn 'TemporalAdjusters/next 'DayOfWeek/WEDNESDAY)) anyDate)

; list of adjuster functions
(def fn-coll (map #(eval (apply adjust-day-of-week-expr-fn %)) dow-adjusters-prs))


(map #(apply adjust-day-of-week-expr-fn %) dow-adjusters-prs)
(def fncts ( map #(apply adjust-day-of-week-expr-fn %) dow-adjusters-prs))
;(reduce (fn [x y] (y x)) anyDate fncts)

(println "apply recursivly any function in 'fn-coll starting with 'anyDate")
(println (let [rule "<<<=sunday"
               result (parse-rule rule)
               dow-adjusters-prs (let [[x [u & direction] [d dayOfWeek]] result] (reverse (map list (map dowadjuster-string-keys direction) (repeat (dow-string-keys dayOfWeek)))))
               fn-coll (map #(eval (apply adjust-day-of-week-expr-fn %)) dow-adjusters-prs)
               ^Temporal anyDate (LocalDateTime/of 2018 07 07 12 18 22)]
           (loop [c fn-coll t anyDate] (let [[f & rest] c] (if (empty? c) t (recur rest (f t)))))))


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


(defn parse-to-adjuster-3
  [rule]
  (let [r (->> rule (antlr/parse selByName {:throw? false}))
        [x [u & direction] [d dayOfWeek]] r]
    (prn direction dayOfWeek)
    (map list (map dowadjuster-string-keys direction) (repeat (dow-string-keys dayOfWeek)))))


(defmacro nextDayOfWeekAdjuster [dow i] (let [t (gensym)] `(reify TemporalAdjuster (adjustInto ^Temporal [this ^Temporal ~t] ((next-day-of-week ~dow ~i) ~t)))))


(defn adjust-day-of-week-expr-fn
  [adjuster ^DayOfWeek dayOfWeek]
  (let [a adjuster d dayOfWeek t (gensym)] (list 'fn (vector t) (list '.adjustInto (list a d) t))))

(defn loop-fn
  "Returns function which recursivly apply the functions in a collection"
  [coll](binding [*ns* *ns*] (in-ns 'date-rule-antlr.core)
               (fn* [temporal]
                 (loop [c (reduce conj '() coll) t temporal]
                  (let [[f & rest] c] (if (empty? c) t (recur rest (f t))))))))


(defn rule-to-expr-fn
  "Returns form of a temporal function according to the rule"
  [^String rule]
  ;(prn (parse-to-adjuster-3 rule))
  (map #(apply adjust-day-of-week-expr-fn %) (parse-to-adjuster-3 rule)))

(defn rule-to-fn
  "Returns form of a temporal function according to the rule"
  [^String rule]
  ;(prn (parse-to-adjuster-3 rule))
  ;(map #(apply adjust-day-of-week-expr-fn %) (parse-to-adjuster-3 rule))
  (apply clojure.core/comp (map eval (rule-to-expr-fn rule))))


(def adjuster-fn (rule-to-fn "<<=sunday"))
(adjuster-fn (LocalDate/of 2018 8 17))


(defn ^TemporalAdjuster temporal-adjuster-fn [^String rule]
  (reify TemporalAdjuster (adjustInto [this t] ((rule-to-fn rule) t))))
(#_"defn thirdWednesday
  [^Temporal t]
  (let [^Temporal w (.next TemporalAdjusters DayOfWeek/WEDNESDAY)]
                 (.adjustInto w t))")

;; https://github.com/clojure/tools.cli
(defn -main [& args]
  "I don't do a whole lot...yet."
  (let [options (parse-opts args [["-f", "--file FILE", "file name"]])
        {:keys [key user file]} (:options options)]
    (println (String/join "\n" (Files/readAllLines (Paths/get (.toURI (-> file io/resource))))))))
