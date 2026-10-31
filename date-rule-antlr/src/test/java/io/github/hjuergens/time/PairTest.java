package io.github.hjuergens.time;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public class PairTest {

    @Test
    public void accessors() {
        Pair<Integer, Boolean> pair = new Pair<>(3, true);
        assertEquals(pair.getFirst(), Integer.valueOf(3));
        assertEquals(pair.getSecond(), Boolean.TRUE);

        pair.setFirst(-1);
        pair.setSecond(false);
        assertEquals(pair.getFirst(), Integer.valueOf(-1));
        assertEquals(pair.getSecond(), Boolean.FALSE);
    }

    @Test
    public void equalsAndHashCode() {
        assertEquals(new Pair<>("a", 1), new Pair<>("a", 1));
        assertEquals(new Pair<>("a", 1).hashCode(), new Pair<>("a", 1).hashCode());
        assertEquals(new Pair<>(null, null), new Pair<>(null, null));
        assertEquals(new Pair<>(null, null).hashCode(), 0);

        assertNotEquals(new Pair<>("a", 1), new Pair<>("a", 2));
        assertNotEquals(new Pair<>("a", 1), new Pair<>("b", 1));
        assertNotEquals(new Pair<>("a", 1), new Pair<>(null, 1));
        assertNotEquals(new Pair<>("a", 1), "(a, 1)");
    }

    @Test
    public void toStringShowsBothElements() {
        assertEquals(new Pair<>("a", 1).toString(), "(a, 1)");
    }
}
