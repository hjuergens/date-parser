package io.github.hjuergens.compiled;

import io.github.hjuergens.time.DateScheduleParser.ExprContext;
import io.github.hjuergens.time.DateScheduleParser.ItemContext;
import io.github.hjuergens.time.DateScheduleParser.MoveContext;
import io.github.hjuergens.time.DateScheduleParser.ScheduleContext;
import io.github.hjuergens.time.DateScheduleParser.SingleDateContext;
import io.github.hjuergens.time.ScheduleSemantics;
import org.antlr.v4.runtime.Token;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * The parse tree of a schedule to the bytecode of one class that implements {@code ScheduleSemantics.Rule}.
 *
 * <p>The actions of the grammar have already built every value the evaluation needs (periods, anchors, annual dates);
 * they become constants of the class, passed to its constructor. An item with one date (a date and its moves, optionally
 * with {@code IF}) is compiled to straight-line code on {@code LocalDate} that calls the date-level helpers of
 * {@code ScheduleSemantics}. An item with several values (a series, an event, {@code &} or {@code \}, a pick, dates in
 * year) binds them one after the other; it stays the closure rule the actions built, a constant the code calls.
 */
final class RuleCompiler implements Opcodes {

    private static final String CLASS = "io/github/hjuergens/compiled/CompiledRule";
    private static final String SEMANTICS = "io/github/hjuergens/time/ScheduleSemantics";
    private static final String RULE = SEMANTICS + "$Rule";
    private static final String CONTEXT = "L" + SEMANTICS + "$Context;";
    private static final String DATE = "Ljava/time/LocalDate;";
    private static final String MARKET = "L" + SEMANTICS + "$Market;";
    private static final String AMOUNT = "Ljava/time/temporal/TemporalAmount;";
    private static final String ANCHOR = SEMANTICS + "$Anchor";

    // locals of evaluate(Context)
    private static final int CTX = 1, LIST = 2, VALUE = 3;

    private final List<Object> constants = new ArrayList<>();
    private final List<String> descriptors = new ArrayList<>();
    private MethodVisitor mv;

    private RuleCompiler() {
    }

    static ScheduleSemantics.Rule compile(ScheduleContext schedule) {
        return new RuleCompiler().define(schedule);
    }

