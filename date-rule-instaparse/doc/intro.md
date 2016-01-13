# Introduction to date-rule-instaparse

TODO: write [great documentation](http://jacobian.org/writing/what-to-write/)


>   <p>Here's a quick guide to the syntax for defining context-free grammars:</p>
>   <table>
>   <tbody><tr><th>Category</th><th>Notations</th><th>Example</th></tr>
>   <tr><td>Rule</td><td>: := ::= =</td><td>S = A</td></tr>
>   <tr><td>End of rule</td><td>; . (optional)</td><td>S = A;</td></tr>
>   <tr><td>Alternation</td><td>|</td><td>A | B</td></tr>
>   <tr><td>Concatenation</td><td>whitespace or ,</td><td>A B</td></tr>
>   <tr><td>Grouping</td><td>()</td><td>(A | B) C</td></tr>
>   <tr><td>Optional</td><td>? []</td><td>A? [A]</td></tr>
>   <tr><td>One or more</td><td>+</td><td>A+</td></tr>
>   <tr><td>Zero or more</td><td>* {}</td><td>A* {A}</td></tr>
>   <tr><td>String terminal</td><td>"" ''</td><td>'a' "a"</td></tr>
>   <tr><td>Regex terminal</td><td>#"" #''</td><td>#'a' #"a"</td></tr>
>   <tr><td>Epsilon</td><td>Epsilon epsilon EPSILON eps ε "" ''</td><td>S = 'a' S | Epsilon</td></tr>
>   <tr><td>Comment</td><td>(* *)</td><td>(* This is a comment *)</td></tr>
>   </tbody></table>
>   <p>As is the norm in EBNF notation, concatenation has a higher precedence than alternation, so in the absence of parentheses, something like <code>A B | C D</code> means <code>(A B) | (C D)</code>.</p>

Extract from the documentation off instaparse on the homepage.
