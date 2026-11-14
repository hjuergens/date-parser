package io.github.hjuergens.util;

public class Ring {
    final private int m;

    public Ring(int m) {
        this.m = m;
    }

    public int minus(int lhs, int rhs) {
        if(lhs <= 0 || lhs > m) throw new IllegalArgumentException("Left hand side out of range.");
        if(rhs <= 0 || rhs > m) throw new IllegalArgumentException("Right hand side out of range.");
        return lhs-rhs < 0 ? lhs-rhs+m : lhs-rhs;
    }

    public int plus(int lhs, int amount) {
        if(lhs <= 0 || lhs > m) throw new IllegalArgumentException("Left hand side out of range.");
        //if(rhs <= 0 || rhs > m) throw new IllegalArgumentException("Right hand side out of range.");

        return Math.floorMod(lhs+amount-1, m)+1;
    }
}
