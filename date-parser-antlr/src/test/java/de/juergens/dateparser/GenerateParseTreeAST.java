/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */

package de.juergens.dateparser;

import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.tool.DOTGenerator;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.tool.Grammar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

/**
 * @author Saroja Parameswaran
 * @author CHUA Chee Wee
 * @author jlengrand @ stackoverflow
 * @since 0.0.3
 * @see <a href="https://www.quora.com/What-are-the-steps-to-use-ANTLR-to-create-an-abstract-syntax-tree-of-Java-source-code-and-then-walk-the-tree"></a>
 * @see <a href="https://github.com/antlr/grammars-v4/issues/15"></a>
 */
@Test
public class GenerateParseTreeAST {
    private static final Logger log = LoggerFactory.getLogger( GenerateParseTreeAST.class.getName() );

    static ParseTreeListener extractor = new ParseTreeListener() {
        @Override
        public void visitTerminal(TerminalNode terminalNode) {
            log.info( terminalNode.toString() );
        }

        @Override
        public void visitErrorNode(ErrorNode errorNode) {
            log.info( errorNode.toString() );
        }

        @Override
        public void enterEveryRule(ParserRuleContext parserRuleContext) {
            log.info( parserRuleContext.toString() );
        }

        @Override
        public void exitEveryRule(ParserRuleContext parserRuleContext) {
            log.info( parserRuleContext.toString() );
        }
    };

    @DataProvider(name = "resourceFiles")
    public Object[][] resourceFiles() {
        return new Object[][]{
                new Object[]{AmbiguousDatesLexer.class, "ambiguousdates.expr"},
                new Object[]{DatesLexer.class, "dates.expr"},
                new Object[]{ISO8601datesLexer.class, "iso8601dates.expr"},
        };
    }

    @Test(suiteName = "date")
    public static void datesFromResource() throws IOException {
        ANTLRInputStream input = new ANTLRInputStream(
                GenerateParseTreeAST.class.getResourceAsStream("/dates.expr")
        );
        DatesLexer lexer = new DatesLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DatesParser parser = new DatesParser(tokens);

        {
            ParseTree treeDate = parser.date().getRuleContext(); // parse
            assertNotNull("date parse tree is null", treeDate);
            log.info(parser.date().toStringTree());
        }

        ParseTree treeDate = parser.date(); // parse
        assertNotNull("date parse tree is null", treeDate);
        log.info(parser.date().toStringTree());

        ParseTreeWalker walker = new ParseTreeWalker(); // create standard walker
        walker.walk(extractor, parser.date()); // initiate walk of tree with listener
    }

    @Test(dataProvider = "streams")
    public void doit (CharStream input) {
        TokenSource lexer = new DatesLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DatesParser parser = new DatesParser(tokens);
        ParserRuleContext tree = parser.date(); // parse

        ParseTreeWalker walker = new ParseTreeWalker(); // create standard walker
        walker.walk(extractor, tree); // initiate walk of tree with listener
    }

    @DataProvider(name = "streams")
    public Object[][] streams() {
        return new Object[][]{
                new Object[]{new ANTLRInputStream("09/06/2015")},
                new Object[]{new ANTLRInputStream("9/06/2015")}
        };
    }

    /**
     * <a href="https://stackoverflow.com/questions/10614659/no-viable-alternative-at-input"></a>
     */
    @Test(expectedExceptions = java.lang.NoSuchFieldError.class)
    public void dotTree() {

        // the expression
        String src = "(ICOM LIKE '%bridge%' or ICOM LIKE '%Munich%')";

        // create a lexer & parser
        //DatesLexer lexer = new DatesLexer(new ANTLRStringStream(src));
        //DatesParser parser = new DatesParser(new CommonTokenStream(lexer));

        DatesLexer lexer = new DatesLexer(new ANTLRInputStream(src));
        DatesParser parser = new DatesParser(new CommonTokenStream(lexer));

        // invoke the entry point of the parser (the parse() method) and get the AST
        Tree tree = parser.date();
        String grammarFileName = lexer.getGrammarFileName();
        log.info("grammar file name=" + grammarFileName);

        // print the DOT representation of the AST 
        Grammar grammar = Grammar.load(grammarFileName);
        DOTGenerator gen = new DOTGenerator(grammar);

        ATNState startState = new ATNState() {
            @Override
            public int getStateType() {
                return ATNState.BASIC;
            }
        };
        String st = gen.getDOT(startState);
        log.info(st);
    }
}
