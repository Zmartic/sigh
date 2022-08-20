package norswap.sigh.interpreter;

import norswap.sigh.ast.*;
import norswap.sigh.scopes.DeclarationKind;
import norswap.sigh.scopes.RootScope;
import norswap.sigh.scopes.Scope;
import norswap.sigh.scopes.SyntheticDeclarationNode;
import norswap.sigh.types.ArrayType;
import norswap.sigh.types.FloatType;
import norswap.sigh.types.IntType;
import norswap.sigh.types.StringType;
import norswap.sigh.types.Type;
import norswap.uranium.Reactor;
import norswap.utils.Util;
import norswap.utils.exceptions.Exceptions;
import norswap.utils.exceptions.NoStackException;
import norswap.utils.visitors.ValuedVisitor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static norswap.utils.Util.cast;
import static norswap.utils.Vanilla.coIterate;
import static norswap.utils.Vanilla.map;

/**
 * Implements a simple but inefficient interpreter for Sigh.
 *
 * <h2>Limitations</h2>
 * <ul>
 *     <li>The compiled code currently doesn't support closures (using variables in functions that
 *     are declared in some surroudning scopes outside the function). The top scope is supported.
 *     </li>
 * </ul>
 *
 * <p>Runtime value representation:
 * <ul>
 *     <li>{@code Int}, {@code Float}, {@code Bool}: {@link Long}, {@link Double}, {@link Boolean}</li>
 *     <li>{@code String}: {@link String}</li>
 *     <li>{@code null}: {@link Null#INSTANCE}</li>
 *     <li>Arrays: {@code Object[]}</li>
 *     <li>Structs: {@code HashMap<String, Object>}</li>
 *     <li>Functions: the corresponding {@link DeclarationNode} ({@link FunDeclarationNode} or
 *     {@link SyntheticDeclarationNode}), excepted structure constructors, which are
 *     represented by {@link Constructor}</li>
 *     <li>Types: the corresponding {@link StructDeclarationNode}</li>
 * </ul>
 */
public final class Interpreter
{
    // ---------------------------------------------------------------------------------------------

    private final ValuedVisitor<SighNode, Object> visitor = new ValuedVisitor<>();
    private final Reactor reactor;
    private ScopeStorage storage = null;
    private RootScope rootScope;
    private ScopeStorage rootStorage;

    // ---------------------------------------------------------------------------------------------

    public Interpreter (Reactor reactor) {
        this.reactor = reactor;

        // expressions
        visitor.register(IntLiteralNode.class,           this::intLiteral);
        visitor.register(FloatLiteralNode.class,         this::floatLiteral);
        visitor.register(StringLiteralNode.class,        this::stringLiteral);
        visitor.register(ReferenceNode.class,            this::reference);
        visitor.register(ConstructorNode.class,          this::constructor);
        visitor.register(ArrayLiteralNode.class,         this::arrayLiteral);
        visitor.register(ParenthesizedNode.class,        this::parenthesized);
        visitor.register(FieldAccessNode.class,          this::fieldAccess);
        visitor.register(ArrayAccessNode.class,          this::arrayAccess);
        visitor.register(FunCallNode.class,              this::funCall);
        visitor.register(UnaryExpressionNode.class,      this::unaryExpression);
        visitor.register(BinaryExpressionNode.class,     this::binaryExpression);
        visitor.register(RangeExpressionNode.class,      this::rangeExpression);
        visitor.register(AssignmentNode.class,           this::assignment);

        // statement groups & declarations
        visitor.register(RootNode.class,                 this::root);
        visitor.register(BlockNode.class,                this::block);
        visitor.register(VarDeclarationNode.class,       this::varDecl);
        // no need to visitor other declarations! (use fallback)

        // statements
        visitor.register(ExpressionStatementNode.class,  this::expressionStmt);
        visitor.register(IfNode.class,                   this::ifStmt);
        visitor.register(WhileNode.class,                this::whileStmt);
        visitor.register(ForNode.class,                  this::forStmt);
        visitor.register(ForEachVarNode.class,           this::forEachVarDecl);
        visitor.register(ForEachNode.class,              this::forEachStmt);
        visitor.register(ReturnNode.class,               this::returnStmt);

        visitor.registerFallback(node -> null);
    }

    // ---------------------------------------------------------------------------------------------

