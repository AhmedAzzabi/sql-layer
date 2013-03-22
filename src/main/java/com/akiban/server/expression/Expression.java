
package com.akiban.server.expression;

import com.akiban.qp.exec.Plannable;
import com.akiban.server.explain.Explainable;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import java.util.Map;

public interface Expression extends Explainable {
    boolean nullIsContaminating();
            
    /**
     * <p>Whether this expression, including any child expressions, is a constant.</p>
     * <p>If an expression is constant, it must return {@code false} for both
     * {@link #needsBindings()} and {@link #needsRow()}. Note that the converse is not true: if both "needs"
     * methods return {@code false}, the method may still be non-constant. {@code RAND()} is a good example.</p>
     * @return whether this expression is constant
     */
    boolean isConstant();

    /**
     * <p>Whether this expression requires a binding before it can be evaluated.</p>
     * <p>If this method returns {@code true}, {@link #isConstant()} must return {@code false}</p>
     * @return whether this expression requires a bindings
     */
    boolean needsBindings();

    /**
     * <p>Whether this expression requires a row before it can be evaluated.</p>
     * <p>If this method returns {@code true}, {@link #isConstant()} must return {@code false}</p>
     * @return whether this expression requires a row
     */
    boolean needsRow();

    /**
     * Returns a thread-local, runtime object that reflects this expression. You should not share this object
     * across threads.
     * @return this expression's ExpressionEvaluation
     */
    ExpressionEvaluation evaluation();

    /**
     * The type represented by this expression. {@code this.evaluation().eval()} returns a {@link ValueSource} whose
     * {@link ValueSource#getConversionType()} method must return the same type as returned by this method (or NULL).
     * @return the AkType this expression's runtime instance will eventually have
     */
    // TODO: Should this return ExpressionType? Or is the precision / scale not relevant?
    AkType valueType();
    
    /**
     * 
     * @return The name of the function/arithmetic operator implemented by this
     * expression.
     */
    String name ();
}
