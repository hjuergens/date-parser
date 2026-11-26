package de.juergens.schedule.rules;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.juergens.schedule.RuleCatalog;
import de.juergens.schedule.RuleCatalog.CalendarSpec;
import de.juergens.schedule.RuleCatalog.Citation;
import de.juergens.schedule.RuleCatalog.Condition;
import de.juergens.schedule.RuleCatalog.IfClause;
import de.juergens.schedule.RuleCatalog.Move;
import de.juergens.schedule.ScheduleSemantics;
import de.juergens.schedule.ScheduleSemantics.Anchor;
import de.juergens.schedule.ScheduleSemantics.Annual;
import de.juergens.schedule.ScheduleSemantics.Expr;
import de.juergens.schedule.ScheduleSemantics.Rule;
import de.juergens.schedule.rules.lexer.Lexer;
import de.juergens.schedule.rules.lexer.LexerException;
import de.juergens.schedule.rules.node.*;
import de.juergens.schedule.rules.parser.Parser;
import de.juergens.schedule.rules.parser.ParserException;

/**
 * Rule files (see {@code rulefile.grammar}): SableCC builds the syntax tree, this class builds the declarations with
 * RuleCatalog and ScheduleSemantics, as ScheduleEvaluator does for a schedule.
 */
public final class RuleFiles {

    /** the @references of the rule being read */
    private Set<String> references = new LinkedHashSet<>();
    /** whether the rule being read has a Hijri method other than UMMALQURA */
    private boolean estimated;

    private RuleFiles() {
    }

    /**
     * the rule catalog of a rule file
     *
     * @throws IllegalArgumentException on a syntax error, an unknown character, overlapping versions, an unknown or a
     *         cyclic reference
     * @throws java.time.DateTimeException on an invalid date
     */
    public static RuleCatalog parseRules(String text) {
        return parseRules(text, null);
    }

    /** the same with announced dates: key -&gt; the dates an authority published */
    public static RuleCatalog parseRules(String text, Map<String, List<LocalDate>> announcements) {
        if (!(parse(text).getPRuleFile() instanceof ADeclarationsRuleFile file)) {
            throw new IllegalArgumentException("a schedule, not a rule file");
        }
        RuleFiles builder = new RuleFiles();
        List<Object> declarations = new ArrayList<>();
        for (Object declaration : file.getDeclaration()) {
            declarations.add(builder.declaration((PDeclaration) declaration));
        }
        return new RuleCatalog(declarations, announcements, RuleFiles::schedule);
    }

    /** one expression of the rule language */
    public static Rule schedule(String text) {
        if (!(parse(text).getPRuleFile() instanceof AScheduleRuleFile file)) {
            throw new IllegalArgumentException("not a schedule: " + text);
        }
        RuleFiles builder = new RuleFiles();
        List<Expr> items = new ArrayList<>();
        items.add(builder.item(file.getItem()));
        for (Object more : file.getMoreItem()) {
            items.add(builder.item(((AMoreItem) more).getItem()));
        }
        return RuleCatalog.exprs(items);
    }

