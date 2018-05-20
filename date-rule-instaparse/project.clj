(defproject date-parser/date-rule-instaparse "0.1.1-SNAPSHOT"
  :description "Date Rule Interpreter using Instaparse"
  :url "http://hjuergens.github.io/date-parser/date-rule-instaparse"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [instaparse "1.4.8"]
                 [rhizome "0.2.9"]
                 [clojure.java-time "0.3.2"]
                 [org.threeten/threeten-extra "1.3.2"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]]
  :main date-rule-instaparse.core
  :aot [date-rule-instaparse.core]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler date-rule-instaparse.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})

