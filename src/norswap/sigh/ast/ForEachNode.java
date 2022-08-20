package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class ForEachNode extends StatementNode
{
    public final ForEachVarNode iterator;
    public final ExpressionNode iterated;
    public final StatementNode body;

    public ForEachNode (Span span, Object iterator, Object iterated, Object body) {
        super(span);
        this.iterator = Util.cast(iterator, ForEachVarNode.class);
        this.iterated = Util.cast(iterated, ExpressionNode.class);
        this.body = Util.cast(body, StatementNode.class);
    }

    @Override public String contents ()
    {
        String candidate = String.format("for %s in %s ...",
            iterator,
            iterated.contents());
        return candidate.length() <= contentsBudget()
            ? candidate
            : "for (?) in (?) ...";
    }
}
