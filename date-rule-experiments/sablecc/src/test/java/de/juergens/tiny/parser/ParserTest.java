/*
 * Copyright 2015 Hartmut Jürgens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.juergens.tiny.parser;

import de.juergens.tiny.analysis.Analysis;
import de.juergens.tiny.analysis.AnalysisAdapter;
import de.juergens.tiny.lexer.Lexer;
import de.juergens.tiny.lexer.LexerException;
import de.juergens.tiny.node.APlusExpr;
import de.juergens.tiny.node.Node;
import de.juergens.tiny.node.Start;
import de.juergens.tiny.node.Switch;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;

import static org.testng.Assert.*;

public class ParserTest {

    @Test(suiteName = "tiny",timeOut = 500)
    public void testStart() throws ParserException, IOException, LexerException {
        Reader reader = new StringReader("4+(6*8)/3");
        PushbackReader in = new PushbackReader(reader);
        Lexer lexer = new Lexer(in);
        Parser parser = new Parser(lexer);
        Start start = parser.parse();
        assertEquals(start.toString(), "4 + ( 6 * 8 ) / 3  ");
    }

    @Test(suiteName = "tiny",timeOut = 500)
    public void testAnalysis() throws ParserException, IOException, LexerException {
        Reader reader = new StringReader("4+(6*8)/3");
        PushbackReader in = new PushbackReader(reader);
        Lexer lexer = new Lexer(in);
        Parser parser = new Parser(lexer);
        Start start = parser.parse();

        Analysis swt = new AnalysisAdapter(){
            @Override
            public void caseAPlusExpr(APlusExpr node) {
                assertEquals(node.getExpr().toString(), "4 ");
                assertEquals(node.getPlus().toString(), "+ ");
                assertEquals(node.getFactor().toString(), "( 6 * 8 ) / 3 ");
            }
        };
        /*
        swt.setIn(expr, new Object());
        Node outNode = new Start();
        swt.getOut(outNode);
        */
        Node expr = start.getPExpr();
        expr.apply(swt);
    }

}