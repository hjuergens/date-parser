package io.github.hjuergens.time;

import java.time.LocalDate;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import io.github.hjuergens.time.RuleCatalog.CalendarSpec;
import io.github.hjuergens.time.RuleCatalog.Citation;
import io.github.hjuergens.time.RuleCatalog.Condition;
import io.github.hjuergens.time.RuleCatalog.IfClause;
import io.github.hjuergens.time.RuleCatalog.Move;
import io.github.hjuergens.time.RuleCatalog.Named;
import io.github.hjuergens.time.RuleCatalog.Postpone;
import io.github.hjuergens.time.RuleCatalog.Scope;
import io.github.hjuergens.time.ScheduleSemantics.Expr;
import io.github.hjuergens.time.ScheduleSemantics.Rule;

/**
 * Rule files ({@code RuleFile.g4}, the same file as in date-rule-antlr-python) parsed with ANTLR's Java target and
 * evaluated by {@link RuleCatalog}. The visitor builds the expressions with ScheduleSemantics and RuleCatalog, as the
 * visitor of date-rule-antlr-python does with semantics.py and catalog.py; dates are {@link Expr}, shifts {@link Move}.
 */
public final class RuleVisitor extends RuleFileBaseVisitor<Object> {

    private static final BaseErrorListener FAIL = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String msg, RecognitionException e) {
            throw new ParseCancellationException(line + ":" + charPositionInLine + " " + msg, e);
        }
    };

    /**
     * the rule catalog of a rule file
     *
     * @throws ParseCancellationException on a syntax error
     * @throws RuntimeException on an invalid date, overlapping versions, an unknown or a cyclic reference
     */
    public static RuleCatalog parseRules(String text) {
        return parseRules(text, null);
    }

    /** the same with announced dates: key -&gt; the dates an authority published */
    public static RuleCatalog parseRules(String text, Map<String, List<LocalDate>> announcements) {
        List<?> declarations = (List<?>) new RuleVisitor().visit(parser(text).ruleFile());
        return new RuleCatalog(declarations, announcements, RuleVisitor::schedule);
    }

    /** one expression of the rule language */
    public static Rule schedule(String text) {
        return (Rule) new RuleVisitor().visit(parser(text).schedule());
    }

    private static RuleFileParser parser(String text) {
        RuleFileLexer lexer = new RuleFileLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(FAIL);
        RuleFileParser parser = new RuleFileParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(FAIL);
        parser.setErrorHandler(new BailErrorStrategy());
        return parser;
    }

    @SuppressWarnings("unchecked")
    private <T> T value(ParseTree tree) {
        return tree == null ? null : (T) visit(tree);
    }

    private <T> List<T> values(List<? extends ParseTree> trees) {
        List<T> out = new ArrayList<>();
        for (ParseTree tree : trees) {
            out.add(value(tree));
        }
        return out;
    }

    /** the text of a STRING token without its quotes */
    private static String text(Token token) {
        return token == null ? null : token.getText().substring(1, token.getText().length() - 1);
    }

    private static String text(TerminalNode node) {
        return node == null ? null : text(node.getSymbol());
    }

    private static Set<String> weekdays(List<TerminalNode> nodes) {
        Set<String> out = new LinkedHashSet<>();
        nodes.forEach(node -> out.add(node.getText()));
        return out;
    }

    private static int number(TerminalNode node) {
        return Integer.parseInt(node.getText());
    }

    private CalendarSpec on(RuleFileParser.OnContext on) {
        return value(on);
    }

    // ---------- items and conditions ----------

    @Override
    public Object visitSchedule(RuleFileParser.ScheduleContext ctx) {
        return RuleCatalog.exprs(values(ctx.item()));
    }

    @Override
    public Object visitExprs(RuleFileParser.ExprsContext ctx) {
        return RuleCatalog.exprs(values(ctx.item()));
    }

    @Override
    public Object visitItem(RuleFileParser.ItemContext ctx) {
        boolean picked = ctx.ORDINAL() != null;
        Expr expr;
        if (picked) {   // a pick: the item starts with a series or an event
            Expr start = ctx.series() != null ? value(ctx.series()) : ScheduleSemantics.event(ctx.EVENT().getText().substring(1));
            expr = RuleCatalog.shifted(start, values(ctx.shift()));
        } else {
            expr = value(ctx.expr());
        }
        for (RuleFileParser.CombineContext combine : ctx.combine()) {
            List<Expr> operand = value(combine.operand());
            expr = RuleCatalog.shifted(ScheduleSemantics.combined(expr, combine.op.getText().equals("&"), operand),
                    values(combine.shift()));
        }
        expr = RuleCatalog.ruleItem(expr, value(ctx.ifClause()));
        if (picked) {
            expr = ScheduleSemantics.pick(ScheduleSemantics.ordinal(ctx.ORDINAL().getText()), ctx.fromEnd != null, expr);
        }
        return expr;
    }

    @Override
    public Object visitIfClause(RuleFileParser.IfClauseContext ctx) {
        return new IfClause(value(ctx.condition()), values(ctx.shift()));
    }

    @Override
    public Object visitSingleOperand(RuleFileParser.SingleOperandContext ctx) {
        return List.of((Expr) value(ctx.date()));
    }

    @Override
    public Object visitSetOperand(RuleFileParser.SetOperandContext ctx) {
        return values(ctx.expr());
    }

    @Override
    public Object visitSeries(RuleFileParser.SeriesContext ctx) {
        return ScheduleSemantics.series(value(ctx.first), value(ctx.step), value(ctx.last),
                ctx.left.getText().equals("]"), ctx.right.getText().equals("["));
    }

    @Override
    public Object visitEndpoint(RuleFileParser.EndpointContext ctx) {
        return RuleCatalog.shifted(value(ctx.singleDate()), values(ctx.shift()));
    }

    @Override
    public Object visitExpr(RuleFileParser.ExprContext ctx) {
        return RuleCatalog.shifted(value(ctx.date()), values(ctx.shift()));
    }

    @Override
    public Object visitCondition(RuleFileParser.ConditionContext ctx) {
        List<Condition> parts = values(ctx.condAnd());
        return parts.size() == 1 ? parts.get(0) : RuleCatalog.anyCondition(parts);
    }

    @Override
    public Object visitCondAnd(RuleFileParser.CondAndContext ctx) {
        List<Condition> parts = values(ctx.condNot());
        return parts.size() == 1 ? parts.get(0) : RuleCatalog.allConditions(parts);
    }

    @Override
    public Object visitCondNot(RuleFileParser.CondNotContext ctx) {
        Condition part = value(ctx.condAtom());
        return ctx.negate != null ? RuleCatalog.notCondition(part) : part;
    }

    @Override
    public Object visitWeekdaySet(RuleFileParser.WeekdaySetContext ctx) {
        return weekdays(ctx.WEEKDAY());
    }

    @Override
    public Object visitOnWeekday(RuleFileParser.OnWeekdayContext ctx) {
        return RuleCatalog.onWeekday(value(ctx.weekdaySet()));
    }

    @Override
    public Object visitOnDay(RuleFileParser.OnDayContext ctx) {
        return RuleCatalog.onDay(Integer.parseInt(ctx.first.getText()), Integer.parseInt(ctx.last.getText()),
                ctx.MONTH() == null ? null : ctx.MONTH().getText());
    }

    @Override
    public Object visitSubject(RuleFileParser.SubjectContext ctx) {
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < ctx.period().size(); i++) {
            moves.add(RuleCatalog.plus(value(ctx.period(i)), ctx.sign.get(i).getText().equals("+") ? 1 : -1, false, null));
        }
        return RuleCatalog.shifted(value(ctx.singleDate()), moves);
    }

    @Override
    public Object visitIsWeekday(RuleFileParser.IsWeekdayContext ctx) {
        return RuleCatalog.isWeekday(value(ctx.subject()), value(ctx.weekdaySet()));
    }

    @Override
    public Object visitIsHoliday(RuleFileParser.IsHolidayContext ctx) {
        return RuleCatalog.holidayCondition(on(ctx.on()));
    }

    @Override
    public Object visitDateIsHoliday(RuleFileParser.DateIsHolidayContext ctx) {
        return RuleCatalog.dateIsHoliday(value(ctx.subject()), on(ctx.on()));
    }

    @Override
    public Object visitBetweenHolidays(RuleFileParser.BetweenHolidaysContext ctx) {
        return RuleCatalog.betweenHolidays(on(ctx.on()));
    }

    @Override
    public Object visitEvery(RuleFileParser.EveryContext ctx) {
        return RuleCatalog.every(value(ctx.period()), number(ctx.NUM()));
    }

    @Override
    public Object visitNestedCondition(RuleFileParser.NestedConditionContext ctx) {
        return value(ctx.condition());
    }

    // ---------- dates ----------

    @Override
    public Object visitSingle(RuleFileParser.SingleContext ctx) {
        return value(ctx.singleDate());
    }

    @Override
    public Object visitSeriesDate(RuleFileParser.SeriesDateContext ctx) {
        return value(ctx.series());
    }

    @Override
    public Object visitBase(RuleFileParser.BaseContext ctx) {
        return ScheduleSemantics.BASE;
    }

    @Override
    public Object visitTom(RuleFileParser.TomContext ctx) {
        return ScheduleSemantics.plus(ScheduleSemantics.BASE, ScheduleSemantics.period(1, "B"));
    }

    @Override
    public Object visitSpotDate(RuleFileParser.SpotDateContext ctx) {
        return RuleCatalog.spotDate(ctx.PAIR() == null ? null : ctx.PAIR().getText(), on(ctx.on()));
    }

    @Override
    public Object visitIso(RuleFileParser.IsoContext ctx) {
        return ScheduleSemantics.literal(date(ctx.y, ctx.m, ctx.d));
    }

    @Override
    public Object visitGerman(RuleFileParser.GermanContext ctx) {
        return ScheduleSemantics.literal(date(ctx.y, ctx.m, ctx.d));
    }

    @Override
    public Object visitUs(RuleFileParser.UsContext ctx) {
        return ScheduleSemantics.literal(date(ctx.y, ctx.m, ctx.d));
    }

    private static LocalDate date(Token y, Token m, Token d) {
        return LocalDate.of(Integer.parseInt(y.getText()), Integer.parseInt(m.getText()), Integer.parseInt(d.getText()));
    }

    @Override
    public Object visitInYear(RuleFileParser.InYearContext ctx) {
        return ScheduleSemantics.inYear(value(ctx.annual()));
    }

    @Override
    public Object visitEvent(RuleFileParser.EventContext ctx) {
        return ScheduleSemantics.event(ctx.EVENT().getText().substring(1));
    }

    @Override
    public Object visitFixed(RuleFileParser.FixedContext ctx) {
        return value(ctx.fixedAnnual());
    }

    @Override
    public Object visitLastWeekday(RuleFileParser.LastWeekdayContext ctx) {
        return ScheduleSemantics.lastWeekday(ctx.WEEKDAY().getText(), ctx.MONTH().getText());
    }

    @Override
    public Object visitMonthDay(RuleFileParser.MonthDayContext ctx) {
        RuleFileParser.HijriMethodContext method = ctx.hijriMethod();
        return RuleCatalog.monthDay(number(ctx.NUM()), ctx.MONTH().getText(), method == null ? null : method.method.getText());
    }

    @Override
    public Object visitNthWeekday(RuleFileParser.NthWeekdayContext ctx) {
        return ScheduleSemantics.nthWeekday(number(ctx.NUM()), ctx.WEEKDAY().getText(), ctx.MONTH().getText());
    }

    @Override
    public Object visitEaster(RuleFileParser.EasterContext ctx) {
        return ScheduleSemantics.EASTER;
    }

    @Override
    public Object visitOrthodoxEaster(RuleFileParser.OrthodoxEasterContext ctx) {
        return RuleCatalog.ORTHODOX_EASTER;
    }

    @Override
    public Object visitHindu(RuleFileParser.HinduContext ctx) {
        return RuleCatalog.hindu(ctx.KEY().getText());
    }

    /** the zone of an astronomical date: UTC+9, "Asia/Tokyo", or null for UTC */
    private static String zone(RuleFileParser.ZoneContext zone) {
        if (zone == null) {
            return null;
        }
        return zone.STRING() != null ? text(zone.STRING()) : zone.ZONE().getText();
    }

    @Override
    public Object visitSeason(RuleFileParser.SeasonContext ctx) {
        return RuleCatalog.season(ctx.k.getText(), ctx.MONTH().getText(), zone(ctx.zone()));
    }

    @Override
    public Object visitSolarTerm(RuleFileParser.SolarTermContext ctx) {
        return RuleCatalog.solarTerm(number(ctx.NUM()), zone(ctx.zone()));
    }

    @Override
    public Object visitPeriod(RuleFileParser.PeriodContext ctx) {
        return ScheduleSemantics.period(number(ctx.NUM()), ctx.UNIT().getText());
    }

    // ---------- shifts ----------

    @Override
    public Object visitPlus(RuleFileParser.PlusContext ctx) {
        return RuleCatalog.plus(value(ctx.period()), 1, ctx.endend != null, on(ctx.on()));
    }

    @Override
    public Object visitMinus(RuleFileParser.MinusContext ctx) {
        return RuleCatalog.plus(value(ctx.period()), -1, ctx.endend != null, on(ctx.on()));
    }

    @Override
    public Object visitImm(RuleFileParser.ImmContext ctx) {
        return RuleCatalog.nextImm();
    }

    @Override
    public Object visitSpot(RuleFileParser.SpotContext ctx) {
        return RuleCatalog.spot(ctx.PAIR() == null ? null : ctx.PAIR().getText(), on(ctx.on()));
    }

    @Override
    public Object visitFix(RuleFileParser.FixContext ctx) {
        return RuleCatalog.fix(ctx.PAIR() == null ? null : ctx.PAIR().getText());
    }

    @Override
    public Object visitRoll(RuleFileParser.RollContext ctx) {
        return RuleCatalog.rollShift(ctx.ROLL().getText(), on(ctx.on()));
    }

    @Override
    public Object visitNext(RuleFileParser.NextContext ctx) {
        return RuleCatalog.next(count(ctx.ORDINAL()), value(ctx.anchor()));
    }

    @Override
    public Object visitPrev(RuleFileParser.PrevContext ctx) {
        return RuleCatalog.previous(count(ctx.ORDINAL()), value(ctx.anchor()));
    }

    private static int count(TerminalNode ordinal) {
        return ordinal == null ? 1 : ScheduleSemantics.ordinal(ordinal.getText());
    }

    @Override
    public Object visitNearest(RuleFileParser.NearestContext ctx) {
        return RuleCatalog.nearestShift(value(ctx.anchor()));
    }

    @Override
    public Object visitSelectIn(RuleFileParser.SelectInContext ctx) {
        RuleFileParser.CountContext count = ctx.count();
        return RuleCatalog.select(count(count.ORDINAL()), count.last != null, value(ctx.select()), ctx.PERIOD().getText());
    }

    @Override
    public Object visitDatesInYear(RuleFileParser.DatesInYearContext ctx) {
        return RuleCatalog.datesInYear(values(ctx.annual()));
    }

    @Override
    public Object visitPostpone(RuleFileParser.PostponeContext ctx) {
        return new Postpone(ctx.ROLL().getText(), on(ctx.on()), value(ctx.period()), ctx.cumulative != null);
    }

    @Override
    public Object visitObservance(RuleFileParser.ObservanceContext ctx) {
        return RuleCatalog.observance(values(ctx.observe()));
    }

    @Override
    public Object visitObservePlus(RuleFileParser.ObservePlusContext ctx) {
        return RuleCatalog.observePlus(weekdays(ctx.WEEKDAY()), value(ctx.period()), ctx.sign.getText().equals("+") ? 1 : -1);
    }

    @Override
    public Object visitObserveNext(RuleFileParser.ObserveNextContext ctx) {
        return RuleCatalog.observeAnchor(weekdays(ctx.WEEKDAY()), value(ctx.anchor()), 1);
    }

    @Override
    public Object visitObservePrev(RuleFileParser.ObservePrevContext ctx) {
        return RuleCatalog.observeAnchor(weekdays(ctx.WEEKDAY()), value(ctx.anchor()), -1);
    }

    @Override
    public Object visitObserveNearest(RuleFileParser.ObserveNearestContext ctx) {
        return RuleCatalog.observeNearest(weekdays(ctx.WEEKDAY()), value(ctx.anchor()));
    }

    @Override
    public Object visitObserveRoll(RuleFileParser.ObserveRollContext ctx) {
        return RuleCatalog.observeRoll(weekdays(ctx.WEEKDAY()), ctx.ROLL().getText(), on(ctx.on()));
    }

    // ---------- anchors ----------

    @Override
    public Object visitSingleSelect(RuleFileParser.SingleSelectContext ctx) {
        return value(ctx.chosen());
    }

    @Override
    public Object visitAnySelect(RuleFileParser.AnySelectContext ctx) {
        return ScheduleSemantics.anyOf(values(ctx.target()));
    }

    @Override
    public Object visitSingleAnchor(RuleFileParser.SingleAnchorContext ctx) {
        return value(ctx.target());
    }

    @Override
    public Object visitAnyOf(RuleFileParser.AnyOfContext ctx) {
        return ScheduleSemantics.anyOf(values(ctx.target()));
    }

    @Override
    public Object visitChosenTarget(RuleFileParser.ChosenTargetContext ctx) {
        return value(ctx.chosen());
    }

    @Override
    public Object visitLastWeekdayAnchor(RuleFileParser.LastWeekdayAnchorContext ctx) {
        return ScheduleSemantics.annual(ScheduleSemantics.lastWeekday(ctx.WEEKDAY().getText(), ctx.MONTH().getText()));
    }

    @Override
    public Object visitWeekdayAnchor(RuleFileParser.WeekdayAnchorContext ctx) {
        return ScheduleSemantics.weekday(ctx.WEEKDAY().getText());
    }

    @Override
    public Object visitBusinessDayAnchor(RuleFileParser.BusinessDayAnchorContext ctx) {
        return RuleCatalog.businessDay(on(ctx.on()));
    }

    @Override
    public Object visitFixingDayAnchor(RuleFileParser.FixingDayAnchorContext ctx) {
        return ScheduleSemantics.FIXING_DAY;
    }

    @Override
    public Object visitImmAnchor(RuleFileParser.ImmAnchorContext ctx) {
        return ScheduleSemantics.IMM;
    }

    @Override
    public Object visitAnnualAnchor(RuleFileParser.AnnualAnchorContext ctx) {
        return ScheduleSemantics.annual(value(ctx.fixedAnnual()));
    }

    @Override
    public Object visitEventAnchor(RuleFileParser.EventAnchorContext ctx) {
        return ScheduleSemantics.eventAnchor(ctx.EVENT().getText().substring(1));
    }

    @Override
    public Object visitHolidayAnchor(RuleFileParser.HolidayAnchorContext ctx) {
        return RuleCatalog.holiday(on(ctx.on()));
    }

    @Override
    public Object visitNonholidayAnchor(RuleFileParser.NonholidayAnchorContext ctx) {
        return RuleCatalog.nonholiday(on(ctx.on()));
    }

    // ---------- calendars ----------

    @Override
    public Object visitOn(RuleFileParser.OnContext ctx) {
        if (ctx.SCOPE() != null) {
            return new Scope(ctx.SCOPE().getText(), 0, Set.of());
        }
        if (ctx.KEY() != null) {
            return new Named(ctx.KEY().getText());
        }
        return value(ctx.calendar());
    }

    @Override
    public Object visitCalendar(RuleFileParser.CalendarContext ctx) {
        return RuleCatalog.calendar(values(ctx.calTerm()));
    }

    @Override
    public Object visitScopeCalendar(RuleFileParser.ScopeCalendarContext ctx) {
        RuleFileParser.IgnoreContext ignore = ctx.ignore();
        if (ignore == null) {
            return new Scope(ctx.SCOPE().getText(), 0, Set.of());
        }
        return new Scope(ctx.SCOPE().getText(), number(ignore.NUM()), weekdays(ignore.CCY()));
    }

    @Override
    public Object visitNamedCalendar(RuleFileParser.NamedCalendarContext ctx) {
        return new Named(ctx.KEY().getText());
    }

    @Override
    public Object visitNestedCalendar(RuleFileParser.NestedCalendarContext ctx) {
        return value(ctx.calendar());
    }

    // ---------- declarations ----------

    @Override
    public Object visitRuleFile(RuleFileParser.RuleFileContext ctx) {
        return values(ctx.declaration());
    }

    @Override
    public Object visitRuleDecl(RuleFileParser.RuleDeclContext ctx) {
        RuleFileParser.ValidityContext validity = ctx.validity();
        LocalDate validFrom = validity == null ? null : value(validity.from_);
        LocalDate validTo = validity == null ? null : value(validity.to);
        List<Map.Entry<String, Object>> attributes = ctx.attributes() == null ? List.of() : values(ctx.attributes().attribute());
        List<Citation> citations = ctx.provenance() == null ? List.of() : values(ctx.provenance().citation());
        Set<String> references = new LinkedHashSet<>();
        boolean[] estimated = {false};
        walk(ctx, tree -> {
            if (tree instanceof TerminalNode node && node.getSymbol().getType() == RuleFileParser.EVENT) {
                references.add(node.getText().substring(1));
            }
            if (tree instanceof RuleFileParser.HijriMethodContext method && !method.method.getText().equals("UMMALQURA")) {
                estimated[0] = true;
            }
        });
        return RuleCatalog.rule(ctx.key.getText(), ctx.eventClass.getText(), value(ctx.scopes(0)),
                ctx.excluded == null ? List.of() : value(ctx.excluded), validFrom, validTo, value(ctx.body()),
                attributes, citations, references, estimated[0]);
    }

    /** every node below the tree */
    private static void walk(ParseTree tree, java.util.function.Consumer<ParseTree> action) {
        for (int i = 0; i < tree.getChildCount(); i++) {
            ParseTree child = tree.getChild(i);
            action.accept(child);
            if (child instanceof ParserRuleContext) {
                walk(child, action);
            }
        }
    }

    @Override
    public Object visitExprsBody(RuleFileParser.ExprsBodyContext ctx) {
        return value(ctx.exprs());
    }

    @Override
    public Object visitOneOf(RuleFileParser.OneOfContext ctx) {
        List<Map.Entry<String, Rule>> alternatives = new ArrayList<>();
        for (RuleFileParser.AlternativeContext alternative : ctx.alternative()) {
            alternatives.add(Map.entry(alternative.name().getText(), RuleCatalog.exprs(List.of((Expr) value(alternative.item())))));
        }
        return RuleCatalog.oneOf(alternatives);
    }

    @Override
    public Object visitScopes(RuleFileParser.ScopesContext ctx) {
        List<String> scopes = new ArrayList<>();
        ctx.SCOPE().forEach(scope -> scopes.add(scope.getText()));
        return scopes;
    }

    @Override
    public Object visitIsoDate(RuleFileParser.IsoDateContext ctx) {
        return date(ctx.y, ctx.m, ctx.d);
    }

    @Override
    public Object visitWeekendDecl(RuleFileParser.WeekendDeclContext ctx) {
        return RuleCatalog.weekend(ctx.SCOPE().getText(), value(ctx.weekdaySet()), value(ctx.isoDate()));
    }

    @Override
    public Object visitCalendarDecl(RuleFileParser.CalendarDeclContext ctx) {
        return RuleCatalog.calendarDeclaration(ctx.KEY().getText(), value(ctx.calendar()));
    }

    @Override
    public Object visitKindAttribute(RuleFileParser.KindAttributeContext ctx) {
        return Map.entry("kind", (Object) ctx.kind.getText());
    }

    @Override
    public Object visitSession(RuleFileParser.SessionContext ctx) {
        return RuleCatalog.session(value(ctx.opens), value(ctx.closes), text(ctx.STRING()));
    }

    @Override
    public Object visitTime(RuleFileParser.TimeContext ctx) {
        return RuleCatalog.time(number(ctx.NUM(0)), number(ctx.NUM(1)));
    }

    @Override
    public Object visitPrecisionAttribute(RuleFileParser.PrecisionAttributeContext ctx) {
        return Map.entry("precision", (Object) ctx.precision.getText());
    }

    @Override
    public Object visitNote(RuleFileParser.NoteContext ctx) {
        return Map.entry("note", (Object) text(ctx.STRING()));
    }

    @Override
    public Object visitCitation(RuleFileParser.CitationContext ctx) {
        return new Citation(text(ctx.source), text(ctx.article), text(ctx.url), ctx.label.getText());
    }
}
