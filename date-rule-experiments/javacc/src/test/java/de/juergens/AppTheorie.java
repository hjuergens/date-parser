package de.juergens;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

import org.junit.Ignore;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.experimental.theories.suppliers.TestedOn;
import org.junit.runner.RunWith;

/**
 * This example shows the use of Theories
 */
@Ignore
@RunWith(Theories.class)
public class AppTheorie {
    @DataPoint
    public static String GOOD_USERNAME = "optimus";
    @DataPoint
    public static String USERNAME_WITH_SLASH = "optimus/prime";

    @Theory
    public void filenameIncludesUsername(String username) {
        assumeThat(username, not(containsString("/")));
        assertThat(new User(username).configFileName(), containsString(username));
    }

    private class User {
        private String username;

        public User(String username) {
            this.username = username;
        }

        public String configFileName() {
            return username;
        }
    }

    @Theory
    public void multiplyIsInverseOfDivideWithInlineDataPoints(
            @TestedOn(ints={0, 5, 10}) int amount,
            @TestedOn(ints={0, 1, 2})  int m
    ) {
        assumeThat(m, not(0));
        assertThat(
                new Dollar(amount).times(m).divideBy(m).getAmount(),
                is(amount)
        );
    }

    private class Dollar {
        private int amount;

        public Dollar(int amount) {
            this.amount = amount;
        }

        public Dollar times(int m) {
            return new Dollar(m * amount);
        }

        public Dollar divideBy(int m) {
            return new Dollar(amount / m);
        }

        public int getAmount() {
            return amount;
        }
    }

}