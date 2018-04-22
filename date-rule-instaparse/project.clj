(defproject date-rule-instaparse "0.1.1-SNAPSHOT"
  :description "Date Rule Interpreter using Instaparse"
  :url "http://hjuergens.github.io/date-parser/date-rule-instaparse"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [instaparse "1.4.8"]
                 [rhizome "0.2.9"]
                 [clojure.java-time "0.3.2"]
                 [org.threeten/threeten-extra "1.3.2"]]
  :main date-rule-instaparse.core
  :aot [date-rule-instaparse.core])
