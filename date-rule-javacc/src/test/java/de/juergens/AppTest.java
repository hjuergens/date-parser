package de.juergens;

import org.junit.AssumptionViolatedException;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Stopwatch;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.collection.IsMapContaining.hasEntry;

/**
 * this example shows the use of [[org.hamcrest]]
 */
@Ignore
@RunWith(JUnit4.class)
public class AppTest
{
    private static final Logger logger = Logger.getLogger("");

    private static void logInfo(Description description, String status, long nanos) {
        String testName = description.getMethodName();
        logger.info(String.format("Test %s %s, spent %d microseconds",
                testName, status, TimeUnit.NANOSECONDS.toMicros(nanos)));
    }

    @Rule
    public Stopwatch stopwatch = new Stopwatch() {
        @Override
        protected void succeeded(long nanos, Description description) {
            logInfo(description, "succeeded", nanos);
        }

        @Override
        protected void failed(long nanos, Throwable e, Description description) {
            logInfo(description, "failed", nanos);
        }

        @Override
        protected void skipped(long nanos, AssumptionViolatedException e, Description description) {
            logInfo(description, "skipped", nanos);
        }

        @Override
        protected void finished(long nanos, Description description) {
            logInfo(description, "finished", nanos);
        }
    };

    @Test
    public void containsIn() {
        assertThat(
                new CartoonCharacterEmailLookupService().getResults("looney"),
                allOf(containsInAnyOrder(
                        allOf(instanceOf(Map.class), hasEntry("id", "56"), hasEntry("email", "roadrunner@fast.org")),
                        allOf(instanceOf(Map.class), hasEntry("id", "76"), hasEntry("email", "wiley@acme.com"))
                ))
        );
    }

    @Test
    public void notEmpty() {
        assertThat(
                new CartoonCharacterEmailLookupService().getResults("looney"),
                allOf(not(empty())
        ));
    }

    private class CartoonCharacterEmailLookupService {
        public java.util.Collection<Map<String,String>> getResults(String looney) {
            List list = new ArrayList<Map<String,String>>();
            {
                Map<String, String> map = new HashMap<String, String>(2);
                map.put("id", "56");
                map.put("email", "roadrunner@fast.org");
                list.add(map);
            }
            {
                Map<String, String> map = new HashMap<String, String>(2);
                map.put("id", "76");
                map.put("email", "wiley@acme.com");
                list.add(map);
            }
            return list;
        }
    }
}
