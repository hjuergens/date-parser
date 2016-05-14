(ns date-rule-instaparse.core
  (:require [instaparse.core :as insta])
  (:gen-class)
  (:import (java.time Year)))

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

(def year
  (insta/parser
    "year = #'[1-9]\\d{3}' | twodigityear
    twodigityear = #'[0-9]{2}'"))

(insta/transform
  {:year #(Year/parse %1)}
  (year "1999"))

(defn parseYear
  "Parse string to year."
  [^String s]
  (insta/transform
    {:year #(java.time.Year/parse %1)}
    ((insta/parser "year = #'[1-9]\\d{3}'") s)))

(defn -main
    "I don't do a whole lot...yet."
    [& args]
    (insta/visualize (as-and-bs "aaabbab")
                     :output-file "vizexample1.png"
                      :options {:dpi 63}))