    public Object interpret (SighNode root) {
        try {
            return run(root);
        } catch (PassthroughException e) {
            throw Exceptions.runtime(e.getCause());
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object run (SighNode node) {
        try {
            return visitor.apply(node);
        } catch (InterpreterException | Return | PassthroughException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InterpreterException("exception while executing " + node, e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Used to implement the control flow of the return statement.
     */
    private static class Return extends NoStackException {
        final Object value;
        private Return (Object value) {
            this.value = value;
        }
    }

    // ---------------------------------------------------------------------------------------------

    private <T> T get(SighNode node) {
        return cast(run(node));
    }

    // ---------------------------------------------------------------------------------------------

    private Long intLiteral (IntLiteralNode node) {
        return node.value;
    }

    private Double floatLiteral (FloatLiteralNode node) {
        return node.value;
    }

    private String stringLiteral (StringLiteralNode node) {
        return node.value;
    }

    // ---------------------------------------------------------------------------------------------

    private Object parenthesized (ParenthesizedNode node) {
        return get(node.expression);
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] arrayLiteral (ArrayLiteralNode node) {
        return map(node.components, new Object[0], visitor);
    }

    // ---------------------------------------------------------------------------------------------

    private Object binaryExpression (BinaryExpressionNode node)
    {
        Type leftType  = reactor.get(node.left, "type");
        Type rightType = reactor.get(node.right, "type");

        // Cases where both operands should not be evaluated.
        switch (node.operator) {
            case OR:  return booleanOp(node, false);
            case AND: return booleanOp(node, true);
        }

        Object left  = get(node.left);
        Object right = get(node.right);

        if (node.operator == BinaryOperator.ADD
                && (leftType instanceof StringType || rightType instanceof StringType))
            return convertToString(left) + convertToString(right);

        // -- Array operation handeler --
        if(reactor.get(node,"type") instanceof ArrayType){
            /* left: primitive ,right: array */
            if(!(leftType  instanceof ArrayType)) {
                ArrayType r = (ArrayType) rightType; // ensured by semantic
                boolean floating = leftType instanceof FloatType || r.innerMostType instanceof FloatType;
                return arrayPrimitiveFactMirror(node, floating, r.dimension, (Number) left, right);
            }
            /* left: array ,right: primitive */
            if(!(rightType instanceof ArrayType)) {
                ArrayType l = (ArrayType) leftType; // ensured by semantic
                boolean floating = l.innerMostType instanceof FloatType || rightType instanceof FloatType;
                return arrayPrimitiveFact(node, floating, l.dimension, left, (Number) right);
            }
            /* left: array ,right: array */
            ArrayType l = (ArrayType) leftType;
            ArrayType r = (ArrayType) rightType;
            int dimLeft  = l.dimension;
            int dimRight = r.dimension;
            boolean floating = l.innerMostType instanceof FloatType || r.innerMostType instanceof FloatType;

            if(dimLeft >= dimRight)
                return arrayArrayFact(  node, floating, dimRight, dimLeft - dimRight, left, right);
            return arrayArrayFactMirror(node, floating, dimLeft , dimRight - dimLeft, left, right);
        }

        boolean floating = leftType instanceof FloatType || rightType instanceof FloatType;
        boolean numeric  = floating || leftType instanceof IntType;

        if (numeric)
            return numericOp(node, floating, (Number) left, (Number) right);

        switch (node.operator) {
            case EQUALITY:
                return  leftType.isPrimitive() ? left.equals(right) : left == right;
            case NOT_EQUALS:
                return  leftType.isPrimitive() ? !left.equals(right) : left != right;
        }
        throw new Error("should not reach here");
    }

    // ---------------------------------------------------------------------------------------------

    private boolean booleanOp (BinaryExpressionNode node, boolean isAnd)
    {
        boolean left = get(node.left);
        return isAnd
                ? left && (boolean) get(node.right)
                : left || (boolean) get(node.right);
    }

    // ---------------------------------------------------------------------------------------------

    // I'm aware that this implementation is not elegante
    private Object arrayArrayFact(BinaryExpressionNode node, boolean floating,
        int loopAA, int loopAP, Object left, Object right)
    {
        /* assert( dim[left] >= dim[right] )
         * loopAA = number of arrayArrayFact call
         * loopAP = number of arrayPrimitiveFact call
         * ---------------------------------
         * res = left + right
         *   ->
         * res[i] = left[i] + right[i]
         */
        if(loopAA > 0){
            Object[] l = (Object[]) left;
            Object[] r = (Object[]) right;
            if(l.length != r.length)
                throw new PassthroughException(new AssertionError(
                    format("Attempt to perform operation between incompatible arrays respectively of size %d and %d",l.length,r.length)));
            if(l.length == 0)
                throw new PassthroughException(new ArithmeticException("Attempting to perform an operation using empty arrays"));

            Object[] res = new Object[l.length];
            for(int i = 0; i < res.length; i++)
                res[i] = arrayArrayFact(node, floating, loopAA-1, loopAP, l[i], r[i]);
            return res;
        }
        return arrayPrimitiveFact(node, floating, loopAP, left, (Number) right);
    }

    private Object arrayArrayFactMirror(BinaryExpressionNode node, boolean floating,
        int loopAA, int loopAP, Object left, Object right)
    {
        /* assert( dim[left] < dim[right] )
         * loopAA = number of arrayArrayFact call
         * loopAP = number of arrayPrimitiveFact call
         * ---------------------------------
         * res = left + right
         *   ->
         * res[i] = left[i] + right[i]
         */
        if(loopAA > 0){
            Object[] l = (Object[]) left;
            Object[] r = (Object[]) right;
            if(l.length != r.length)
                throw new PassthroughException(new AssertionError(
                    format("Attempt to perform operation between incompatible arrays respectively of size %d and %d",l.length,r.length)));
            if(l.length == 0)
                throw new PassthroughException(new ArithmeticException("Attempting to perform an operation using empty arrays"));

            Object[] res = new Object[l.length];
            for(int i = 0; i < res.length; i++)
                res[i] = arrayArrayFactMirror(node, floating, loopAA-1, loopAP, l[i], r[i]);
            return res;
        }
        return arrayPrimitiveFactMirror(node, floating, loopAP, (Number) left, right);
    }

    private Object arrayPrimitiveFact(BinaryExpressionNode node, boolean floating,
        int loopAP, Object left, Number right)
    {
        /* loopAP = number of arrayPrimitiveFact call
         * ---------------------------------
         * res = left + right
         * ->
         * res[i] = left[i] + right
         */
        if(loopAP > 0){
            Object[] l = (Object[]) left;
            if(l.length == 0)
                throw new PassthroughException(new ArithmeticException("Attempting to perform an operation using an empty array"));
            Object[] res = new Object[l.length];
            for(int i = 0; i < res.length; i++)
                res[i] = arrayPrimitiveFact(node, floating, loopAP-1, l[i], right);
            return res;
        }
        return numericOp(node, floating, (Number) left, right);
    }

    private Object arrayPrimitiveFactMirror(BinaryExpressionNode node, boolean floating,
        int loopAP, Number left, Object right)
    {
        /* loopAP = number of arrayPrimitiveFact call
         * ---------------------------------
         * res = left + right
         * ->
         * res[i] = left + right[i]
         */
        if(loopAP > 0){
            Object[] r = (Object[]) right;
            if(r.length == 0)
                throw new PassthroughException(new ArithmeticException("Attempting to perform an operation using an empty array"));
            Object[] res = new Object[r.length];
            for(int i = 0; i < res.length; i++)
                res[i] = arrayPrimitiveFactMirror(node, floating, loopAP-1, left, r[i]);
            return res;
        }
        return numericOp(node, floating, left, (Number) right);
    }

    // ---------------------------------------------------------------------------------------------

    private Object numericOp
            (BinaryExpressionNode node, boolean floating, Number left, Number right)
    {
        long ileft, iright;
        double fleft, fright;

        if (floating) {
            fleft  = left.doubleValue();
            fright = right.doubleValue();
            ileft = iright = 0;
        } else {
            ileft  = left.longValue();
            iright = right.longValue();
            fleft = fright = 0;
        }

        Object result;
        if (floating)
            switch (node.operator) {
                case MULTIPLY:      return fleft *  fright;
                case DIVIDE:        return fleft /  fright;
                case REMAINDER:     return fleft %  fright;
                case ADD:           return fleft +  fright;
                case SUBTRACT:      return fleft -  fright;
                case GREATER:       return fleft >  fright;
                case LOWER:         return fleft <  fright;
                case GREATER_EQUAL: return fleft >= fright;
                case LOWER_EQUAL:   return fleft <= fright;
                case EQUALITY:      return fleft == fright;
                case NOT_EQUALS:    return fleft != fright;
                default:
                    throw new Error("should not reach here");
            }
        else
            switch (node.operator) {
                case MULTIPLY:      return ileft *  iright;
                case DIVIDE:        return ileft /  iright;
                case REMAINDER:     return ileft %  iright;
                case ADD:           return ileft +  iright;
                case SUBTRACT:      return ileft -  iright;
                case GREATER:       return ileft >  iright;
                case LOWER:         return ileft <  iright;
                case GREATER_EQUAL: return ileft >= iright;
                case LOWER_EQUAL:   return ileft <= iright;
                case EQUALITY:      return ileft == iright;
                case NOT_EQUALS:    return ileft != iright;
                default:
                    throw new Error("should not reach here");
            }
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] rangeExpression (RangeExpressionNode node)
    {
        long left = get(node.left);
        long right = get(node.right);
        int size = (int) (right - left);
        if(size <= 0)
            return new Object[0]; // empty selection

        Object[] range =  new Object[size];
        for(int iter = 0; iter < size; iter++)
            range[iter] = left + iter;

        return range;
    }

    // ---------------------------------------------------------------------------------------------

    public Object assignment (AssignmentNode node)
    {
        if (node.left instanceof ReferenceNode) {
            Scope scope = reactor.get(node.left, "scope");
            String name = ((ReferenceNode) node.left).name;
            Object rvalue = get(node.right);
            assign(scope, name, rvalue, reactor.get(node, "type"));
            return rvalue;
        }

        if (node.left instanceof ArrayAccessNode) {
            ArrayAccessNode arrayAccess = (ArrayAccessNode) node.left;
            Object[] array = getNonNullArray(arrayAccess.array);
            Type type = reactor.get(arrayAccess.index, "type");

            /* Simple Array Access */
            if(!(type instanceof ArrayType)) {
                int index = getIndex(arrayAccess.index);
                try {
                    return array[index] = get(node.right);
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new PassthroughException(e);
                }
            }
            /* Mutliple Array Access */
            int[] indexes = getIndexes(arrayAccess.index);
            Object[] right = get(node.right);

            if(indexes.length == 0)
                throw new PassthroughException(new NullPointerException(
                    "empty array access cannot be assigned"));
            if(indexes.length != right.length)
                throw new PassthroughException(new ArrayIndexOutOfBoundsException(
                    format("Trying to assign an array of size %d to an array access of size %d",
                           indexes.length, right.length)));

            try {
                for(int iter = 0; iter < indexes.length; iter++)
                    array[indexes[iter]] = right[iter];
                return array;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PassthroughException(e);
            }
        }

        if (node.left instanceof FieldAccessNode) {
            FieldAccessNode fieldAccess = (FieldAccessNode) node.left;
            Object object = get(fieldAccess.stem);
            if (object == Null.INSTANCE)
                throw new PassthroughException(
                    new NullPointerException("accessing field of null object"));
            Map<String, Object> struct = cast(object);
            Object right = get(node.right);
            struct.put(fieldAccess.fieldName, right);
            return right;
        }

        throw new Error("should not reach here");
    }

    // ---------------------------------------------------------------------------------------------

    private int getIndex (ExpressionNode node)
    {
        return checkInt(get(node));
    }

    // ---------------------------------------------------------------------------------------------

    private int[] getIndexes (ExpressionNode node)
    {
        /*
         * return the int[] value of node
         */
        Object[] obj = get(node);
        int[] indexes = new int[obj.length];
        for(int i = 0; i < obj.length; i++)
            indexes[i] = checkInt((long) obj[i]) ;
        return indexes;
    }

    // ---------------------------------------------------------------------------------------------

    private int checkInt (long value)
    {
        if (value < 0)
            throw new ArrayIndexOutOfBoundsException("Negative index: " + value);
        if (value >= Integer.MAX_VALUE - 1)
            throw new ArrayIndexOutOfBoundsException("Index exceeds max array index (2ˆ31 - 2): " + value);
        return (int) value;
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] getNonNullArray (ExpressionNode node)
    {
        Object object = get(node);
        if (object == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("indexing null array"));
        return (Object[]) object;
    }

    // ---------------------------------------------------------------------------------------------

    private Object unaryExpression (UnaryExpressionNode node)
    {
        // there is only NOT
        assert node.operator == UnaryOperator.NOT;
        return ! (boolean) get(node.operand);
    }

    // ---------------------------------------------------------------------------------------------

    private Object arrayAccess (ArrayAccessNode node)
    {
        Object[] array = getNonNullArray(node.array);
        Type type = reactor.get(node.index,"type");

        /* Simple Array Access */
        if(!(type instanceof ArrayType)) {
            try {
                return array[getIndex(node.index)];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PassthroughException(e);
            }
        }

        /* Multiple Array Access */
        int[] indexes = getIndexes(node.index);
        Object[] res = new Object[indexes.length];
        try {
            for(int iter = 0; iter < indexes.length; iter++)
                res[iter] = array[indexes[iter]];
            return res;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new PassthroughException(e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object root (RootNode node)
    {
        assert storage == null;
        rootScope = reactor.get(node, "scope");
        storage = rootStorage = new ScopeStorage(rootScope, null);
        storage.initRoot(rootScope);

        try {
            node.statements.forEach(this::run);
        } catch (Return r) {
            return r.value;
            // allow returning from the main script
        } finally {
            storage = null;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void block (BlockNode node) {
        Scope scope = reactor.get(node, "scope");
        storage = new ScopeStorage(scope, storage);
        node.statements.forEach(this::run);
        storage = storage.parent;
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Constructor constructor (ConstructorNode node) {
        // guaranteed safe by semantic analysis
        return new Constructor(get(node.ref));
    }

    // ---------------------------------------------------------------------------------------------

    private Object expressionStmt (ExpressionStatementNode node) {
        get(node.expression);
        return null;  // discard value
    }

    // ---------------------------------------------------------------------------------------------

    private Object fieldAccess (FieldAccessNode node)
    {
        Object stem = get(node.stem);
        if (stem == Null.INSTANCE)
            throw new PassthroughException(
                new NullPointerException("accessing field of null object"));
        return stem instanceof Map
                ? Util.<Map<String, Object>>cast(stem).get(node.fieldName)
                : (long) ((Object[]) stem).length; // only field on arrays
    }

    // ---------------------------------------------------------------------------------------------

    private Object funCall (FunCallNode node)
    {
        Object decl = get(node.function);
        Object[] args = map(node.arguments, new Object[0], this::run);

        if (decl == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("calling a null function"));

        if (decl instanceof SyntheticDeclarationNode)
            return builtin(((SyntheticDeclarationNode) decl).name(), args);

        if (decl instanceof Constructor)
            return buildStruct(((Constructor) decl).declaration, args);

        ScopeStorage oldStorage = storage;
        Scope scope = reactor.get(decl, "scope");
        storage = new ScopeStorage(scope, storage);

        FunDeclarationNode funDecl = (FunDeclarationNode) decl;
        coIterate(args, funDecl.parameters,
                (arg, param) -> { if(param.type instanceof ArrayTypeNode)
                                      checkLength((ArrayTypeNode) param.type, arg);
                                  storage.set(scope, param.name, arg);
        });

        try {
            get(funDecl.block);
        } catch (Return r) {
            return r.value;
        } finally {
            storage = oldStorage;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Object builtin (String name, Object[] args)
    {
        assert name.equals("print"); // only one at the moment
        String out = convertToString(args[0]);
        System.out.println(out);
        return out;
    }

    // ---------------------------------------------------------------------------------------------

    private String convertToString (Object arg)
    {
        if (arg == Null.INSTANCE)
            return "null";
        else if (arg instanceof Object[])
            return Arrays.deepToString((Object[]) arg);
        else if (arg instanceof FunDeclarationNode)
            return ((FunDeclarationNode) arg).name;
        else if (arg instanceof StructDeclarationNode)
            return ((StructDeclarationNode) arg).name;
        else if (arg instanceof Constructor)
            return "$" + ((Constructor) arg).declaration.name;
        else
            return arg.toString();
    }

    // ---------------------------------------------------------------------------------------------

    private HashMap<String, Object> buildStruct (StructDeclarationNode node, Object[] args)
    {
        HashMap<String, Object> struct = new HashMap<>();
        for (int i = 0; i < node.fields.size(); ++i)
            struct.put(node.fields.get(i).name, args[i]);
        return struct;
    }

    // ---------------------------------------------------------------------------------------------

    private Void ifStmt (IfNode node)
    {
        if (get(node.condition))
            get(node.trueStatement);
        else if (node.falseStatement != null)
            get(node.falseStatement);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void whileStmt (WhileNode node)
    {
        while (get(node.condition))
            get(node.body);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void forStmt (ForNode node)
    {
        Scope scope = reactor.get(node.iterator, "scope");
        Type type = reactor.get(node.iterator, "type");
        String name = node.iterator.name;
        // declare&init iterator
        get(node.iterator);

        while (!(boolean) get(node.condition)){
            // execute body
            get(node.body);
            // update the iterator
            Object rvalue = get(node.iterationRule);
            assign(scope, name, rvalue, type);
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void forEachVarDecl (ForEachVarNode node)
    {
        // no need to do anything, the variable is assigned during the first iteration of ForEach
        return null;
    }


    // ---------------------------------------------------------------------------------------------

    private Void forEachStmt (ForEachNode node)
    {
        Scope scope = reactor.get(node.iterator, "scope");
        Type type = reactor.get(node.iterator, "type");
        String name = node.iterator.name;
        Object[] array = getNonNullArray(node.iterated);

        if(array.length == 0)
            throw new PassthroughException(new IndexOutOfBoundsException("Cannot iterate over empty array"));

        if(node.iterator.type instanceof ArrayTypeNode)
            checkLength((ArrayTypeNode) node.iterator.type, array[0]);

        for (Object rvalue : array) {
            // assign iterator = iterated[iter]
            assign(scope, name, rvalue, type);
            // execute body
            get(node.body);
        }
        return null;
    }


    // ---------------------------------------------------------------------------------------------

    private Object reference (ReferenceNode node)
    {
        Scope scope = reactor.get(node, "scope");
        DeclarationNode decl = reactor.get(node, "decl");

        if (decl instanceof VarDeclarationNode
        || decl instanceof ParameterNode
        || decl instanceof ForEachVarNode
        || decl instanceof SyntheticDeclarationNode
                && ((SyntheticDeclarationNode) decl).kind() == DeclarationKind.VARIABLE)
            return scope == rootScope
                ? rootStorage.get(scope, node.name)
                : storage.get(scope, node.name);

        return decl; // structure or function
    }

    // ---------------------------------------------------------------------------------------------

    private Void returnStmt (ReturnNode node) {
        Object rvalue = node.expression == null ? null : get(node.expression);
        TypeNode type = reactor.get(node, "type"); // root function has no type = null
        if (type instanceof ArrayTypeNode)
            checkLength((ArrayTypeNode) type, rvalue);
        throw new Return(rvalue);
    }

    // ---------------------------------------------------------------------------------------------

    private Void varDecl (VarDeclarationNode node)
    {
        Scope scope = reactor.get(node, "scope");
        Object initializer = get(node.initializer);
        TypeNode type = node.type;
        if(type instanceof ArrayTypeNode)
            checkLength((ArrayTypeNode) type, initializer);
        assign(scope, node.name, initializer, reactor.get(node, "type"));
        return null;
    }

    private void checkLength(ArrayTypeNode type, Object value)
    {
        if(value != null && !(value instanceof Null)) {
            Object[] array = (Object[]) value;
            if (type.lengthHint != null) {
                long lengthHint = get(type.lengthHint);
                if (lengthHint <= 0)
                    throw new PassthroughException(new AssertionError("Length hinting cannot be expresses with zero or negatif value, got " + lengthHint));
                long arrayLength = array.length;
                if (lengthHint != arrayLength)
                    throw new PassthroughException(new AssertionError(format("Incorrect array length provided, expected size %d but got size %d", lengthHint, arrayLength)));
            }
            if(type.componentType instanceof ArrayTypeNode)
                if(array.length > 0 && array[0] instanceof Object[])
                    checkLength((ArrayTypeNode) type.componentType, array[0]);
                else
                    checkLength((ArrayTypeNode) type.componentType, null);

            return;
        }
        if (type.lengthHint != null)
            throw new PassthroughException(new NullPointerException("No target array found for length check"));
    }

    // ---------------------------------------------------------------------------------------------

    private void assign (Scope scope, String name, Object value, Type targetType)
    {
        if (value instanceof Long && targetType instanceof FloatType)
            value = ((Long) value).doubleValue();
        storage.set(scope, name, value);
    }

    // ---------------------------------------------------------------------------------------------
}
