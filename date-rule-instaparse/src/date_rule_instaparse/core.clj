(ns date-rule-instaparse.core
  (:require [instaparse.core :as insta])
  (:gen-class))

(ns date-rule-instaparse.core
    (:gen-class))

(def as-and-bs
  (insta/parser
    "S = AB*
     AB = A B
     A = 'a'+
     B = 'b'+"))

(defn -main
    "I don't do a whole lot...yet."
    [& args]
    ((insta/visualize (as-and-bs "aaabbab") :output-file "vizexample1.png" :options {:dpi 63})))