    private static Start parse(String text) {
        try {
            return new Parser(new Lexer(new PushbackReader(new StringReader(text), 1024))).parse();
        } catch (ParserException | LexerException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (IOException e) {   // a string reader never throws it
            throw new UncheckedIOException(e);
        }
    }

    private static String unquote(Token token) {
        return token == null ? null : token.getText().substring(1, token.getText().length() - 1);
    }

    private static int number(Token token) {
        return Integer.parseInt(token.getText());
    }

    private String event(Token token) {
        String name = token.getText().substring(1);
        references.add(name);
        return name;
    }

    // ================= declarations =================

    private Object declaration(PDeclaration node) {
        if (node instanceof AWeekendDeclaration weekend) {
            return RuleCatalog.weekend(weekend.getScope().getText(), weekdays(weekend.getWeekdaySet()),
                    weekend.getValidFrom() == null ? null : isoDate(((AValidFrom) weekend.getValidFrom()).getIsoDate()));
        }
        if (node instanceof ACalendarDeclaration calendar) {
            return RuleCatalog.calendarDeclaration(calendar.getKey().getText(), calendar(calendar.getCalendar()));
        }
        ARuleDeclaration rule = (ARuleDeclaration) node;
        references = new LinkedHashSet<>();
        estimated = false;
        LocalDate from = null, to = null;
        if (rule.getValidity() instanceof AValidity validity) {
            from = validity.getValidFrom() == null ? null : isoDate(((AValidFrom) validity.getValidFrom()).getIsoDate());
            to = validity.getValidTo() == null ? null : isoDate(((AValidTo) validity.getValidTo()).getIsoDate());
        }
        Object body = body(rule.getBody());
        List<Map.Entry<String, Object>> attributes = new ArrayList<>();
        if (rule.getAttributes() instanceof AAttributes list) {
            attributes.add(attribute(list.getAttribute()));
            for (Object more : list.getMoreAttribute()) {
                attributes.add(attribute(((AMoreAttribute) more).getAttribute()));
            }
        }
        List<Citation> citations = new ArrayList<>();
        if (rule.getProvenance() instanceof AProvenance provenance) {
            citations.add(citation(provenance.getCitation()));
            for (Object more : provenance.getMoreCitation()) {
                citations.add(citation(((AMoreCitation) more).getCitation()));
            }
        }
        List<String> exclude = rule.getExcluded() == null ? List.of() : scopes(((AExcluded) rule.getExcluded()).getScopes());
        return RuleCatalog.rule(name(rule.getKey()), name(rule.getEventClass()), scopes(rule.getInclude()), exclude,
                from, to, body, attributes, citations, references, estimated);
    }

    private Object body(PBody node) {
        if (node instanceof AExprsBody exprs) {
            List<Expr> items = new ArrayList<>();
            items.add(item(exprs.getItem()));
            for (Object more : exprs.getMoreItem()) {
                items.add(item(((AMoreItem) more).getItem()));
            }
            return RuleCatalog.exprs(items);
        }
        AOneOfBody oneOf = (AOneOfBody) node;
        List<Map.Entry<String, Rule>> alternatives = new ArrayList<>();
        alternatives.add(alternative(oneOf.getAlternative()));
        for (Object more : oneOf.getMoreAlternative()) {
            alternatives.add(alternative(((AMoreAlternative) more).getAlternative()));
        }
        return RuleCatalog.oneOf(alternatives);
    }

    private Map.Entry<String, Rule> alternative(PAlternative node) {
        AAlternative alternative = (AAlternative) node;
        return Map.entry(name(alternative.getName()), RuleCatalog.exprs(List.of(item(alternative.getItem()))));
    }

    private static String name(PName node) {
        return node.toString().trim();
    }

    private static List<String> scopes(PScopes node) {
        AScopes scopes = (AScopes) node;
        List<String> out = new ArrayList<>();
        out.add(scopes.getScope().getText());
        for (Object more : scopes.getMoreScope()) {
            out.add(((AMoreScope) more).getScope().getText());
        }
        return out;
    }

    private static LocalDate isoDate(PIsoDate node) {
        AIsoDate date = (AIsoDate) node;
        return LocalDate.of(number(date.getYear()), number(date.getMonth()), number(date.getDay()));
    }

    private static Map.Entry<String, Object> attribute(PAttribute node) {
        if (node instanceof AKindAttribute kind) {
            return Map.entry("kind", (Object) kind.getKindWord().getText());
        }
        if (node instanceof APrecisionAttribute precision) {
            return Map.entry("precision", (Object) precision.getPrecisionWord().getText());
        }
        if (node instanceof ANoteAttribute note) {
            return Map.entry("note", (Object) unquote(note.getString()));
        }
        ASessionAttribute session = (ASessionAttribute) node;
        return RuleCatalog.session(time(session.getOpens()),
                session.getCloses() == null ? null : time(((ACloses) session.getCloses()).getTime()),
                session.getSessionZone() == null ? null : unquote(((ASessionZone) session.getSessionZone()).getString()));
    }

    private static String time(PTime node) {
        ATime time = (ATime) node;
        return RuleCatalog.time(number(time.getHours()), number(time.getMinutes()));
    }

    private static Citation citation(PCitation node) {
        ACitation citation = (ACitation) node;
        return new Citation(unquote(citation.getSource()),
                citation.getArticle() == null ? null : unquote(((AArticle) citation.getArticle()).getString()),
                citation.getUrl() == null ? null : unquote(((AUrl) citation.getUrl()).getString()),
                citation.getLabelWord().getText());
    }

    // ================= calendars =================

    private static CalendarSpec calendar(PCalendar node) {
        ACalendar calendar = (ACalendar) node;
        List<CalendarSpec> terms = new ArrayList<>();
        terms.add(calTerm(calendar.getCalTerm()));
        for (Object more : calendar.getMoreCalTerm()) {
            terms.add(calTerm(((AMoreCalTerm) more).getCalTerm()));
        }
        return RuleCatalog.calendar(terms);
    }

    private static CalendarSpec calTerm(PCalTerm node) {
        if (node instanceof ANamedCalTerm named) {
            return new RuleCatalog.Named(named.getKey().getText());
        }
        if (node instanceof ANestedCalTerm nested) {
            return calendar(nested.getCalendar());
        }
        AScopeCalTerm scope = (AScopeCalTerm) node;
        if (!(scope.getIgnore() instanceof AIgnore ignore)) {
            return new RuleCatalog.Scope(scope.getScope().getText(), 0, Set.of());
        }
        Set<String> unless = new LinkedHashSet<>();
        if (ignore.getUnless() instanceof AUnless list) {
            unless.add(list.getCcy().getText());
            for (Object more : list.getMoreCcy()) {
                unless.add(((AMoreCcy) more).getCcy().getText());
            }
        }
        return new RuleCatalog.Scope(scope.getScope().getText(), number(ignore.getNumber()), unless);
    }

    private static CalendarSpec on(POn node) {
        if (node == null) {
            return null;
        }
        if (node instanceof AScopeOn scope) {
            return new RuleCatalog.Scope(scope.getScope().getText(), 0, Set.of());
        }
        if (node instanceof ANamedOn named) {
            return new RuleCatalog.Named(named.getKey().getText());
        }
        return calendar(((ANestedOn) node).getCalendar());
    }

    // ================= expressions =================

    private Expr item(PItem node) {
        if (node instanceof APlainItem plain) {
            return item(expr(plain.getExpr()), plain.getCombine(), plain.getIfClause());
        }
        APick pick;
        Expr value;
        if (node instanceof APickedSeriesItem picked) {
            pick = (APick) picked.getPick();
            value = item(RuleCatalog.shifted(series(picked.getSeries()), moves(picked.getShift())), picked.getCombine(),
                    picked.getIfClause());
        } else {
            APickedEventItem picked = (APickedEventItem) node;
            pick = (APick) picked.getPick();
            value = item(RuleCatalog.shifted(ScheduleSemantics.event(event(picked.getEvent())), moves(picked.getShift())),
                    picked.getCombine(), picked.getIfClause());
        }
        return ScheduleSemantics.pick(ScheduleSemantics.ordinal(pick.getOrdinal().getText()), pick.getLast() != null, value);
    }

    /** A &amp; B includes the dates of B, A \ B excludes them; the shifts after it apply to the result; then IF */
    private Expr item(Expr start, List<?> combines, PIfClause clause) {
        Expr value = start;
        for (Object combine : combines) {
            if (combine instanceof AIncludeCombine include) {
                value = RuleCatalog.shifted(ScheduleSemantics.combined(value, true, operand(include.getOperand())),
                        moves(include.getShift()));
            } else {
                AExcludeCombine exclude = (AExcludeCombine) combine;
                value = RuleCatalog.shifted(ScheduleSemantics.combined(value, false, operand(exclude.getOperand())),
                        moves(exclude.getShift()));
            }
        }
        IfClause ifClause = null;
        if (clause instanceof AIfClause given) {
            ifClause = new IfClause(condition(given.getCondition()), moves(given.getShift()));
        }
        return RuleCatalog.ruleItem(value, ifClause);
    }

    private List<Expr> operand(POperand node) {
        if (node instanceof ASingleOperand single) {
            return List.of(date(single.getDate()));
        }
        ASetOperand set = (ASetOperand) node;
        List<Expr> exprs = new ArrayList<>();
        exprs.add(expr(set.getExpr()));
        for (Object more : set.getMoreExpr()) {
            exprs.add(expr(((AMoreExpr) more).getExpr()));
        }
        return exprs;
    }

    private Expr series(PSeries node) {
        ASeries series = (ASeries) node;
        TemporalAmount step = series.getStep() == null ? null : period(((AStep) series.getStep()).getPeriod());
        return ScheduleSemantics.series(endpoint(series.getFirst()), step,
                series.getLast() == null ? null : endpoint(series.getLast()),
                series.getStartBracket() instanceof AOpenStartBracket, series.getEndBracket() instanceof AOpenEndBracket);
    }

    private Expr endpoint(PEndpoint node) {
        AEndpoint endpoint = (AEndpoint) node;
        return RuleCatalog.shifted(singleDate(endpoint.getSingleDate()), moves(endpoint.getShift()));
    }

    private Expr expr(PExpr node) {
        AExpr expr = (AExpr) node;
        return RuleCatalog.shifted(date(expr.getDate()), moves(expr.getShift()));
    }

    private List<Move> moves(List<?> shifts) {
        List<Move> moves = new ArrayList<>();
        for (Object shift : shifts) {
            moves.add(move((PShift) shift));
        }
        return moves;
    }

    private static String text(Token token) {
        return token == null ? null : token.getText();
    }

    private static int count(TOrdinal ordinal) {
        return ordinal == null ? 1 : ScheduleSemantics.ordinal(ordinal.getText());
    }

    private Move move(PShift node) {
        if (node instanceof APlusShift plus) {
            return RuleCatalog.plus(period(plus.getPeriod()), 1, plus.getEndend() != null, on(plus.getOn()));
        }
        if (node instanceof AMinusShift minus) {
            return RuleCatalog.plus(period(minus.getPeriod()), -1, minus.getEndend() != null, on(minus.getOn()));
        }
        if (node instanceof AImmShift) {
            return RuleCatalog.nextImm();
        }
        if (node instanceof ASpotShift spot) {
            return RuleCatalog.spot(text(spot.getPair()), on(spot.getOn()));
        }
        if (node instanceof AFixShift fix) {
            return RuleCatalog.fix(text(fix.getPair()));
        }
        if (node instanceof ARollShift roll) {
            return RuleCatalog.rollShift(roll.getRoll().getText(), on(roll.getOn()));
        }
        if (node instanceof ANextShift next) {
            return RuleCatalog.next(count(next.getOrdinal()), anchor(next.getAnchor()));
        }
        if (node instanceof APrevShift prev) {
            return RuleCatalog.previous(count(prev.getOrdinal()), anchor(prev.getAnchor()));
        }
        if (node instanceof ANearestShift nearest) {
            return RuleCatalog.nearestShift(anchor(nearest.getAnchor()));
        }
        if (node instanceof ANearestRollShift nearest) {
            return RuleCatalog.rollShift("NEAREST", on(nearest.getOn()));
        }
        if (node instanceof ASelectShift select) {
            PCount count = select.getCount();
            int n = count instanceof AFromStartCount start ? ScheduleSemantics.ordinal(start.getOrdinal().getText())
                    : count instanceof AFromEndCount end ? ScheduleSemantics.ordinal(end.getOrdinal().getText()) : 1;
            return RuleCatalog.select(n, !(count instanceof AFromStartCount), select(select.getSelect()),
                    select.getPeriodWord().getText());
        }
        if (node instanceof ADatesInYearShift dates) {
            List<Annual> annuals = new ArrayList<>();
            annuals.add(annual(dates.getAnnual()));
            for (Object more : dates.getMoreAnnual()) {
                annuals.add(annual(((AMoreAnnual) more).getAnnual()));
            }
            return RuleCatalog.datesInYear(annuals);
        }
        if (node instanceof APostponeShift postpone) {
            return new RuleCatalog.Postpone(postpone.getRoll().getText(), on(postpone.getOn()), period(postpone.getPeriod()),
                    postpone.getCumulative() != null);
        }
        AObservanceShift observance = (AObservanceShift) node;
        List<Map.Entry<Set<String>, Move>> entries = new ArrayList<>();
        entries.add(observe(observance.getObserve()));
        for (Object more : observance.getMoreObserve()) {
            entries.add(observe(((AMoreObserve) more).getObserve()));
        }
        return RuleCatalog.observance(entries);
    }

    private static Set<String> weekdays(List<?> tokens) {
        Set<String> weekdays = new LinkedHashSet<>();
        for (Object token : tokens) {
            weekdays.add(((Token) token).getText());
        }
        return weekdays;
    }

    private Map.Entry<Set<String>, Move> observe(PObserve node) {
        if (node instanceof APlusObserve plus) {
            return RuleCatalog.observePlus(weekdays(plus.getWeekday()), period(plus.getPeriod()), 1);
        }
        if (node instanceof AMinusObserve minus) {
            return RuleCatalog.observePlus(weekdays(minus.getWeekday()), period(minus.getPeriod()), -1);
        }
        if (node instanceof ANextObserve next) {
            return RuleCatalog.observeAnchor(weekdays(next.getWeekday()), anchor(next.getAnchor()), 1);
        }
        if (node instanceof APrevObserve prev) {
            return RuleCatalog.observeAnchor(weekdays(prev.getWeekday()), anchor(prev.getAnchor()), -1);
        }
        if (node instanceof ANearestObserve nearest) {
            return RuleCatalog.observeNearest(weekdays(nearest.getWeekday()), anchor(nearest.getAnchor()));
        }
        ARollObserve roll = (ARollObserve) node;
        return RuleCatalog.observeRoll(weekdays(roll.getWeekday()), roll.getRoll().getText(), on(roll.getOn()));
    }

    // ================= anchors =================

    private Anchor select(PSelect node) {
        if (node instanceof ASingleSelect single) {
            return chosen(single.getChosen());
        }
        ASetSelect set = (ASetSelect) node;
        return targets(set.getTarget(), set.getMoreTarget());
    }

    private Anchor anchor(PAnchor node) {
        if (node instanceof ASingleAnchor single) {
            return target(single.getTarget());
        }
        ASetAnchor set = (ASetAnchor) node;
        return targets(set.getTarget(), set.getMoreTarget());
    }

    private Anchor targets(PTarget first, List<?> more) {
        List<Anchor> anchors = new ArrayList<>();
        anchors.add(target(first));
        for (Object next : more) {
            anchors.add(target(((AMoreTarget) next).getTarget()));
        }
        return ScheduleSemantics.anyOf(anchors);
    }

    private Anchor target(PTarget node) {
        if (node instanceof AChosenTarget chosen) {
            return chosen(chosen.getChosen());
        }
        ALastTarget last = (ALastTarget) node;
        return ScheduleSemantics.annual(ScheduleSemantics.lastWeekday(last.getWeekday().getText(), last.getMonth().getText()));
    }

    private Anchor chosen(PChosen node) {
        if (node instanceof AWeekdayChosen weekday) {
            return ScheduleSemantics.weekday(weekday.getWeekday().getText());
        }
        if (node instanceof ABdChosen bd) {
            return RuleCatalog.businessDay(on(bd.getOn()));
        }
        if (node instanceof AFdChosen) {
            return ScheduleSemantics.FIXING_DAY;
        }
        if (node instanceof AImmChosen) {
            return ScheduleSemantics.IMM;
        }
        if (node instanceof AAnnualChosen annual) {
            return ScheduleSemantics.annual(fixedAnnual(annual.getFixedAnnual()));
        }
        if (node instanceof AEventChosen event) {
            return ScheduleSemantics.eventAnchor(event(event.getEvent()));
        }
        if (node instanceof AHolidayChosen holiday) {
            return RuleCatalog.holiday(on(holiday.getOn()));
        }
        return RuleCatalog.nonholiday(on(((ANonholidayChosen) node).getOn()));
    }

    // ================= dates =================

    private Expr date(PDate node) {
        if (node instanceof ASingleDate single) {
            return singleDate(single.getSingleDate());
        }
        return series(((ASeriesDate) node).getSeries());
    }

    private Expr singleDate(PSingleDate node) {
        if (node instanceof ABaseSingleDate) {
            return ScheduleSemantics.BASE;
        }
        if (node instanceof ATomSingleDate) {
            return ScheduleSemantics.plus(ScheduleSemantics.BASE, ScheduleSemantics.period(1, "B"));
        }
        if (node instanceof ASpotSingleDate spot) {
            return RuleCatalog.spotDate(text(spot.getPair()), on(spot.getOn()));
        }
        if (node instanceof AAnnualSingleDate annual) {
            return ScheduleSemantics.inYear(annual(annual.getAnnual()));
        }
        if (node instanceof AEventSingleDate event) {
            return ScheduleSemantics.event(event(event.getEvent()));
        }
        if (node instanceof AIsoSingleDate iso) {
            return ScheduleSemantics.literal(LocalDate.of(number(iso.getYear()), number(iso.getMonth()), number(iso.getDay())));
        }
        if (node instanceof AGermanSingleDate german) {
            return ScheduleSemantics.literal(LocalDate.of(number(german.getYear()), number(german.getMonth()), number(german.getDay())));
        }
        AUsSingleDate us = (AUsSingleDate) node;
        return ScheduleSemantics.literal(LocalDate.of(number(us.getYear()), number(us.getMonth()), number(us.getDay())));
    }

    private Annual annual(PAnnual node) {
        if (node instanceof AFixedAnnual fixed) {
            return fixedAnnual(fixed.getFixedAnnual());
        }
        ALastAnnual last = (ALastAnnual) node;
        return ScheduleSemantics.lastWeekday(last.getWeekday().getText(), last.getMonth().getText());
    }

    private Annual fixedAnnual(PFixedAnnual node) {
        if (node instanceof AMonthDayFixedAnnual monthDay) {
            String method = null;
            if (monthDay.getHijriMethod() instanceof AMethodHijriMethod given) {
                method = given.getMethod().getText();
            } else if (monthDay.getHijriMethod() instanceof AFollowHijriMethod) {
                method = "FOLLOW";
            }
            if (method != null && !method.equals("UMMALQURA")) {
                estimated = true;
            }
            return RuleCatalog.monthDay(number(monthDay.getNumber()), monthDay.getMonth().getText(), method);
        }
        if (node instanceof ANthWeekdayFixedAnnual nth) {
            return ScheduleSemantics.nthWeekday(number(nth.getNumber()), nth.getWeekday().getText(), nth.getMonth().getText());
        }
        if (node instanceof AEasterFixedAnnual) {
            return ScheduleSemantics.EASTER;
        }
        if (node instanceof AOrthodoxEasterFixedAnnual) {
            return RuleCatalog.ORTHODOX_EASTER;
        }
        if (node instanceof ASeasonFixedAnnual season) {
            return RuleCatalog.season(season.getSeason().getText(), season.getMonth().getText(), zone(season.getTimeZone()));
        }
        if (node instanceof ASolarTermFixedAnnual term) {
            return RuleCatalog.solarTerm(number(term.getNumber()), zone(term.getTimeZone()));
        }
        return RuleCatalog.hindu(((AHinduFixedAnnual) node).getKey().getText());
    }

    private static String zone(PTimeZone node) {
        if (node instanceof AOffsetTimeZone offset) {
            return offset.getZone().getText();
        }
        return node == null ? null : unquote(((ANameTimeZone) node).getString());
    }

    private static TemporalAmount period(PPeriod node) {
        APeriod period = (APeriod) node;
        return ScheduleSemantics.period(number(period.getNumber()), period.getUnit().getText());
    }

    // ================= conditions =================

    private Condition condition(PCondition node) {
        ACondition condition = (ACondition) node;
        List<Condition> parts = new ArrayList<>();
        parts.add(condAnd(condition.getCondAnd()));
        for (Object more : condition.getMoreCondAnd()) {
            parts.add(condAnd(((AMoreCondAnd) more).getCondAnd()));
        }
        return parts.size() == 1 ? parts.get(0) : RuleCatalog.anyCondition(parts);
    }

    private Condition condAnd(PCondAnd node) {
        ACondAnd condition = (ACondAnd) node;
        List<Condition> parts = new ArrayList<>();
        parts.add(condNot(condition.getCondNot()));
        for (Object more : condition.getMoreCondNot()) {
            parts.add(condNot(((AMoreCondNot) more).getCondNot()));
        }
        return parts.size() == 1 ? parts.get(0) : RuleCatalog.allConditions(parts);
    }

    private Condition condNot(PCondNot node) {
        ACondNot condition = (ACondNot) node;
        Condition atom = condAtom(condition.getCondAtom());
        return condition.getNot() != null ? RuleCatalog.notCondition(atom) : atom;
    }

    private Condition condAtom(PCondAtom node) {
        if (node instanceof AOnWeekdayCondAtom weekday) {
            return RuleCatalog.onWeekday(weekdays(weekday.getWeekdaySet()));
        }
        if (node instanceof AOnDayCondAtom day) {
            return RuleCatalog.onDay(number(day.getFirst()), number(day.getLast()), text(day.getMonth()));
        }
        if (node instanceof AIsWeekdayCondAtom is) {
            return RuleCatalog.isWeekday(subject(is.getSubject()), weekdays(is.getWeekdaySet()));
        }
        if (node instanceof ADateIsHolidayCondAtom is) {
            return RuleCatalog.dateIsHoliday(subject(is.getSubject()), on(is.getOn()));
        }
        if (node instanceof AIsHolidayCondAtom holiday) {
            return RuleCatalog.holidayCondition(on(holiday.getOn()));
        }
        if (node instanceof ABetweenHolidaysCondAtom between) {
            return RuleCatalog.betweenHolidays(on(between.getOn()));
        }
        if (node instanceof AEveryCondAtom every) {
            return RuleCatalog.every(period(every.getPeriod()), number(every.getNumber()));
        }
        return condition(((ANestedCondAtom) node).getCondition());
    }

    private Expr subject(PSubject node) {
        ASubject subject = (ASubject) node;
        List<Move> moves = new ArrayList<>();
        for (Object shift : subject.getSubjectShift()) {
            if (shift instanceof APlusSubjectShift plus) {
                moves.add(RuleCatalog.plus(period(plus.getPeriod()), 1, false, null));
            } else {
                moves.add(RuleCatalog.plus(period(((AMinusSubjectShift) shift).getPeriod()), -1, false, null));
            }
        }
        return RuleCatalog.shifted(singleDate(subject.getSingleDate()), moves);
    }

    private static Set<String> weekdays(PWeekdaySet node) {
        if (node instanceof AListWeekdaySet list) {
            return weekdays(list.getWeekday());
        }
        ASetWeekdaySet set = (ASetWeekdaySet) node;
        Set<String> weekdays = new LinkedHashSet<>();
        weekdays.add(set.getWeekday().getText());
        for (Object more : set.getMoreWeekday()) {
            weekdays.add(((AMoreWeekday) more).getWeekday().getText());
        }
        return weekdays;
    }
}
