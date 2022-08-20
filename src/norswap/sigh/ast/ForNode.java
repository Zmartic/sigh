package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class ForNode extends StatementNode
{
    public final VarDeclarationNode iterator;
    public final ExpressionNode iterationRule;
    public final ExpressionNode condition;
    public final StatementNode body;

    public ForNode (Span span, Object iterator, Object iterationRule, Object condition, Object body) {
        super(span);
        this.iterator = Util.cast(iterator, VarDeclarationNode.class);
        this.iterationRule = Util.cast(iterationRule, ExpressionNode.class);
        this.condition = condition == null
            ? new ReferenceNode(null, "false")
            : Util.cast(condition, ExpressionNode.class);
        this.body = Util.cast(body, StatementNode.class);
    }

    @Override public String contents ()
    {
        String candidate = String.format("for %s do %s until %s ...",
            iterator.contents(),
            iterationRule.contents(),
            condition.contents());
        return candidate.length() <= contentsBudget()
            ? candidate
            : "for (?) do (?) until (?) ...";
    }
}