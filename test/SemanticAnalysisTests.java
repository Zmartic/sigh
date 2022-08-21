import norswap.autumn.AutumnTestFixture;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.SighNode;
import norswap.uranium.Reactor;
import norswap.uranium.UraniumTestFixture;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;

/**
 * NOTE(norswap): These tests were derived from the {@link InterpreterTests} and don't test anything
 * more, but show how to idiomatically test semantic analysis. using {@link UraniumTestFixture}.
 */
public final class SemanticAnalysisTests extends UraniumTestFixture
{
    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.rule = grammar.root();
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    private String input;

    @Override protected Object parse (String input) {
        this.input = input;
        return autumnFixture.success(input).topValue();
    }

    @Override protected String astNodeToString (Object ast) {
        LineMapString map = new LineMapString("<test>", input);
        return ast.toString() + " (" + ((SighNode) ast).span.startString(map) + ")";
    }

    // ---------------------------------------------------------------------------------------------

    @Override protected void configureSemanticAnalysis (Reactor reactor, Object ast) {
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        walker.walk(((SighNode) ast));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testLiteralsAndUnary() {
        successInput("return 42");
        successInput("return 42.0");
        successInput("return \"hello\"");
        successInput("return (42)");
        successInput("return [1, 2, 3]");
        successInput("return true");
        successInput("return false");
        successInput("return null");
        successInput("return !false");
        successInput("return !true");
        successInput("return !!true");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testNumericBinary() {
        successInput("return 1 + 2");
        successInput("return 2 - 1");
        successInput("return 2 * 3");
        successInput("return 2 / 3");
        successInput("return 3 / 2");
        successInput("return 2 % 3");
        successInput("return 3 % 2");

        successInput("return 1.0 + 2.0");
        successInput("return 2.0 - 1.0");
        successInput("return 2.0 * 3.0");
        successInput("return 2.0 / 3.0");
        successInput("return 3.0 / 2.0");
        successInput("return 2.0 % 3.0");
        successInput("return 3.0 % 2.0");

        successInput("return 1 + 2.0");
        successInput("return 2 - 1.0");
        successInput("return 2 * 3.0");
        successInput("return 2 / 3.0");
        successInput("return 3 / 2.0");
        successInput("return 2 % 3.0");
        successInput("return 3 % 2.0");

        successInput("return 1.0 + 2");
        successInput("return 2.0 - 1");
        successInput("return 2.0 * 3");
        successInput("return 2.0 / 3");
        successInput("return 3.0 / 2");
        successInput("return 2.0 % 3");
        successInput("return 3.0 % 2");

        failureInputWith("return 2 + true", "Trying to add Int with Bool");
        failureInputWith("return true + 2", "Trying to add Bool with Int");
        //failureInputWith("return 2 + [1]", "Trying to add Int with Int[]");
        //failureInputWith("return [1] + 2", "Trying to add Int[] with Int");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testOtherBinary() {
        successInput("return true && false");
        successInput("return false && true");
        successInput("return true && true");
        successInput("return true || false");
        successInput("return false || true");
        successInput("return false || false");

        failureInputWith("return false || 1",
            "Attempting to perform binary logic on non-boolean type: Int");
        failureInputWith("return 2 || true",
            "Attempting to perform binary logic on non-boolean type: Int");

        successInput("return 1 + \"a\"");
        successInput("return \"a\" + 1");
        successInput("return \"a\" + true");

        successInput("return 1 == 1");
        successInput("return 1 == 2");
        successInput("return 1.0 == 1.0");
        successInput("return 1.0 == 2.0");
        successInput("return true == true");
        successInput("return false == false");
        successInput("return true == false");
        successInput("return 1 == 1.0");

        failureInputWith("return true == 1", "Trying to compare incomparable types Bool and Int");
        failureInputWith("return 2 == false", "Trying to compare incomparable types Int and Bool");

        successInput("return \"hi\" == \"hi\"");
        successInput("return [1] == [1]");

        successInput("return 1 != 1");
        successInput("return 1 != 2");
        successInput("return 1.0 != 1.0");
        successInput("return 1.0 != 2.0");
        successInput("return true != true");
        successInput("return false != false");
        successInput("return true != false");
        successInput("return 1 != 1.0");

        failureInputWith("return true != 1", "Trying to compare incomparable types Bool and Int");
        failureInputWith("return 2 != false", "Trying to compare incomparable types Int and Bool");

        successInput("return \"hi\" != \"hi\"");
        successInput("return [1] != [1]");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testVarDecl() {
        successInput("var x: Int = 1; return x");
        successInput("var x: Float = 2.0; return x");

        successInput("var x: Int = 0; return x = 3");
        successInput("var x: String = \"0\"; return x = \"S\"");

        failureInputWith("var x: Int = true", "expected Int but got Bool");
        failureInputWith("return x + 1", "Could not resolve: x");
        failureInputWith("return x + 1; var x: Int = 2", "Variable used before declaration: x");

        // implicit conversions
        successInput("var x: Float = 1 ; x = 2");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testRootAndBlock () {
        successInput("return");
        successInput("return 1");
        successInput("return 1; return 2");

        successInput("print(\"a\")");
        successInput("print(\"a\" + 1)");
        successInput("print(\"a\"); print(\"b\")");

        successInput("{ print(\"a\"); print(\"b\") }");

        successInput(
            "var x: Int = 1;" +
            "{ print(\"\" + x); var x: Int = 2; print(\"\" + x) }" +
            "print(\"\" + x)");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testCalls() {
        successInput(
            "fun add (a: Int, b: Int): Int { return a + b } " +
            "return add(4, 7)");

        successInput(
            "struct Point { var x: Int; var y: Int }" +
            "return $Point(1, 2)");

        successInput("var str: String = null; return print(str + 1)");

        failureInputWith("return print(1)", "argument 0: expected String but got Int");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayStructAccess() {
        successInput("return [1][0]");
        successInput("return [1.0][0]");
        successInput("return [1, 2][1]");

        failureInputWith("return [1][true]", "Indexing an array using a non-Int-valued expression");

        // TODO make this legal?
        // successInput("[].length", 0L);

        successInput("return [1].length");
        successInput("return [1, 2].length");

        successInput("var array: Int[] = null; return array[0]");
        successInput("var array: Int[] = null; return array.length");

        successInput("var x: Int[] = [0, 1]; x[0] = 3; return x[0]");
        successInput("var x: Int[] = []; x[0] = 3; return x[0]");
        successInput("var x: Int[] = null; x[0] = 3");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, 2).y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = null;" +
            "return p.y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = $P(1, 2);" +
            "p.y = 42;" +
            "return p.y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = null;" +
            "p.y = 42");

        failureInputWith(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, true)",
            "argument 1: expected Int but got Bool");

        failureInputWith(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, 2).z",
            "Trying to access missing field z on struct P");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testIfWhile () {
        successInput("if (true) return 1 else return 2");
        successInput("if (false) return 1 else return 2");
        successInput("if (false) return 1 else if (true) return 2 else return 3 ");
        successInput("if (false) return 1 else if (false) return 2 else return 3 ");

        successInput("var i: Int = 0; while (i < 3) { print(\"\" + i); i = i + 1 } ");

        failureInputWith("if 1 return 1",
            "If statement with a non-boolean condition of type: Int");
        failureInputWith("while 1 return 1",
            "While statement with a non-boolean condition of type: Int");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testInference() {
        successInput("var array: Int[] = []");
        successInput("var array: String[] = []");
        successInput("fun use_array (array: Int[]) {} ; use_array([])");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testTypeAsValues() {
        successInput("struct S{} ; return \"\"+ S");
        successInput("struct S{} ; var type: Type = S ; return \"\"+ type");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testUnconditionalReturn()
    {
        successInput("fun f(): Int { if (true) return 1 else return 2 } ; return f()");

        // TODO: would be nice if this pinpointed the if-statement as missing the return,
        //   not the whole function declaration
        failureInputWith("fun f(): Int { if (true) return 1 } ; return f()",
            "Missing return in function");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayBinary() {
        successInput("var i: Int[] = [1,2,3] + 1");
        successInput("var i: Int[] = [1,2,3] - 1");
        successInput("var i: Int[] = [1,2,3] * 1");
        successInput("var i: Int[] = [1,2,3] / 1");

        successInput("var i: Int[] = 1 + [1,2,3]");
        successInput("var i: Int[] = 1 - [1,2,3]");
        successInput("var i: Int[] = 1 * [1,2,3]");
        successInput("var i: Int[] = 1 / [1,2,3]");

        successInput("var i: Int[] = [1,2,3] + [1,2,3]");
        successInput("var i: Int[] = [1,2,3] - [1,2,3]");
        successInput("var i: Int[] = [1,2,3] * [1,2,3]");
        successInput("var i: Int[] = [1,2,3] / [1,2,3]");

        successInput("var i: Bool[] = [1,2,3] >  1");
        successInput("var i: Bool[] = [1,2,3] <  1");
        successInput("var i: Bool[] = [1,2,3] >= 1");
        successInput("var i: Bool[] = [1,2,3] <= 1");

        successInput("var i: Bool[] = 1 >  [1,2,3]");
        successInput("var i: Bool[] = 1 <  [1,2,3]");
        successInput("var i: Bool[] = 1 >= [1,2,3]");
        successInput("var i: Bool[] = 1 <= [1,2,3]");

        successInput("var i: Bool[] = [1,2,3] > [1,2,3]");
        successInput("var i: Bool[] = [1,2,3] < [1,2,3]");
        successInput("var i: Bool[] = [1,2,3] >= [1,2,3]");
        successInput("var i: Bool[] = [1,2,3] <= [1,2,3]");

        successInput("var i: Int[][] = [[1,1],[2,2],[3,3]] + 1");
        successInput("var i: Int[][] = [[1,1],[2,2],[3,3]] + [1,0,1]");// result = [[2,2],[0,0],[4,4]]

        failureInputWith("var i: Int[] = [1,2,3] + null",
            "Trying to add Int[] with Null");
        successInput("var i: String = [1,2,3] + \"h\"");
        failureInputWith("var i: String[] = [\"hello\",\"world\"] + 1",
            "Trying to add String[] with Int");

        successInput("return [1,2,3] == [1,2,3];");
    }

    @Test public void testForStmt()
    {
        successInput("for var i: Int = 0 do i + 1 until i > 10 { print(i + \"\"); } ; return");
        successInput("for var i: Int = 0 do i + 1 { if i > 10 { return; } } ;");

        failureInputWith("for var i: Int = 0 do i + \"1\" until i > 10 { print(i + \"\"); } ; return",
            "incompatible iteration rule type provided for iterator `i`: expected Int but got String");
        failureInputWith("for var i: Int = 0 do i + 1 until i + 10 { print(i + \"\"); } ; return",
            "For statement with a non-boolean condition of type: Int");

        successInput("var sum: Int = 0 ; for i: Int in [1,5,9,6] { sum = sum + i; } ; print(\"sum = \"+sum) ;");
        successInput("for s: String in [\"hello\",\" \",\"world\"] { print(s); } ;");

        failureInputWith("for i: Int in [1,2,3,4] { print(i); } ;",
            "incompatible argument provided for argument 0: expected String but got Int");
        failureInputWith("for i: String in [1,2,3,4] { print(i); } ;",
            "incompatible component type provided for iterator i: expected String but got Int");
        failureInputWith("for s: String in \"hello\" { print(s); } ;",
            "For loop received non-iterable type: String");
        failureInputWith("for s: String in [] { print(s); } ;",
            "For loop cannot iterate over an empty array");
    }

    @Test public void testRangeExpr()
    {
        successInput("var a: Int[] = [0,0,0,0] ; a[1:3] = [1,1] * 5 ;");

        successInput("var a: Int[] = [1,2,3,4] ; var b: Int[] = a[0:2] + b[2:4] ;");
        successInput("var a: Int[] = [1,2,3,4] ; var empty_a: Int[] = a[1:1] ;");

        successInput("var a: Int[] = 0 : 10 ;");
        successInput("var even_a: Int[] = (0 : 5) * 2 ;");

        failureInputWith("var a: Int[] = [0,0,0,0] ; var b: Int[] = a[\"1\":3]",
            "Incompatible left operand type for range operation. expected: Int actual: String");
        failureInputWith("var a: Int[] = [0,0,0,0] ; var b: Int[] = a[1:\"3\"]",
            "Incompatible right operand type for range operation. expected: Int actual: String");
        failureInputWith("var a: Int[] = [0,0,0,0] ; var b: Int[] = a[1:3.0]",
            "Incompatible right operand type for range operation. expected: Int actual: Float");
        failureInputWith("var a: Int[] = [0,0,0,0] ; var b: Int[] = a[1:2:3]",
            "Incompatible left operand type for range operation. expected: Int actual: Int[]");
    }

    @Test public void testLengthHint() {
        successInput("var a: Int[4] = [0,0,0,0];");
        successInput("var i: Int = 4 ; var a: Int[i] = [0,0,0,0];");
        failureInputWith("var a: Int[\"four\"] = [0,0,0,0];",
            "Incompatible type for array length hinting. expected Int but got String");
        failureInputWith("var i: String = \"four\" ; var a: Int[i] = [0,0,0,0];",
            "Incompatible type for array length hinting. expected Int but got String");

        successInput("var a: Int[12] = [0,0,0,0];"); //cannot check until run time
    }
}
