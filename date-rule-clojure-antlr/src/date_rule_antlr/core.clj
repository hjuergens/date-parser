(#_  "
=> (ns date-rule-antlr.core)
=> (use 'date-rule-antlr.core :reload)
")
(ns date-rule-antlr.core
  (:gen-class); for -main method in uberjar
  (:import [java.nio.file Files Paths])
  (:require [clj-antlr.core :as antlr]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]])
  (:use clojure.pprint))

(def aaa (antlr/parser "grammar Aaa;
                           aaa : AA+;
                           AA : [Aa]+ ;
                           WS : ' ' -> channel(HIDDEN) ;"))

(#_ "for documentation
=> (use 'clojure.repl)
=> (doc antlr/parser)
")

(def url (io/resource "Json.g4" (clojure.lang.RT/baseLoader)))

(def grammarTxt (String/join "\n" (Files/readAllLines (Paths/get (.toURI (-> "Json.g4" io/resource))))))

;(println grammarTxt)

(def json (antlr/parser grammarTxt))

(->> "[1,,3]" (antlr/parse json {:throw? false}) pprint)

(try (json "[1,2,,3,]") (catch clj_antlr.ParseError e (pprint @e)))

;; https://github.com/clojure/tools.cli
(defn -main [& args]
  "I don't do a whole lot...yet."
  (let [options (parse-opts args [["-f", "--file FILE", "file name"]])
        {:keys [key user file]} (:options options)]
    (println (String/join "\n" (Files/readAllLines (Paths/get (.toURI (-> file io/resource))))))))

