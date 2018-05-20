(ns date-rule-antlr.core
  (:gen-class); for -main method in uberjar
  (:import [java.time Year Period LocalDateTime DayOfWeek]
           java.util.Date
           [java.time.temporal Temporal TemporalAdjusters])
  (:require [clj-antlr.core :as antlr])
  (:use clojure.pprint))

(def aaa (antlr/parser "grammar Aaa;
                           aaa : AA+;
                           AA : [Aa]+ ;
                           WS : ' ' -> channel(HIDDEN) ;"))

(pprint (aaa "aAAaa A aAA AAAAaAA"))

(try (aaa "[1,2,,3,]") (catch clj_antlr.ParseError e (pprint @e)))

(defn -main
  "I don't do a whole lot...yet."
  [& args]
  (pprint (aaa "aAAaa A aAA AAAAaAA")))
