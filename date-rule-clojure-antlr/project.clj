(defproject date-parser/date-rule-clojure-antlr "0.1.2-SNAPSHOT"
  :description "Date Rule Interpreter using Antlr"
  :url "http://hjuergens.github.io/date-parser/date-rule-clojure-antlr"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [rhizome "0.2.9"]
                 [clojure.java-time "0.3.2"]
                 [org.threeten/threeten-extra "1.3.2"]
                 [clj-antlr "0.2.4"]
                 [org.clojure/tools.cli "0.3.7"]]
  :profiles { ;:dev {:dependencies
              ;       [[org.clojure/test.check "0.9.0"]]}
             :uberjar {:aot :all}}
  :resource-paths ["resources"]
  :main date-rule-antlr.core)

