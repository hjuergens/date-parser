(ns io.github.hjuergens.instaparse.rulefile
  "Rule files parsed with instaparse (rulefile.ebnf): the tree is transformed bottom-up into the declarations of
  RuleCatalog, the evaluator all JVM modules share; shifts become RuleCatalog$Move objects. The @references and the
  Hijri methods of a rule are read from its tree before the transform."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [instaparse.core :as insta])
  (:import (io.github.hjuergens.instaparse RuleCatalog RuleCatalog$CalendarSpec RuleCatalog$Citation
                                           RuleCatalog$IfClause RuleCatalog$Named RuleCatalog$Postpone RuleCatalog$Scope
                                           ScheduleSemantics)
           (clojure.lang MapEntry)
           (java.time LocalDate)))

;; whitespace and comments between tokens
(def ^:private whitespace (insta/parser "ws = #'(\\s|#[^\\n]*)+'"))

(def parser
  (insta/parser (io/resource "io/github/hjuergens/instaparse/rulefile.ebnf") :auto-whitespace whitespace))

(defn- int-of [s] (Integer/parseInt s))

(defn- unquote* [s] (when s (subs s 1 (dec (count s)))))

(defn- date-of [y m d] (LocalDate/of (int-of y) (int-of m) (int-of d)))

(defn- calendar-in [opts] (first (filter #(instance? RuleCatalog$CalendarSpec %) opts)))

(defn- shifted [start shifts] (RuleCatalog/shifted start (vec shifts)))

(defn- count-of [ordinal] (if ordinal (ScheduleSemantics/ordinal ordinal) 1))

(defn- tagged [opts tag] (some #(when (and (map? %) (contains? % tag)) (get % tag)) opts))

;; one function per tag; optional parts come back as maps ({:exclude ...}) or as values of their own type
(def ^:private transforms
  {:schedule       (fn [rule] rule)
   :items          (fn [& items] (RuleCatalog/exprs (vec items)))
   ;; declarations
   :rule-decl      (fn [key event-class include & more]
                     {:key key :event-class event-class :include include
                      :exclude (or (tagged more :exclude) [])
                      :from (tagged more :from) :to (tagged more :to)
                      :body (first (remove map? more))
                      :attributes (or (tagged more :attributes) [])
                      :citations (or (tagged more :citations) [])})
   :weekend-decl   (fn [scope weekdays & [from]] (RuleCatalog/weekend scope weekdays (:from from)))
   :calendar-decl  (fn [key calendar & [cutoff]] (RuleCatalog/calendarDeclaration key calendar cutoff))
   :cutoff-attribute (fn [time zone] (RuleCatalog/cutoff time zone))
   :one-of         (fn [& alternatives] (RuleCatalog/oneOf (vec alternatives)))
   :alternative    (fn [item name] (MapEntry. name (RuleCatalog/exprs [item])))
   :name           identity
   :scopes         (fn [& scopes] (vec scopes))
   :except         (fn [scopes] {:exclude scopes})
   :validity       (fn [& parts] {:from (tagged parts :from) :to (tagged parts :to)})
   :valid-from     (fn [d] {:from d})
   :valid-to       (fn [d] {:to d})
   :iso-date       date-of
   :attributes     (fn [& attributes] {:attributes (vec attributes)})
   :kind-attribute (fn [kind] (MapEntry. "kind" kind))
   :session        (fn [opens & more]
                     (RuleCatalog/session opens (tagged more :closes) (tagged more :tz)))
   :closes         (fn [time] {:closes time})
   :session-zone   (fn [s] {:tz (unquote* s)})
   :precision-attribute (fn [precision] (MapEntry. "precision" precision))
   :note           (fn [s] (MapEntry. "note" (unquote* s)))
   :time           (fn [h m] (RuleCatalog/time (int-of h) (int-of m)))
   :provenance     (fn [& citations] {:citations (vec citations)})
   :citation       (fn [source & more]
                     (RuleCatalog$Citation. (unquote* source) (tagged more :article) (tagged more :url) (last more)))
   :article        (fn [s] {:article (unquote* s)})
   :url            (fn [s] {:url (unquote* s)})
   ;; calendars
   :calendar       (fn [& terms] (RuleCatalog/calendar (vec terms)))
   :scope-term     (fn ([scope] (RuleCatalog$Scope. scope 0 #{}))
                     ([scope [days unless]] (RuleCatalog$Scope. scope (int days) (set unless))))
   :named-term     (fn [key] (RuleCatalog$Named. key))
   :ignore         (fn [days & [unless]] [(int-of days) (or unless [])])
   :unless         (fn [& currencies] (vec currencies))
   :on             identity
   :on-scope       (fn [scope] (RuleCatalog$Scope. scope 0 #{}))
   ;; items
   :plain-item     (fn [expr & more]
                     (let [combined (reduce (fn [left step] (if (fn? step) (step left) left)) expr more)]
                       (RuleCatalog/ruleItem combined (first (filter #(instance? RuleCatalog$IfClause %) more)))))
   :picked-item    (fn [[n from-end] start & more]
                     (let [moves (filter #(instance? io.github.hjuergens.instaparse.RuleCatalog$Move %) more)
                           combined (reduce (fn [left step] (if (fn? step) (step left) left)) (shifted start moves) more)]
                       (ScheduleSemantics/pick n from-end
                                               (RuleCatalog/ruleItem combined (first (filter #(instance? RuleCatalog$IfClause %) more))))))
   :pick           identity
   :if-clause      (fn [condition & shifts] (RuleCatalog$IfClause. condition (vec shifts)))
   :include        (fn [operand & shifts] (fn [left] (shifted (ScheduleSemantics/combined left true operand) shifts)))
   :exclude        (fn [operand & shifts] (fn [left] (shifted (ScheduleSemantics/combined left false operand) shifts)))
   :operand        (fn [& exprs] (vec exprs))
   :series         (fn ([start-out first last end-out]   ; [a : b], or an open series [a : step : ]
                      (if (instance? java.time.temporal.TemporalAmount last)
                        (ScheduleSemantics/series first last nil start-out end-out)
                        (ScheduleSemantics/series first nil last start-out end-out)))
                     ([start-out first step last end-out] (ScheduleSemantics/series first step last start-out end-out)))
   :closed-start   (fn [] false)
   :open-start     (fn [] true)
   :closed-end     (fn [] false)
   :open-end       (fn [] true)
   :endpoint       (fn [start & shifts] (shifted start shifts))
   :expr           (fn [date & shifts] (shifted date shifts))
   ;; shifts
   :plus           (fn [period & opts] (RuleCatalog/plus period 1 (boolean (some #{:endend} opts)) (calendar-in opts)))
   :minus          (fn [period & opts] (RuleCatalog/plus period -1 (boolean (some #{:endend} opts)) (calendar-in opts)))
   :endend         (fn [] :endend)
   :imm            (fn [] (RuleCatalog/nextImm))
   :spot           (fn [& opts] (RuleCatalog/spot (first (filter string? opts)) (calendar-in opts)))
   :fix            (fn [& [pair]] (RuleCatalog/fix pair))
   :roll           (fn [convention & [on]] (RuleCatalog/rollShift convention on))
   :next           (fn ([anchor] (RuleCatalog/next 1 anchor))
                     ([n anchor] (RuleCatalog/next (count-of n) anchor)))
   :prev           (fn ([anchor] (RuleCatalog/previous 1 anchor))
                     ([n anchor] (RuleCatalog/previous (count-of n) anchor)))
   :nearest        (fn [anchor] (RuleCatalog/nearestShift anchor))
   :nearest-roll   (fn [on] (RuleCatalog/rollShift "NEAREST" on))
   :cutoff         (fn [time zone] (RuleCatalog/cutoff time zone))
   :cutoff-on      (fn [on] (RuleCatalog/cutoffOn on))
   :select         (fn [[n from-end] anchor period] (RuleCatalog/select n from-end anchor period))
   :from-start     (fn [ordinal] [(ScheduleSemantics/ordinal ordinal) false])
   :from-end       (fn [ordinal] [(ScheduleSemantics/ordinal ordinal) true])
   :last           (fn [] [1 true])
   :dates-in-year  (fn [& annuals] (RuleCatalog/datesInYear (vec annuals)))
   :postpone       (fn [convention & opts]
                     (RuleCatalog$Postpone. convention (calendar-in opts)
                                            (first (filter #(instance? java.time.temporal.TemporalAmount %) opts))
                                            (boolean (some #{:cumulative} opts))))
   :cumulative     (fn [] :cumulative)
   :observance     (fn [& entries] (RuleCatalog/observance (vec entries)))
   :observe-plus   (fn [weekdays period] (RuleCatalog/observePlus weekdays period 1))
   :observe-minus  (fn [weekdays period] (RuleCatalog/observePlus weekdays period -1))
   :observe-next   (fn [weekdays anchor] (RuleCatalog/observeAnchor weekdays anchor 1))
   :observe-prev   (fn [weekdays anchor] (RuleCatalog/observeAnchor weekdays anchor -1))
   :observe-nearest (fn [weekdays anchor] (RuleCatalog/observeNearest weekdays anchor))
   :observe-roll   (fn [weekdays convention & [on]] (RuleCatalog/observeRoll weekdays convention on))
   :weekdays       (fn [& weekdays] (set weekdays))
   ;; anchors
   :any-of         (fn [& anchors] (ScheduleSemantics/anyOf (vec anchors)))
   :weekday-anchor (fn [weekday] (ScheduleSemantics/weekday weekday))
   :bd-anchor      (fn [& [on]] (RuleCatalog/businessDay on))
   :fd-anchor      (fn [] ScheduleSemantics/FIXING_DAY)
   :imm-anchor     (fn [] ScheduleSemantics/IMM)
   :annual-anchor  (fn [annual] (ScheduleSemantics/annual annual))
   :fixed-anchor   (fn [annual] (ScheduleSemantics/annual annual))
   :event-anchor   (fn [event] (ScheduleSemantics/eventAnchor (subs event 1)))
   :holiday-anchor (fn [& [on]] (RuleCatalog/holiday on))
   :nonholiday-anchor (fn [& [on]] (RuleCatalog/nonholiday on))
   ;; dates
   :base           (fn [] ScheduleSemantics/BASE)
   :tom            (fn [] (ScheduleSemantics/plus ScheduleSemantics/BASE (ScheduleSemantics/period 1 "B")))
   :spot-date      (fn [& opts] (RuleCatalog/spotDate (first (filter string? opts)) (calendar-in opts)))
   :iso            (fn [y m d] (ScheduleSemantics/literal (date-of y m d)))
   :german         (fn [d m y] (ScheduleSemantics/literal (date-of y m d)))
   :us             (fn [m d y] (ScheduleSemantics/literal (date-of y m d)))
   :in-year        (fn [annual] (ScheduleSemantics/inYear annual))
   :event          (fn [event] (ScheduleSemantics/event (subs event 1)))
   :month-day      (fn [day month & [method]] (RuleCatalog/monthDay (int-of day) month method))
   :hijri-method   identity
   :nth-weekday    (fn [n weekday month] (ScheduleSemantics/nthWeekday (int-of n) weekday month))
   :last-weekday   (fn [weekday month] (ScheduleSemantics/lastWeekday weekday month))
   :easter         (fn [] ScheduleSemantics/EASTER)
   :orthodox-easter (fn [] RuleCatalog/ORTHODOX_EASTER)
   :season         (fn [kind month & [zone]] (RuleCatalog/season kind month zone))
   :solar-term     (fn [n & [zone]] (RuleCatalog/solarTerm (int-of n) zone))
   :hindu          (fn [key] (RuleCatalog/hindu key))
   :zone           (fn [z] (if (str/starts-with? z "\"") (unquote* z) z))
   :period         (fn [n unit] (ScheduleSemantics/period (int-of n) unit))
   ;; conditions
   :condition      (fn [& parts] (if (next parts) (RuleCatalog/anyCondition (vec parts)) (first parts)))
   :cond-and       (fn [& parts] (if (next parts) (RuleCatalog/allConditions (vec parts)) (first parts)))
   :cond-not       (fn ([atom] atom) ([_ atom] (RuleCatalog/notCondition atom)))
   :negate         (fn [] :not)
   :on-weekday     (fn [weekdays] (RuleCatalog/onWeekday weekdays))
   :on-day         (fn [first last & [month]] (RuleCatalog/onDay (int-of first) (int-of last) month))
   :is-weekday     (fn [subject weekdays] (RuleCatalog/isWeekday subject weekdays))
   :date-is-holiday (fn [subject & [on]] (RuleCatalog/dateIsHoliday subject on))
   :is-holiday     (fn [& [on]] (RuleCatalog/holidayCondition on))
   :between-holidays (fn [& [on]] (RuleCatalog/betweenHolidays on))
   :every          (fn [period from] (RuleCatalog/every period (int-of from)))
   :subject        (fn [date & shifts] (shifted date shifts))
   :subject-plus   (fn [period] (RuleCatalog/plus period 1 false nil))
   :subject-minus  (fn [period] (RuleCatalog/plus period -1 false nil))
   :weekday-set    (fn [& weekdays] (set weekdays))})

(defn- tree-of [text]
  (let [tree (insta/parse parser text)]
    (when (insta/failure? tree)
      (throw (IllegalArgumentException. (pr-str tree))))
    tree))

(defn- references
  "the names of the @references in a tree: the EVENT tokens are the strings starting with @"
  [tree]
  (set (for [x (tree-seq vector? seq tree) :when (and (string? x) (str/starts-with? x "@"))] (subs x 1))))

(defn- estimated?
  "whether a tree has a Hijri method other than UMMALQURA"
  [tree]
  (boolean (some #(and (vector? %) (= :hijri-method (first %)) (not= "UMMALQURA" (second %))) (tree-seq vector? seq tree))))

(defn- declaration [tree]
  (if (= :rule-decl (first tree))
    (let [{:keys [key event-class include exclude from to body attributes citations]} (insta/transform transforms tree)]
      (RuleCatalog/rule key event-class include exclude from to body attributes citations (references tree)
                        (estimated? tree)))
    (insta/transform transforms tree)))

(defn parse-rules
  "the declarations of a rule file, for the constructor of RuleCatalog. Throws IllegalArgumentException on a syntax
  error."
  [text]
  (let [[_ [tag & declarations]] (tree-of text)]
    (when-not (= :declarations tag)
      (throw (IllegalArgumentException. "a schedule, not a rule file")))
    (mapv declaration declarations)))

(defn schedule
  "one expression of the rule language, a ScheduleSemantics$Rule"
  [text]
  (let [[_ [tag :as node]] (tree-of text)]
    (when-not (= :schedule tag)
      (throw (IllegalArgumentException. (str "not a schedule: " text))))
    (insta/transform transforms node)))
