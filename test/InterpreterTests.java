import norswap.autumn.AutumnTestFixture;
import norswap.autumn.Grammar;
import norswap.autumn.Grammar.rule;
import norswap.autumn.ParseResult;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.SighNode;
import norswap.sigh.interpreter.Interpreter;
import norswap.sigh.interpreter.LengthException;
import norswap.sigh.interpreter.Null;
import norswap.uranium.Reactor;
import norswap.uranium.SemanticError;
import norswap.utils.IO;
import norswap.utils.TestFixture;
import norswap.utils.data.wrappers.Pair;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;
import java.util.HashMap;
import java.util.Set;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

public final class InterpreterTests extends TestFixture {

    // TODO peeling

    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    // ---------------------------------------------------------------------------------------------

    private Grammar.rule rule;

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, null);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn, String expectedOutput) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (rule rule, String input, Object expectedReturn, String expectedOutput) {
        // TODO
        // (1) write proper parsing tests
        // (2) write some kind of automated runner, and use it here

        autumnFixture.rule = rule;
        ParseResult parseResult = autumnFixture.success(input);
        SighNode root = parseResult.topValue();

        Reactor reactor = new Reactor();
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        Interpreter interpreter = new Interpreter(reactor);
        walker.walk(root);
        reactor.run();
        Set<SemanticError> errors = reactor.errors();

        if (!errors.isEmpty()) {
            LineMapString map = new LineMapString("<test>", input);
            String report = reactor.reportErrors(it ->
                it.toString() + " (" + ((SighNode) it).span.startString(map) + ")");
            //            String tree = AttributeTreeFormatter.format(root, reactor,
            //                    new ReflectiveFieldWalker<>(SighNode.class, PRE_VISIT, POST_VISIT));
            //            System.err.println(tree);
            throw new AssertionError(report);
        }

        Pair<String, Object> result = IO.captureStdout(() -> interpreter.interpret(root));
        assertEquals(result.b, expectedReturn);
        if (expectedOutput != null) assertEquals(result.a, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn, String expectedOutput) {
        rule = grammar.root;
        check("return " + input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn) {
        rule = grammar.root;
        check("return " + input, expectedReturn);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkThrows (String input, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> check(input, null));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testLiteralsAndUnary () {
        checkExpr("42", 42L);
        checkExpr("42.0", 42.0d);
        checkExpr("\"hello\"", "hello");
        checkExpr("(42)", 42L);
        checkExpr("[1, 2, 3]", new Object[]{1L, 2L, 3L});
        checkExpr("true", true);
        checkExpr("false", false);
        checkExpr("null", Null.INSTANCE);
        checkExpr("!false", true);
        checkExpr("!true", false);
        checkExpr("!!true", true);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        checkExpr("1 + 2", 3L);
        checkExpr("2 - 1", 1L);
        checkExpr("2 * 3", 6L);
        checkExpr("2 / 3", 0L);
        checkExpr("3 / 2", 1L);
        checkExpr("2 % 3", 2L);
        checkExpr("3 % 2", 1L);

        checkExpr("1.0 + 2.0", 3.0d);
        checkExpr("2.0 - 1.0", 1.0d);
        checkExpr("2.0 * 3.0", 6.0d);
        checkExpr("2.0 / 3.0", 2d / 3d);
        checkExpr("3.0 / 2.0", 3d / 2d);
        checkExpr("2.0 % 3.0", 2.0d);
        checkExpr("3.0 % 2.0", 1.0d);

        checkExpr("1 + 2.0", 3.0d);
        checkExpr("2 - 1.0", 1.0d);
        checkExpr("2 * 3.0", 6.0d);
        checkExpr("2 / 3.0", 2d / 3d);
        checkExpr("3 / 2.0", 3d / 2d);
        checkExpr("2 % 3.0", 2.0d);
        checkExpr("3 % 2.0", 1.0d);

        checkExpr("1.0 + 2", 3.0d);
        checkExpr("2.0 - 1", 1.0d);
        checkExpr("2.0 * 3", 6.0d);
        checkExpr("2.0 / 3", 2d / 3d);
        checkExpr("3.0 / 2", 3d / 2d);
        checkExpr("2.0 % 3", 2.0d);
        checkExpr("3.0 % 2", 1.0d);

        checkExpr("2 * (4-1) * 4.0 / 6 % (2+1)", 1.0d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testOtherBinary () {
        checkExpr("true  && true",  true);
        checkExpr("true  || true",  true);
        checkExpr("true  || false", true);
        checkExpr("false || true",  true);
        checkExpr("false && true",  false);
        checkExpr("true  && false", false);
        checkExpr("false && false", false);
        checkExpr("false || false", false);

        checkExpr("1 + \"a\"", "1a");
        checkExpr("\"a\" + 1", "a1");
        checkExpr("\"a\" + true", "atrue");

        checkExpr("1 == 1", true);
        checkExpr("1 == 2", false);
        checkExpr("1.0 == 1.0", true);
        checkExpr("1.0 == 2.0", false);
        checkExpr("true == true", true);
        checkExpr("false == false", true);
        checkExpr("true == false", false);
        checkExpr("1 == 1.0", true);
        checkExpr("[1] == [1]", true);

        checkExpr("1 != 1", false);
        checkExpr("1 != 2", true);
        checkExpr("1.0 != 1.0", false);
        checkExpr("1.0 != 2.0", true);
        checkExpr("true != true", false);
        checkExpr("false != false", false);
        checkExpr("true != false", true);
        checkExpr("1 != 1.0", false);

        checkExpr("\"hi\" != \"hi2\"", true);
        checkExpr("[1] != [1]", false);

         // test short circuit
        checkExpr("true || print(\"x\") == \"y\"", true, "");
        checkExpr("false && print(\"x\") == \"y\"", false, "");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testVarDecl () {
        check("var x: Int = 1; return x", 1L);
        check("var x: Float = 2.0; return x", 2d);

        check("var x: Int = 0; return x = 3", 3L);
        check("var x: String = \"0\"; return x = \"S\"", "S");

        // implicit conversions
        check("var x: Float = 1; x = 2; return x", 2.0d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testRootAndBlock () {
        rule = grammar.root;
        check("return", null);
        check("return 1", 1L);
        check("return 1; return 2", 1L);

        check("print(\"a\")", null, "a\n");
        check("print(\"a\" + 1)", null, "a1\n");
        check("print(\"a\"); print(\"b\")", null, "a\nb\n");

        check("{ print(\"a\"); print(\"b\") }", null, "a\nb\n");

        check(
            "var x: Int = 1;" +
            "{ print(\"\" + x); var x: Int = 2; print(\"\" + x) }" +
            "print(\"\" + x)",
            null, "1\n2\n1\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testCalls () {
        check(
            "fun add (a: Int, b: Int): Int { return a + b } " +
                "return add(4, 7)",
            11L);

        HashMap<String, Object> point = new HashMap<>();
        point.put("x", 1L);
        point.put("y", 2L);

        check(
            "struct Point { var x: Int; var y: Int }" +
                "return $Point(1, 2)",
            point);

        check("var str: String = null; return print(str + 1)", "null1", "null1\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testArrayStructAccess () {
        checkExpr("[1][0]", 1L);
        checkExpr("[1.0][0]", 1d);
        checkExpr("[1, 2][1]", 2L);

        // TODO check that this fails (& maybe improve so that it generates a better message?)
        // or change to make it legal (introduce a top type, and make it a top type array if thre
        // is no inference context available)
        // checkExpr("[].length", 0L);
        checkExpr("[1].length", 1L);
        checkExpr("[1, 2].length", 2L);

        checkThrows("var array: Int[] = null; return array[0]", NullPointerException.class);
        checkThrows("var array: Int[] = null; return array.length", NullPointerException.class);

        check("var x: Int[] = [0, 1]; x[0] = 3; return x[0]", 3L);
        checkThrows("var x: Int[] = []; x[0] = 3; return x[0]",
            ArrayIndexOutOfBoundsException.class);
        checkThrows("var x: Int[] = null; x[0] = 3",
            NullPointerException.class);

        check(
            "struct P { var x: Int; var y: Int }" +
                "return $P(1, 2).y",
            2L);

        checkThrows(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = null;" +
                "return p.y",
            NullPointerException.class);

        check(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = $P(1, 2);" +
                "p.y = 42;" +
                "return p.y",
            42L);

        checkThrows(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = null;" +
                "p.y = 42",
            NullPointerException.class);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testIfWhile () {
        check("if (true) return 1 else return 2", 1L);
        check("if (false) return 1 else return 2", 2L);
        check("if (false) return 1 else if (true) return 2 else return 3 ", 2L);
        check("if (false) return 1 else if (false) return 2 else return 3 ", 3L);

        check("var i: Int = 0; while (i < 3) { print(\"\" + i); i = i + 1 } ", null, "0\n1\n2\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testInference () {
        check("var array: Int[] = []", null);
        check("var array: String[] = []", null);
        check("fun use_array (array: Int[]) {} ; use_array([])", null);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testTypeAsValues () {
        check("struct S{} ; return \"\"+ S", "S");
        check("struct S{} ; var type: Type = S ; return \"\"+ type", "S");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testUnconditionalReturn()
    {
        check("fun f(): Int { if (true) return 1 else return 2 } ; return f()", 1L);
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testForStmt()
    {
        rule = grammar.root;
        check("for var i: Int = 1 do i + 1 until i > 5 { print(i + \"\"); }", null, "1\n2\n3\n4\n5\n");
        check("var array: Int[] = [1,2,3,4,5] ; for i: Int in array { print(i + \"\"); }", null, "1\n2\n3\n4\n5\n");
        check("var sum: Int = 0 ; for i: Int in [1,2,3,4,5] { sum = sum + i; if (i > 3) return sum; }", 10L);
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testRange()
    {
        rule = grammar.root;
        check("print((0:5) + \"\" );", null,"[0, 1, 2, 3, 4]\n");
        check("print((2:6) + \"\" );", null,"[2, 3, 4, 5]\n");
        check("print((-2:2) + \"\" );", null,"[-2, -1, 0, 1]\n");
        check("print((2:2) + \"\" );", null,"[]\n");
        check("print((6:2) + \"\" );", null,"[]\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayAssign()
    {
        rule = grammar.root;
        check("var a: Int[] = [0,0,0,0,0]; var idxs: Int[] = [1,2,3]; a[idxs] = [5,6,7]; print(a+\"\");", null,"[0, 5, 6, 7, 0]\n");
        check("var a: Int[] = [0,0,0,0,0]; var idxs: Int[] = [3,1,2]; a[idxs] = [5,6,7]; print(a+\"\");", null,"[0, 6, 7, 5, 0]\n");
        check("var a: Int[] = [0,0,0,0,0]; a[1:4] = [1,2,3]; print(a+\"\");", null,"[0, 1, 2, 3, 0]\n");
        checkThrows("var a: Int[] = [0,0,0,0,0];" +
                    "a[1:4] = [1];", // incompatible length
                    LengthException.class);
        checkThrows("var a: Int[] = [0,0,0,0,0];" +
                    "a[1:2] = [1,2,3,4,5];", // incompatible length,
                    LengthException.class);
        checkThrows("var a: Int[] = [0,0,0,0,0];" +
                    "a[1:1] = [];", // a[1:1] = empty selection = cannot be assigned
                    NullPointerException.class);
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayBinary()
    {
        rule = grammar.root;
        check("var a: Int[]   = [1,2,3];       var b: Int[]   = [1,1,2];       var c: Int[]   = a + b; print(c + \"\");",null,"[2, 3, 5]\n");
        check("var a: Int[]   = [1,2,3];       var b: Float[] = [1.0,1.0,2.0]; var c: Float[] = a + b; print(c + \"\");",null,"[2.0, 3.0, 5.0]\n");
        check("var a: Float[] = [1.0,2.0,3.0]; var b: Int[]   = [1,1,2];       var c: Float[] = a + b; print(c + \"\");",null,"[2.0, 3.0, 5.0]\n");
        check("var a: Float[] = [1.0,2.0,3.0]; var b: Float[] = [1.0,1.0,2.0]; var c: Float[] = a + b; print(c + \"\");",null,"[2.0, 3.0, 5.0]\n");

        check("var a: Int[]   = [1,2,3];       var b: Int[]   = a + 1;   print(b +\"\");",null,"[2, 3, 4]\n");
        check("var a: Int[]   = [1,2,3];       var b: Float[] = a + 1.0; print(b +\"\");",null,"[2.0, 3.0, 4.0]\n");
        check("var a: Float[] = [1.0,2.0,3.0]; var b: Float[] = a + 1;   print(b +\"\");",null,"[2.0, 3.0, 4.0]\n");
        check("var a: Float[] = [1.0,2.0,3.0]; var b: Float[] = a + 1.0; print(b +\"\");",null,"[2.0, 3.0, 4.0]\n");

        check("var a: Int[]   = [1,2,3]; var b: Int[]   = [0,2,4]; var c: Bool[]  = a >  b; print(c + \"\");",null,"[true, false, false]\n");
        check("var a: Int[]   = [1,2,3]; var b: Int[]   = [0,2,4]; var c: Bool[]  = a <  b; print(c + \"\");",null,"[false, false, true]\n");
        check("var a: Int[]   = [1,2,3]; var b: Int[]   = [0,2,4]; var c: Bool[]  = a >= b; print(c + \"\");",null,"[true, true, false]\n");
        check("var a: Int[]   = [1,2,3]; var b: Int[]   = [0,2,4]; var c: Bool[]  = a <= b; print(c + \"\");",null,"[false, true, true]\n");

        check("var a: Int[]   = [1,2,3]; var c: Bool[]  = a >  2; print(c + \"\");",null,"[false, false, true]\n");
        check("var a: Int[]   = [1,2,3]; var c: Bool[]  = a <  2; print(c + \"\");",null,"[true, false, false]\n");
        check("var a: Int[]   = [1,2,3]; var c: Bool[]  = a >= 2; print(c + \"\");",null,"[false, true, true]\n");
        check("var a: Int[]   = [1,2,3]; var c: Bool[]  = a <= 2; print(c + \"\");",null,"[true, true, false]\n");

        check("var a: Int[]   = [1,2,3]; var b: Int[] = a[0:2] + a[1:3]; print(b +\"\");",null,"[3, 5]\n");

        check("var a: Int[][] = [[1,2,3],[4,5,6],[7,8,9]] + 1;       print(a +\"\");",null,"[[2, 3, 4], [5, 6, 7], [8, 9, 10]]\n");
        check("var a: Int[][] = 1 + [[1,2,3],[4,5,6],[7,8,9]];       print(a +\"\");",null,"[[2, 3, 4], [5, 6, 7], [8, 9, 10]]\n");
        check("var a: Int[][] = [[1,2,3],[4,5,6],[7,8,9]] + [1,0,2]; print(a +\"\");",null,"[[2, 3, 4], [4, 5, 6], [9, 10, 11]]\n");
        check("var a: Int[][] = [1,0,2] + [[1,2,3],[4,5,6],[7,8,9]]; print(a +\"\");",null,"[[2, 3, 4], [4, 5, 6], [9, 10, 11]]\n");

        checkThrows("var a: Int[] = [1,2,3];" +
                    "var b: Int[] = [1,2];" +
                    "var c: Int[] = a + b;", // incompatible length
                    LengthException.class);
        checkThrows("var a: Int[][] = [[1,2,3],[4,5,6],[7,8,9]];" +
                    "var b: Int[][] = [[1],[1],[1]];" +
                    "var c: Int[][] = a + b;", // incompatible length
                    LengthException.class);

        checkThrows("var a: Int[] = [] + []", ArithmeticException.class);
        checkThrows("var a: Int[] = [] + 1", ArithmeticException.class);
        checkThrows("var a: Int[] = 1 + []", ArithmeticException.class);

        checkThrows("var a: Int[][] = [[]]", AssertionError.class); // bug from Sigh: expected Int[][] but got Int[][][]

        check("return [1,2,3] == [1,2,3];", true);
        check("return [1,2,3] == [3,2,1];", false);
        check("return [1,2,3] != [1,2,3];", false);
        check("return [1,2,3] != [3,2,1];", true);
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testLengthHinting()
    {
        rule = grammar.root;
        check("var a: Int[4] = [1,2,3,4];",null);
        check("var a: Int[2][4] = [[1,1],[2,2],[3,3],[4,4]];",null);

        checkThrows("var a: Int[0] = []",
            AssertionError.class); // incoherent length hint
        checkThrows("var a: Int[-1] = [0,0,0];",
            AssertionError.class); // incoherent length hint

        check("var a: Int[][] = [];",null);
        checkThrows("var a: Int[][1] = [];",
                    AssertionError.class);       // array size expected 1 but got 0
        checkThrows("var a: Int[1][] = [];",
                    NullPointerException.class); // dimension of [] is too low, length hint cannot be checked

        check("fun f(x:Int[3]): Int { return x[1] } ; return f([1,2,3])",2L);
        checkThrows("fun f(x:Int[3]): Int { return x[1] } ; return f([1,2,3,4])",AssertionError.class);
        checkThrows("fun f(x:Int[3]): Int { return x[1] } ; return f([])",AssertionError.class);
        check("fun f(i:Int, x:Int[i]): Int { return x[i-1] } ; return f(3,[1,2,3])",3L);

        check("fun f(x:Int[]): Int[2] { return x } ; print( f([1,2]) + \"\" );", null, "[1, 2]\n");
        checkThrows("fun f(x:Int[]): Int[2] { return x } ; return f([1,2,3]);", AssertionError.class);
        checkThrows("fun f(x:Int[]): Int[2] { return x } ; return f([]);", AssertionError.class);
        check("fun f(i:Int, x:Int[]): Int[i] { return x } ; print( f(2,[1,2]) + \"\" );", null, "[1, 2]\n");

        check("var ii: Int[][] = [[1],[2],[3]]; for i:Int[1] in ii { print(i + \"\"); }", null, "[1]\n[2]\n[3]\n");
        checkThrows("var ii: Int[][] = [[1],[2],[3]]; for i:Int[12] in ii { print(i + \"\"); }", AssertionError.class);

        // dumb exemple
        check("for var i:Int[3] = [1,2,3] do i + 1 until i[2] > 4 { print( i[1] + \"\" ) }", null, "2\n3\n");
        checkThrows("for var i:Int[4] = [1,2,3] do i + 1 until i[2] > 4 { print( i[1] + \"\" ) }", AssertionError.class);
    }

    // ---------------------------------------------------------------------------------------------

    // NOTE(norswap): Not incredibly complete, but should cover the basics.
}
