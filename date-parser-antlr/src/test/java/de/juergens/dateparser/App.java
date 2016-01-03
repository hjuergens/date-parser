package de.juergens.dateparser;

import java.io.InputStream;

public class App {

    public static void main(String[] args) throws Exception {

        InputStream stream = App.class.getResourceAsStream("/example.field");

        DateRule dateRule = new DateRuleFactory().createDateRule(stream);

        dateRule.play();
    }
}