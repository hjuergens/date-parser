package io.github.hjuergens.time;

import java.util.List;
import java.util.LinkedList;

public class IntegerListVisitor extends ListBaseVisitor<List<Integer>>  {
    @Override
    public List<Integer> visitList(ListParser.ListContext ctx) {
        final int start = Integer.parseInt(ctx.s.getText());
        final int step = Integer.parseInt(ctx.p.getText());
        final int end = Integer.parseInt(ctx.e.getText());
        assert step > 0;
        List<Integer> list = new LinkedList<>();
        for (int j = start; j <= end; j += step) { list.add(j); }
        return list;
    }
}
