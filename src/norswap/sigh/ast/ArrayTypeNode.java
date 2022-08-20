package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.sigh.types.ArrayType;
import norswap.utils.Util;

public final class ArrayTypeNode extends TypeNode
{
    public final TypeNode componentType;
    public final ExpressionNode lengthHint;

    public ArrayTypeNode (Span span, Object componentType, Object lengthHint) {
        super(span);
        this.componentType = Util.cast(componentType, TypeNode.class);
        this.lengthHint = lengthHint == null ? null : Util.cast(lengthHint, ExpressionNode.class);
    }

    @Override public String contents() {
        return componentType.contents() + "[]";
    }
}
