(defproject date-parser/date-rule-antlr "0.1.1-SNAPSHOT"
  :description "Date Rule Interpreter using Antlr"
  :url "http://hjuergens.github.io/date-parser/date-rule-antlr"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [rhizome "0.2.9"]
                 [clojure.java-time "0.3.2"]
                 [org.threeten/threeten-extra "1.3.2"]
                 [clj-antlr "0.2.4"]]
  :main date-rule-antlr.core)

