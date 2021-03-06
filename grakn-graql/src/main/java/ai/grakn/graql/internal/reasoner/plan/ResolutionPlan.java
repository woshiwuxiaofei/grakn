/*
 * GRAKN.AI - THE KNOWLEDGE GRAPH
 * Copyright (C) 2018 Grakn Labs Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.grakn.graql.internal.reasoner.plan;

import ai.grakn.exception.GraqlQueryException;
import ai.grakn.graql.Var;
import ai.grakn.graql.admin.Atomic;
import ai.grakn.graql.internal.gremlin.GraqlTraversal;
import ai.grakn.graql.internal.reasoner.atom.Atom;
import ai.grakn.graql.internal.reasoner.atom.AtomicBase;
import ai.grakn.graql.internal.reasoner.atom.predicate.IdPredicate;
import ai.grakn.graql.internal.reasoner.atom.predicate.NeqPredicate;
import ai.grakn.graql.internal.reasoner.query.ReasonerQueries;
import ai.grakn.graql.internal.reasoner.query.ReasonerQueryImpl;
import ai.grakn.kb.internal.EmbeddedGraknTx;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * <p>
 * Class defining the resolution plan for a given {@link ReasonerQueryImpl}.
 * The plan is constructed  using the {@link GraqlTraversal} with the aid of {@link GraqlTraversalPlanner}.
 * </p>
 *
 * @author Kasper Piskorski
 *
 */
public final class ResolutionPlan {

    final private ReasonerQueryImpl query;
    final private ImmutableList<Atom> plan;

    public ResolutionPlan(ReasonerQueryImpl q){
        this.query = q;
        this.plan = GraqlTraversalPlanner.refinedPlan(query);
        validatePlan();
    }

    @Override
    public String toString(){
        return plan.stream().map(AtomicBase::toString).collect(Collectors.joining("\n"));
    }

    /**
     * @return corresponding atom plan
     */
    public ImmutableList<Atom> plan(){ return plan;}

    /**
     * @return true if the plan is complete with respect to provided query - contains all selectable atoms
     */
    private boolean isComplete(){
        return plan.containsAll(query.selectAtoms());
    }

    /**
     * @return true if the plan is valid with respect to provided query - its resolution doesn't lead to any non-ground neq predicates
     */
    private boolean isNeqGround(){
        Set<NeqPredicate> nonGroundPredicates = new HashSet<>();
        Set<Var> mappedVars = this.query.getAtoms(IdPredicate.class).map(Atomic::getVarName).collect(Collectors.toSet());
        for(Atom atom : this.plan){
            mappedVars.addAll(atom.getVarNames());
            atom.getPredicates(NeqPredicate.class)
                    .forEach(neq -> {
                        //look for non-local non-ground predicates
                        if (!mappedVars.containsAll(neq.getVarNames())
                                && !atom.getVarNames().containsAll(neq.getVarNames())){
                            nonGroundPredicates.add(neq);
                        } else{
                            //if this is ground for this atom but non-ground for another it is ground
                            if (nonGroundPredicates.contains(neq)) nonGroundPredicates.remove(neq);
                        }
                    });
        }
        return nonGroundPredicates.isEmpty();
    }


    private void validatePlan() {
        if (!isNeqGround()) {
            throw GraqlQueryException.nonGroundNeqPredicate(query);
        }
        if (!isComplete()){
            throw GraqlQueryException.incompleteResolutionPlan(query);
        }
    }

    /**
     * compute the query resolution plan - list of queries ordered by their cost as computed by the graql traversal planner
     * @return list of prioritised queries
     */
    public LinkedList<ReasonerQueryImpl> queryPlan(){
        LinkedList<ReasonerQueryImpl> queries = new LinkedList<>();
        LinkedList<Atom> atoms = new LinkedList<>(plan);
        EmbeddedGraknTx<?> tx = query.tx();

        List<Atom> nonResolvableAtoms = new ArrayList<>();
        while (!atoms.isEmpty()) {
            Atom top = atoms.remove();
            if (top.isRuleResolvable()) {
                if (!nonResolvableAtoms.isEmpty()) {
                    queries.add(ReasonerQueries.create(nonResolvableAtoms, tx));
                    nonResolvableAtoms.clear();
                }
                queries.add(ReasonerQueries.atomic(top));
            } else {
                nonResolvableAtoms.add(top);
                if (atoms.isEmpty()) queries.add(ReasonerQueries.create(nonResolvableAtoms, tx));
            }
        }
        return queries;
    }
}