    private ScheduleSemantics.Rule define(ScheduleContext schedule) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";   // only reached for unequal types, which no frame of the class merges
            }
        };
        cw.visit(V17, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, CLASS, null, "java/lang/Object", new String[]{RULE});

        mv = cw.visitMethod(ACC_PUBLIC, "evaluate", "(" + CONTEXT + ")Ljava/util/List;", null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, "java/util/ArrayList");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        mv.visitVarInsn(ASTORE, LIST);
        for (ItemContext item : schedule.items().e) {
            if (isSingle(item)) {
                single(item);
            } else {
                // the dates of the item: list(item) of ScheduleSemantics, which binds its values one after the other
                mv.visitVarInsn(ALOAD, LIST);
                constant(ScheduleSemantics.list(List.of(item.value)), "L" + RULE + ";");
                mv.visitVarInsn(ALOAD, CTX);
                mv.visitMethodInsn(INVOKEINTERFACE, RULE, "evaluate", "(" + CONTEXT + ")Ljava/util/List;", true);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "addAll", "(Ljava/util/Collection;)Z", true);
                mv.visitInsn(POP);
            }
        }
        mv.visitVarInsn(ALOAD, LIST);
        mv.visitMethodInsn(INVOKESTATIC, "java/util/List", "copyOf", "(Ljava/util/Collection;)Ljava/util/List;", true);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // one final field per constant, set by the constructor from an Object[]
        MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "([Ljava/lang/Object;)V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        for (int i = 0; i < constants.size(); i++) {
            String descriptor = descriptors.get(i);
            cw.visitField(ACC_PRIVATE | ACC_FINAL, "c" + i, descriptor, null, null).visitEnd();
            init.visitVarInsn(ALOAD, 0);
            init.visitVarInsn(ALOAD, 1);
            init.visitLdcInsn(i);
            init.visitInsn(AALOAD);
            init.visitTypeInsn(CHECKCAST, descriptor.substring(1, descriptor.length() - 1));
            init.visitFieldInsn(PUTFIELD, CLASS, "c" + i, descriptor);
        }
        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        cw.visitEnd();

        try {
            return (ScheduleSemantics.Rule) new RuleClassLoader().define(CLASS.replace('/', '.'), cw.toByteArray())
                    .getConstructor(Object[].class).newInstance((Object) constants.toArray());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot load the compiled rule", e);
        }
    }

    /** an item with one date: no pick, no &amp; or \, a single date (not an event) and no dates in year among its moves */
    private static boolean isSingle(ItemContext item) {
        if (item.o != null || !item.operand().isEmpty() || !item.move().isEmpty() || item.expr() == null) {
            return false;
        }
        SingleDateContext date = item.expr().date().singleDate();
        return date != null && date.EVENT() == null
                && item.expr().move().stream().noneMatch(move -> move.getChild(1) != null && move.getChild(1).getText().equals("{"));
    }

    /** the date of the item into VALUE, then added to the list unless IF leaves it out */
    private void single(ItemContext item) {
        ExprContext expr = item.expr();
        singleDate(expr.date().singleDate());
        mv.visitVarInsn(ASTORE, VALUE);
        for (MoveContext move : expr.move()) {
            move(move);
            mv.visitVarInsn(ASTORE, VALUE);
        }
        Label skip = new Label();
        if (!item.w.isEmpty()) {
            int days = 0;   // bit k: the weekday with value k + 1
            for (Token weekday : item.w) {
                days |= 1 << (List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").indexOf(weekday.getText()));
            }
            mv.visitLdcInsn(days);
            mv.visitVarInsn(ALOAD, VALUE);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "getDayOfWeek", "()Ljava/time/DayOfWeek;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/time/DayOfWeek", "getValue", "()I", false);
            mv.visitInsn(ICONST_1);
            mv.visitInsn(ISUB);
            mv.visitInsn(ISHR);
            mv.visitInsn(ICONST_1);
            mv.visitInsn(IAND);
            mv.visitJumpInsn(IFEQ, skip);
        }
        mv.visitVarInsn(ALOAD, LIST);
        mv.visitVarInsn(ALOAD, VALUE);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
        mv.visitInsn(POP);
        mv.visitLabel(skip);
    }

    /** T, TOM, SPOT [PAIR], an annual date or a literal onto the stack */
    private void singleDate(SingleDateContext date) {
        String text = date.getStart().getText();
        if (date.spot() != null) {
            market();
            base();
            pair(date.spot().pair);
            mv.visitMethodInsn(INVOKEINTERFACE, SEMANTICS + "$Market", "spot", "(" + DATE + "Ljava/lang/String;)" + DATE, true);
        } else if (date.annual() != null) {
            constant(date.annual().value, "L" + SEMANTICS + "$Annual;");
            base();
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", "getYear", "()I", false);
            mv.visitMethodInsn(INVOKEINTERFACE, SEMANTICS + "$Annual", "in", "(I)" + DATE, true);
        } else if (text.equals("T")) {
            base();
        } else if (text.equals("TOM")) {
            base();
            plus(ScheduleSemantics.period(1, "B"), 1);
        } else {
            LocalDate literal = date.value.at(null);   // a literal doesn't need a context
            mv.visitLdcInsn(literal.getYear());
            mv.visitLdcInsn(literal.getMonthValue());
            mv.visitLdcInsn(literal.getDayOfMonth());
            mv.visitMethodInsn(INVOKESTATIC, "java/time/LocalDate", "of", "(III)" + DATE, false);
        }
    }

    /** the date in VALUE moved, onto the stack */
    private void move(MoveContext move) {
        String first = move.getChild(0).getText();
        if (move.observance() != null) {
            mv.visitVarInsn(ALOAD, VALUE);
            constant(List.copyOf(move.observance().shifts), "Ljava/util/List;");
            mv.visitVarInsn(ALOAD, CTX);
            mv.visitMethodInsn(INVOKESTATIC, SEMANTICS, "observe", "(" + DATE + "Ljava/util/List;" + CONTEXT + ")" + DATE, false);
            return;
        }
        if (!first.equals(">")) {   // + or - a period
            mv.visitVarInsn(ALOAD, VALUE);
            plus(move.period().value, first.equals("+") ? 1 : -1);
            return;
        }
        String second = move.getChild(1).getText();
        if (move.spot() != null) {
            market();
            mv.visitVarInsn(ALOAD, VALUE);
            pair(move.spot().pair);
            mv.visitMethodInsn(INVOKEINTERFACE, SEMANTICS + "$Market", "spot", "(" + DATE + "Ljava/lang/String;)" + DATE, true);
        } else if (second.equals("IMM")) {
            mv.visitVarInsn(ALOAD, VALUE);
            mv.visitMethodInsn(INVOKESTATIC, SEMANTICS, "nextImm", "(" + DATE + ")" + DATE, false);
        } else if (second.equals("FIX")) {
            mv.visitVarInsn(ALOAD, VALUE);
            mv.visitLdcInsn(-ScheduleSemantics.spotLag(move.p == null ? null : move.p.getText()));
            context("fixing");
            mv.visitMethodInsn(INVOKESTATIC, SEMANTICS, "plusBusinessDays", "(" + DATE + "I" + MARKET + ")" + DATE, false);
        } else if (move.ROLL() != null) {
            mv.visitVarInsn(ALOAD, VALUE);
            mv.visitLdcInsn(move.ROLL().getText());
            market();
            mv.visitMethodInsn(INVOKESTATIC, SEMANTICS, "roll", "(" + DATE + "Ljava/lang/String;" + MARKET + ")" + DATE, false);
        } else if (second.equals("NEXT") || second.equals("PREV")) {
            constant(ScheduleSemantics.times(move.ordinal().n, move.anchor().value), "L" + ANCHOR + ";");
            mv.visitVarInsn(ALOAD, VALUE);
            mv.visitInsn(second.equals("NEXT") ? ICONST_1 : ICONST_M1);
            mv.visitVarInsn(ALOAD, CTX);
            mv.visitMethodInsn(INVOKEINTERFACE, ANCHOR, "from", "(" + DATE + "I" + CONTEXT + ")" + DATE, true);
        } else {   // > count select IN PERIOD
            mv.visitVarInsn(ALOAD, VALUE);
            mv.visitLdcInsn(move.count().n);
            mv.visitInsn(move.count().fromEnd ? ICONST_1 : ICONST_0);
            constant(move.select().value, "L" + ANCHOR + ";");
            mv.visitLdcInsn(move.PERIOD().getText());
            mv.visitVarInsn(ALOAD, CTX);
            mv.visitMethodInsn(INVOKESTATIC, SEMANTICS, "select", "(" + DATE + "IZL" + ANCHOR + ";Ljava/lang/String;" + CONTEXT + ")" + DATE, false);
        }
    }

    /** the date on the stack plus (sign 1) or minus (-1) the amount: a Period directly, business and fixing days by the calendars */
    private void plus(java.time.temporal.TemporalAmount amount, int sign) {
        constant(amount, AMOUNT);
        if (amount instanceof Period) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/time/LocalDate", sign > 0 ? "plus" : "minus", "(" + AMOUNT + ")" + DATE, false);
            return;
        }
        mv.visitInsn(sign > 0 ? ICONST_1 : ICONST_M1);
        market();
        context("fixing");
        mv.visitMethodInsn(INVOKESTATIC, SEMANTICS, "plus", "(" + DATE + AMOUNT + "I" + MARKET + MARKET + ")" + DATE, false);
    }

    /** T: ScheduleSemantics.BASE, which is an error without base date */
    private void base() {
        mv.visitFieldInsn(GETSTATIC, SEMANTICS, "BASE", "L" + SEMANTICS + "$Expr;");
        mv.visitVarInsn(ALOAD, CTX);
        mv.visitMethodInsn(INVOKEINTERFACE, SEMANTICS + "$Expr", "at", "(" + CONTEXT + ")" + DATE, true);
    }

    private void market() {
        context("market");
    }

    private void context(String calendar) {
        mv.visitVarInsn(ALOAD, CTX);
        mv.visitMethodInsn(INVOKEVIRTUAL, SEMANTICS + "$Context", calendar, "()" + MARKET, false);
    }

    private void pair(String pair) {
        if (pair == null) {
            mv.visitInsn(ACONST_NULL);
        } else {
            mv.visitLdcInsn(pair);
        }
    }

    /** a value of the parse onto the stack, as a field of the class */
    private void constant(Object value, String descriptor) {
        constants.add(value);
        descriptors.add(descriptor);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, CLASS, "c" + (constants.size() - 1), descriptor);
    }
}
