package org.semanticweb.ontop.owlrefplatform.core.translator;

/*
 * #%L
 * ontop-reformulation-core
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.datatypes.XMLDatatypeUtil;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.model.vocabulary.XMLSchema;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.algebra.*;
import org.openrdf.query.algebra.Compare.CompareOp;
import org.openrdf.query.algebra.MathExpr.MathOp;
import org.openrdf.query.parser.ParsedGraphQuery;
import org.openrdf.query.parser.ParsedQuery;
import org.openrdf.query.parser.ParsedTupleQuery;
import org.semanticweb.ontop.model.*;
import org.semanticweb.ontop.model.Predicate.COL_TYPE;
import org.semanticweb.ontop.model.ValueConstant;
import org.semanticweb.ontop.model.impl.OBDADataFactoryImpl;
import org.semanticweb.ontop.model.impl.OBDAVocabulary;
import org.semanticweb.ontop.owlrefplatform.core.abox.SemanticIndexURIMap;
import org.semanticweb.ontop.owlrefplatform.core.basicoperations.Unifier;
import org.semanticweb.ontop.owlrefplatform.core.basicoperations.UnifierUtilities;
import org.semanticweb.ontop.owlrefplatform.core.basicoperations.UriTemplateMatcher;
import org.slf4j.LoggerFactory;

import java.util.*;

/***
 * Translate a SPARQL algebra expression into a Datalog program that has the
 * same semantics. We use the built-int predicates Join and Left join. The rules
 * in the program have always 1 or 2 operator atoms, plus (in)equality atoms
 * (due to filters).
 * 
 * This programs needs to be flattened by another procedure later.
 * 
 * @author mariano
 */
public class SparqlAlgebraToDatalogTranslator {

	
	private final OBDADataFactory ofac = OBDADataFactoryImpl.getInstance();

	private final TermComparator comparator = new TermComparator();

	private UriTemplateMatcher uriTemplateMatcher;

	private SemanticIndexURIMap uriRef = null;  // used only in the Semantic Index mode
	
	public SparqlAlgebraToDatalogTranslator(UriTemplateMatcher templateMatcher) {
		uriTemplateMatcher = templateMatcher;
	}
	
	public void setTemplateMatcher(UriTemplateMatcher templateMatcher) {
		uriTemplateMatcher = templateMatcher;
	}

	protected static org.slf4j.Logger log = LoggerFactory
			.getLogger(SparqlAlgebraToDatalogTranslator.class);

	public DatalogProgram translate(ParsedQuery pq, List<String> signature) {
		TupleExpr te = pq.getTupleExpr();

		log.debug("SPARQL algebra: \n{}", te);
		DatalogProgram result = ofac.getDatalogProgram();

		// Render the variable names in the signature into Variable object
		List<Variable> vars = new LinkedList<Variable>();
		for (String vs : signature) {
			vars.add(ofac.getVariable(vs));
		}
		int[] freshvarcount = { 1 };

		translate(vars, te, result, 1, freshvarcount);
		return result;
	}
	
	/**
	 * Translate a given SPARQL query object to datalog program.
	 * 
	 *
	 *            The Query object.
	 * @return Datalog program that represents the construction of the SPARQL
	 *         query.
	 */
//	public DatalogProgram translate(Query arqQuery, List<String> signature) {
//
//		Op op = Algebra.compile(arqQuery);
//
//		log.debug("SPARQL algebra: \n{}", op);
//
//		DatalogProgram result = ofac.getDatalogProgram();
//
//		// Render the variable names in the signature into Variable object
//		List<Variable> vars = new LinkedList<Variable>();
//		for (String vs : signature) {
//			vars.add(ofac.getVariable(vs));
//		}
//
//		int[] freshvarcount = { 1 };
//
//		//translate(vars, op, result, 1, freshvarcount);
//		return result;
//	}

	private void translate(List<Variable> vars, TupleExpr te,
			DatalogProgram pr, long i, int[] varcount) {
		if (te instanceof Slice) {

			// Add LIMIT and OFFSET modifiers, if any
			Slice slice = (Slice) te;
			translate(vars, slice, pr, i, varcount);

		} else if (te instanceof Distinct) {

			// Add DISTINCT modifier, if any
			Distinct distinct = (Distinct) te;
			translate(vars, distinct, pr, i, varcount);

		} else if (te instanceof Projection) {

			// Add PROJECTION modifier, if any
			Projection project = (Projection) te;
			translate(vars, project, pr, i, varcount);

		} else if (te instanceof Order) {

			// Add ORDER BY modifier, if any
			Order order = (Order) te;
			translate(vars, order, pr, i, varcount);
		
		} else if (te instanceof Group) { 
			Group gr = (Group) te;
			translate(vars, gr, pr, i, varcount);
			
		} else if (te instanceof Filter) {
			Filter filter = (Filter) te;
			translate(vars, filter, pr, i, varcount);

		} else if (te instanceof StatementPattern) {

			StatementPattern stmp = (StatementPattern) te;
			translate(vars, stmp, pr, i, varcount);

		} else if (te instanceof Join) {
			Join join = (Join) te;
			translate(vars, join, pr, i, varcount);

		} else if (te instanceof Union) {
			Union union = (Union) te;
			translate(vars, union, pr, i, varcount);

		} else if (te instanceof LeftJoin) {
			LeftJoin join = (LeftJoin) te;
			translate(vars, join, pr, i, varcount);
		
		} else if (te instanceof Reduced) {
			translate(vars, ((Reduced) te).getArg(), pr, i, varcount);
		
		} else if (te instanceof Extension) { 
			Extension extend = (Extension) te;
			translate(vars, extend, pr, i, varcount);
		
		} else {
			try {
				throw new QueryEvaluationException("Operation not supported: "
						+ te.toString());
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private void translate(List<Variable> vars, Extension extend,
			DatalogProgram pr, long i, int[] varcount) {
		TupleExpr subte = extend.getArg();
		List<ExtensionElem> elements = extend.getElements();
		Set<Variable> atom2VarsSet = null;

        /**
         * TODO: why are they outside the LOOP??
         * Will we really want to accumulate variables???
         */
		List<Term> atom2VarsList = new LinkedList<Term>();
		List<Term> atom1VarsList = new LinkedList<Term>();

		for (ExtensionElem el: elements) {
			Variable var = null;
			
			String name = el.getName();
			ValueExpr vexp = el.getExpr();

			var = ofac.getVariable(name);			
			Term term = getBooleanTerm(vexp);

			Set<Variable> atom1VarsSet = getVariables(subte);
			atom1VarsList.addAll(atom1VarsSet);
			
			atom1VarsList.add(var);
			Collections.sort(atom1VarsList, comparator);

            /**
             * Only variable names, no aggregation formula.
             */
            List<Term> atom1Variables = new ArrayList<>(atom1VarsList);

			int indexOfvar = atom1VarsList.indexOf(var);
			atom1VarsList.set(indexOfvar,term);
			Predicate leftAtomPred = ofac.getPredicate("ans" + (i),
					atom1VarsList.size());
			Function head = ofac.getFunction(leftAtomPred, atom1VarsList);
		
			atom2VarsSet = getVariables(subte);
			
			atom2VarsList.addAll(atom2VarsSet);
			Collections.sort(atom2VarsList, comparator);
			Predicate rightAtomPred = ofac.getPredicate("ans" + ((2 * i)),
					atom2VarsList.size());
			Function rightAtom = ofac.getFunction(rightAtomPred, atom2VarsList);

			CQIE newrule = ofac.getCQIE(head, rightAtom);
			
			/**
			 * When there is an aggregate in the head,
			 * the arity of the atom is reduced.
			 * 
			 * This atom usually appears in parent rules, so 
			 * its arity must be fixed in these rules.
			 */
			if (vexp instanceof AggregateOperator) {
				pr = updateArity(leftAtomPred, atom1Variables, pr);
			}
			pr.appendRule(newrule);
		}

		/**
		 * Translating the rest
		 */
        List<Variable> vars1 = new LinkedList<Variable>();
        if (!atom2VarsList.isEmpty()){
            for (Term var1 : atom2VarsList)
                vars1.add((Variable) var1);
            translate(vars1, subte, pr, 2 * i, varcount);
        } else{
            translate(vars, subte, pr, i , varcount);
        }
	}

    /**
     * In some cases (like when aggregates are used), we cannot know in advance the final arity
     * of a given atom.
     * The strategy adopted here is to update this arity a posteriori if needed.
     *
     * For a given "ans_xy" function symbol, we look at all the already existing rules in the Datalog program
     * and replace each atom that use this function symbol.
     *
     * @param functionSymbol Read-only.
     * @param atomTerms terms to use to update atoms. Read-only.
     * @param programToUpdate Will be modified. Also returned.
     * @return the updated Datalog program
     */
	private DatalogProgram updateArity(Predicate functionSymbol, List<Term> atomTerms,
			DatalogProgram programToUpdate) {

        // New atom
		Function updatedAtom = ofac.getFunction(functionSymbol, atomTerms);

        /**
         * Looks at all the already existing rules (mostly parent rules)
         */
		for(CQIE rule : programToUpdate.getRules()) {

            /**
             * Looks at each body atom
             */
            List<Function> bodyAtoms = rule.getBody();
            for(int i = 0; i < bodyAtoms.size(); i++) {
                /**
                 * Replaces the atom if it uses the same answer function symbol
                 */
                Predicate localFunctionSymbol = bodyAtoms.get(i).getFunctionSymbol();
                if (localFunctionSymbol.equals(functionSymbol)) {
                    bodyAtoms.set(i, updatedAtom);
                }
            }
            /**
             * The rule is updated by side-effect (some people call it a bug).
             * Unfortunately, we cannot call rule.updateBody(bodyAtoms) ...
             * 
             * TODO: stop this practice.
             */
        }
		return programToUpdate;
	}

	private void translate(List<Variable> vars, Union union,
			DatalogProgram pr, long i, int[] varcount) {
		TupleExpr left = union.getLeftArg();
		TupleExpr right = union.getRightArg();

		/* Preparing the two atoms */

		Set<Variable> atom1VarsSet = getVariables(left);
		List<Term> atom1VarsList = new LinkedList<Term>();
		atom1VarsList.addAll(atom1VarsSet);
		Collections.sort(atom1VarsList, comparator);
		Predicate leftAtomPred = ofac.getPredicate("ans" + (2 * i),
				atom1VarsList.size());
		Function leftAtom = ofac.getFunction(leftAtomPred, atom1VarsList);

		Set<Variable> atom2VarsSet = getVariables(right);
		List<Term> atom2VarsList = new LinkedList<Term>();
		atom2VarsList.addAll(atom2VarsSet);
		Collections.sort(atom2VarsList, comparator);
		Predicate rightAtomPred = ofac.getPredicate("ans" + ((2 * i) + 1),
				atom2VarsList.size());
		Function rightAtom = ofac.getFunction(rightAtomPred, atom2VarsList);

		/* Preparing the head of the Union rules (2 rules) */
		// Collections.sort(vars, comparator);
		List<Term> headVars = new LinkedList<Term>();
		for (Variable var : vars) {
			headVars.add(var);
		}
		Predicate answerPred = ofac.getPredicate("ans" + i, vars.size());
		Function head = ofac.getFunction(answerPred, headVars);

		/*
		 * Adding the UNION to the program, i.e., two rules Note, we need to
		 * make null any head variables that do not appear in the body of the
		 * uniones, e.g,
		 * 
		 * q(x,y,z) <- Union(R(x,y), R(x,z))
		 * 
		 * results in
		 * 
		 * q(x,y,null) :- ... R(x,y) ... q(x,null,z) :- ... R(x,z) ...
		 */

		// finding out null
		Set<Variable> nullVars = new HashSet<Variable>();
		nullVars.addAll(vars);
		nullVars.removeAll(atom1VarsSet); // the remaining variables do not
											// appear in the body assigning
											// null;
		Unifier nullifier = UnifierUtilities.getNullifier(nullVars);
		// making the rule
		CQIE newrule1 = ofac.getCQIE(head, leftAtom);
		pr.appendRule(UnifierUtilities.applyUnifier(newrule1, nullifier));

		// finding out null
		nullVars = new HashSet<Variable>();
		nullVars.addAll(vars);
		nullVars.removeAll(atom2VarsSet); // the remaining variables do not
											// appear in the body assigning
											// null;
		nullifier = UnifierUtilities.getNullifier(nullVars);
		// making the rule
		CQIE newrule2 = ofac.getCQIE(head, rightAtom);
		pr.appendRule(UnifierUtilities.applyUnifier(newrule2, nullifier));

		/*
		 * Translating the rest
		 */
		{
			List<Variable> vars1 = new LinkedList<Variable>();
			for (Term var : atom1VarsList)
				vars1.add((Variable) var);
			translate(vars1, left, pr, 2 * i, varcount);
		}
		{
			List<Variable> vars2 = new LinkedList<Variable>();
			for (Term var : atom2VarsList)
				vars2.add((Variable) var);
			translate(vars2, right, pr, 2 * i + 1, varcount);
		}

	}

	private void translate(List<Variable> vars, Join join, DatalogProgram pr,
			long i, int[] varcount) {
		TupleExpr left = join.getLeftArg();
		TupleExpr right = join.getRightArg();

		/* Preparing the two atoms */

		Set<Variable> atom1VarsSet = getVariables(left);
		List<Term> atom1VarsList = new LinkedList<Term>();
		atom1VarsList.addAll(atom1VarsSet);
		Collections.sort(atom1VarsList, comparator);
		Predicate leftAtomPred = ofac.getPredicate("ans" + (2 * i),
				atom1VarsList.size());
		Function leftAtom = ofac.getFunction(leftAtomPred, atom1VarsList);

		Set<Variable> atom2VarsSet = getVariables(right);
		List<Term> atom2VarsList = new LinkedList<Term>();
		atom2VarsList.addAll(atom2VarsSet);
		Collections.sort(atom2VarsList, comparator);
		Predicate rightAtomPred = ofac.getPredicate("ans" + ((2 * i) + 1),
				atom2VarsList.size());
		Function rightAtom = ofac.getFunction(rightAtomPred, atom2VarsList);
		/* The join, this is no longer necessary, we will try to avoid explicit joins
		as much as poosible, just use comma */
//		Predicate joinp = OBDAVocabulary.SPARQL_JOIN;
//		Function joinAtom = ofac.getFunction(joinp, leftAtom, rightAtom);

		/* Preparing the head of the Join rule */
		// Collections.sort(vars, comparator);
		List<Term> headVars = new LinkedList<Term>();
		for (Variable var : vars) {
			headVars.add(var);
		}
		Predicate answerPred = ofac.getPredicate("ans" + i, vars.size());
		Function head = ofac.getFunction(answerPred, headVars);

		/*
		 * Adding the join to the program
		 */

		CQIE newrule = ofac.getCQIE(head, leftAtom, rightAtom);
		pr.appendRule(newrule);

		/*
		 * Translating the rest
		 */
		{
			List<Variable> vars1 = new LinkedList<Variable>();
			for (Term var : atom1VarsList)
				vars1.add((Variable) var);
			translate(vars1, left, pr, 2 * i, varcount);
		}
		{
			List<Variable> vars2 = new LinkedList<Variable>();
			for (Term var : atom2VarsList)
				vars2.add((Variable) var);
			translate(vars2, right, pr, 2 * i + 1, varcount);
		}
	}

	private void translate(List<Variable> vars, LeftJoin join,
			DatalogProgram pr, long i, int[] varcount) {
		TupleExpr left = join.getLeftArg();
		TupleExpr right = join.getRightArg();
		ValueExpr filter = join.getCondition();

		/* Preparing the two atoms */

		Set<Variable> atom1VarsSet = getVariables(left);
		List<Term> atom1VarsList = new LinkedList<Term>();
		atom1VarsList.addAll(atom1VarsSet);
		Collections.sort(atom1VarsList, comparator);
		Predicate leftAtomPred = ofac.getPredicate("ans" + (2 * i),
				atom1VarsList.size());
		Function leftAtom = ofac.getFunction(leftAtomPred, atom1VarsList);

		Set<Variable> atom2VarsSet = getVariables(right);
		List<Term> atom2VarsList = new LinkedList<Term>();
		atom2VarsList.addAll(atom2VarsSet);
		Collections.sort(atom2VarsList, comparator);
		Predicate rightAtomPred = ofac.getPredicate("ans" + ((2 * i) + 1),
				atom2VarsList.size());
		Function rightAtom = ofac.getFunction(rightAtomPred, atom2VarsList);

		/* The join */
		Predicate joinp = OBDAVocabulary.SPARQL_LEFTJOIN;

		Function joinAtom = ofac.getFunction(joinp, leftAtom, rightAtom);

		/* adding the conditions of the filter for the LeftJoin */
		if (filter != null) {
		
			List<Term> joinTerms = joinAtom.getTerms();
			joinTerms.add(((Function) getBooleanTerm(filter)));
//			for (Expr expr : filter.getList()) {
//				joinTerms.add(((Function) getBooleanTerm(expr)));
//			}
			
		}

		/* Preparing the head of the LeftJoin rule */
		// Collections.sort(vars, comparator);
		List<Term> headVars = new LinkedList<Term>();
		for (Variable var : vars) {
			headVars.add(var);
		}
		Predicate answerPred = ofac.getPredicate("ans" + i, vars.size());
		Function head = ofac.getFunction(answerPred, headVars);

		/*
		 * Adding the join to the program
		 */

		List<Function> atoms = new LinkedList<Function>();
		atoms.add(joinAtom);

		CQIE newrule = ofac.getCQIE(head, atoms);
		pr.appendRule(newrule);

		/*
		 * Translating the rest
		 */
		{
			List<Variable> vars1 = new LinkedList<Variable>();
			for (Term var : atom1VarsList)
				vars1.add((Variable) var);
			translate(vars1, left, pr, 2 * i, varcount);
		}
		{
			List<Variable> vars2 = new LinkedList<Variable>();
			for (Term var : atom2VarsList)
				vars2.add((Variable) var);
			translate(vars2, right, pr, 2 * i + 1, varcount);
		}
	}

    /**
     * Projection SPARQL node.
     *
     * Creates one rule for the projection and adds it into the Datalog Program.
     *
     * In this rule, there is 1 head atom and 1 body atom.
     *
     * Basically, the head atom usually takes less arguments than the body atom
     *   --> that is the most common effect of the projection.
     *
     * Pursues by translating its child nodes (from the SPARQL tree).
     */
	private void translate(List<Variable> vars, Projection project,
			DatalogProgram pr, long i, int[] varcount) {

		TupleExpr te = project.getArg();

        // Projected variables --> for the head atom
		List<Variable> projectedVariables = new ArrayList<>();
		for (ProjectionElem var : project.getProjectionElemList().getElements()) {
			projectedVariables.add(ofac.getVariable(var.getSourceName()));
		}

        // All variables --> for the body atom
        List<Variable> allVariables = new ArrayList<>(getVariables(te));

        /**
         * Head: considers only the projected variables.
         */
		Predicate predicate = ofac.getPredicate("ans" + i,
				projectedVariables.size());
		Function headAtom = ofac.getFunction(predicate, new ArrayList<Term>(projectedVariables));

        /**
         * Body atom (just one).
         * In many cases, the body atom takes more arguments than the head one.
         *
         * For this body atom, we don't know yet the right arity because
         * we don't know if some aggregates are used or not.
         *
         * For the moment, we do like if there were no aggregate so all the variables are arguments
         * of the atom.
         * If latter, we find some aggregates, the atom arity will be updated.
         */
        List<Term> bodyAtomTerms = new ArrayList<Term>(allVariables);

        Predicate bodyAtomFunctionSymbol = ofac.getPredicate("ans" + (i + 1), allVariables.size());
        Function bodyAtom = ofac.getFunction(bodyAtomFunctionSymbol, bodyAtomTerms);

        CQIE newRule = ofac.getCQIE(headAtom, bodyAtom);
		pr.appendRule(newRule);

		/**
         * Continue the nested tree
         */
		translate(allVariables, te, pr, i + 1, varcount);
	}

	private void translate(List<Variable> vars, Slice slice,
			DatalogProgram pr, long i, int[] varcount) {
		TupleExpr te;
		pr.getQueryModifiers().setOffset(slice.getOffset());
		pr.getQueryModifiers().setLimit(slice.getLimit());
		te = slice.getArg(); // narrow down the query
		translate(vars, te, pr, i, varcount);
	}

	private void translate(List<Variable> vars, Distinct distinct,
			DatalogProgram pr, long i, int[] varcount) {
		TupleExpr te;
		pr.getQueryModifiers().setDistinct();
		te = distinct.getArg(); // narrow down the query
		translate(vars, te, pr, i, varcount);
	}

	private void translate(List<Variable> vars, Order order,
			DatalogProgram pr, long i, int[] varcount) {
		TupleExpr te;
		for (OrderElem c : order.getElements()) {
			
			ValueExpr expression = c.getExpr();
			if (!(expression instanceof Var)) {
				throw new IllegalArgumentException("Error translating ORDER BY. The current implementation can only sort by variables, this query has a more complex expression. Offending expression: '"+expression+"'");
			}
			Var v = (Var) expression;
			Variable var = ofac.getVariable(v.getName());
			int direction = 0;
			if (c.isAscending()) direction = 1;
            else direction = 2;
			pr.getQueryModifiers().addOrderCondition(var, direction);
		}
		te = order.getArg(); // narrow down the query
		translate(vars, te, pr, i, varcount);
	}
	
	private void translate(List<Variable> vars, Group group,
			DatalogProgram pr, long i, int[] varcount) {

		TupleExpr te;
		te = group.getArg(); // narrow down the query

		Set <String> bindings = group.getGroupBindingNames();
		
//		List <GroupElem> gel = group.getGroupElements();
//		
//		for (GroupElem el: gel) {
//			AggregateOperator op = el.getOperator();
//			String name = el.getName();
//			int ii = 0;
//		}
		
		//Set<Variable> remainingVars= getVariables(te);
		
		//Construction the aggregate Atom
		if (!bindings.isEmpty()){
			String nextVar = bindings.iterator().next();
			Variable groupvar = (Variable) ofac.getVariable(nextVar);
			Function aggregateAtom = ofac.getFunction(OBDAVocabulary.SPARQL_GROUP,groupvar );

			//Construction the head of the new rule
			Predicate predicate = ofac.getPredicate("ans" + (i), vars.size());
			List<Term> termVars = new LinkedList<Term>();
			termVars.addAll(vars);
			Function head = ofac.getFunction(predicate, termVars);

			//Construction the body of the new rule that encode the rest of the tree
			Predicate pbody;
			Function bodyAtom;
			pbody = ofac.getPredicate("ans" + (i * 2), vars.size());
			bodyAtom = ofac.getFunction(pbody, termVars);

			//Constructing the list
			LinkedList<Function> body = new LinkedList<Function>();
			body.add(bodyAtom);
			body.add(aggregateAtom);

			//Constructing the rule itself
			CQIE cq = ofac.getCQIE(head, body);
			pr.appendRule(cq);


			/*
		Set <String> bindings = group.getGroupBindingNames();
		for (String b : bindings) {
			Variable var = ofac.getVariable(b);
			pr.getQueryModifiers().addGroupCondition(var);
		}
		te = group.getArg(); // narrow down the query
		translate(vars, te, pr, i, varcount);
			 */

			//iterating
			translate(vars, te, pr, i*2, varcount);
		}else{
			translate(vars, te, pr, i, varcount);
		}
		
		
	}

	public void translate(List<Variable> var, Filter filter, DatalogProgram pr,
			long i, int varcount[]) {
		ValueExpr condition = filter.getCondition();
		List<Function> filterAtoms = new LinkedList<Function>();
		Set<Variable> filteredVariables = new LinkedHashSet<Variable>();

			Function a = null;
			if (condition instanceof Var) {
				a = ofac.getFunction(OBDAVocabulary.IS_TRUE, getVariableTerm((Var) condition));
			} else {
				a = (Function) getBooleanTerm(condition);
			}
			if (a != null) {
				Function filterAtom = ofac.getFunction(a.getFunctionSymbol(),
						a.getTerms());
				filterAtoms.add(filterAtom);
				filteredVariables.addAll(filterAtom.getReferencedVariables());
			}

		Predicate predicate = ofac.getPredicate("ans" + (i), var.size());
		List<Term> vars = new LinkedList<Term>();
		vars.addAll(var);
		Function head = ofac.getFunction(predicate, vars);

		Predicate pbody;
		Function bodyAtom;

		List<Term> innerProjection = new LinkedList<Term>();
		innerProjection.addAll(filteredVariables);
		Collections.sort(innerProjection, comparator);

		/***
		 * This is necessary because some filters might apply to variables that
		 * have not been projected yet, for example:
		 * <p>
		 * (filter (= ?x 99) <br>
		 * <t> (bgp (triple <http://example/x> <http://example/p> ?x)))
		 * <p>
		 * in this cases we must project at least the filtered variables from
		 * the nested expressions, otherwise we endup with free variables.
		 * 
		 */

		// TODO here we might be missing the case where there is a filter
		// on a variable that has not been projected out of the inner
		// expressions
		if (vars.size() == 0 && filteredVariables.size() > 0) {
			pbody = ofac.getPredicate("ans" + (i * 2), innerProjection.size());
			bodyAtom = ofac.getFunction(pbody, innerProjection);
		} else {
			pbody = ofac.getPredicate("ans" + (i * 2), vars.size());
			bodyAtom = ofac.getFunction(pbody, vars);
		}
		
		LinkedList<Function> body = new LinkedList<Function>();
		
		TupleExpr sub = filter.getArg();
		if (sub instanceof Extension) { // The filter is HAVING condition
			List <Term> havingTerms = new LinkedList<Term> ();
			havingTerms.addAll(filterAtoms);
			Function havingAtom = ofac.getFunction(OBDAVocabulary.SPARQL_HAVING,havingTerms );
			body.add(bodyAtom);
			body.add(havingAtom);
		} else {
			body.add(bodyAtom);
			body.addAll(filterAtoms);
		}

		CQIE cq = ofac.getCQIE(head, body);
		pr.appendRule(cq);

		

		if (vars.size() == 0 && filteredVariables.size() > 0) {
			List<Variable> newvars = new LinkedList<Variable>();
			for (Term l : innerProjection)
				newvars.add((Variable) l);
			translate(newvars, sub, pr, (i * 2), varcount);
		} else
			translate(var, sub, pr, (i * 2), varcount);

	}

	/***
	 * This translates a single triple. In most cases it will generate one
	 * single atom, however, if URI's are present, it will generate also
	 * equality atoms.
	 * 
	 * @param triple
	 * @return
	 */
	public void translate(List<Variable> vars, StatementPattern triple,
			DatalogProgram pr, long i, int[] varcount) {
		
		Var obj = triple.getObjectVar();
		Var pred = triple.getPredicateVar();
		Var subj = triple.getSubjectVar();
		
		Value o = obj.getValue();
		Value p = pred.getValue();
		Value s = subj.getValue();
		
		if (!(p instanceof URIImpl || (p == null))) {
			// if predicate is a variable or literal
			throw new RuntimeException("Unsupported query syntax");
		}

		LinkedList<Function> result = new LinkedList<Function>();

		// Instantiate the subject and object URI
		String subjectUri = null;
		String objectUri = null;
		String propertyUri = null;

		// Instantiate the subject and object data type
		COL_TYPE subjectType = null;
		COL_TYPE objectType = null;

		// / Instantiate the atom components: predicate and terms.
		Predicate predicate = null;
		Vector<Term> terms = new Vector<Term>();

		if (p instanceof URIImpl && p.toString().equals(RDF.TYPE.stringValue())) {
			// Subject node
			
			terms.add(getOntopTerm(subj, s));
			

			// Object node
			if (o == null) {

				predicate = OBDAVocabulary.QUEST_TRIPLE_PRED;

				Function rdfTypeConstant = ofac.getFunction(ofac
						.getUriTemplatePredicate(1), ofac.getConstantLiteral(OBDAVocabulary.RDF_TYPE));
				terms.add(rdfTypeConstant);
				terms.add(ofac.getVariable(obj.getName()));

			} else if (o instanceof LiteralImpl) {
				throw new RuntimeException("Unsupported query syntax");
			} else if (o instanceof URIImpl) {
				URIImpl object = (URIImpl) o;
				objectUri = object.stringValue();
			}

			// Construct the predicate
			String predicateUri = objectUri;
			if (predicateUri == null) {
				// NO OP, already assigned
			} else if (predicateUri.equals(
					OBDAVocabulary.RDFS_LITERAL_URI)) {
				predicate = OBDAVocabulary.RDFS_LITERAL;
			} else if (predicateUri.equals(
					OBDAVocabulary.XSD_BOOLEAN_URI)) {
				predicate = OBDAVocabulary.XSD_BOOLEAN;
			} else if (predicateUri.equals(
					OBDAVocabulary.XSD_DATETIME_URI)) {
				predicate = OBDAVocabulary.XSD_DATETIME;
			} else if (predicateUri.equals(
					OBDAVocabulary.XSD_DECIMAL_URI)) {
				predicate = OBDAVocabulary.XSD_DECIMAL;
			} else if (predicateUri.equals(
					OBDAVocabulary.XSD_DOUBLE_URI)) {
				predicate = OBDAVocabulary.XSD_DOUBLE;
			} else if (predicateUri.equals(
					OBDAVocabulary.XSD_FLOAT_URI)) {
				predicate = OBDAVocabulary.XSD_FLOAT;
            } else if (predicateUri.equals(
                    OBDAVocabulary.XSD_NEGATIVE_INTEGER)) {
                predicate = OBDAVocabulary.XSD_NEGATIVE_INTEGER;
            } else if (predicateUri.equals(
                    OBDAVocabulary.XSD_NON_NEGATIVE_INTEGER)) {
                predicate = OBDAVocabulary.XSD_NON_NEGATIVE_INTEGER;
            } else if (predicateUri.equals(
                    OBDAVocabulary.XSD_POSITIVE_INTEGER)) {
                predicate = OBDAVocabulary.XSD_POSITIVE_INTEGER;
            } else if (predicateUri.equals(
                    OBDAVocabulary.XSD_NON_POSITIVE_INTEGER)) {
                predicate = OBDAVocabulary.XSD_NON_POSITIVE_INTEGER;
            } else if (predicateUri.equals(
                    OBDAVocabulary.XSD_UNSIGNED_INT)) {
                predicate = OBDAVocabulary.XSD_UNSIGNED_INT;
			} else if (predicateUri.equals(
					OBDAVocabulary.XSD_INT_URI)) {
				predicate = OBDAVocabulary.XSD_INT;
			} else if (predicateUri.equals(
					OBDAVocabulary.XSD_INTEGER_URI)) {
				predicate = OBDAVocabulary.XSD_INTEGER;
			} else if (predicateUri.equals(
                OBDAVocabulary.XSD_LONG_URI)) {
                predicate = OBDAVocabulary.XSD_LONG;
            } else if (predicateUri.equals(
					OBDAVocabulary.XSD_STRING_URI)) {
				predicate = OBDAVocabulary.XSD_STRING;
			} else if (predicateUri.equals(
					OBDAVocabulary.XSD_DATE_URI)) {
				predicate = OBDAVocabulary.XSD_DATE;
			}  else if (predicateUri.equals(
					OBDAVocabulary.XSD_TIME_URI)) {
				predicate = OBDAVocabulary.XSD_TIME;
			} else if (predicateUri.equals(
					OBDAVocabulary.XSD_YEAR_URI)) {
				predicate = OBDAVocabulary.XSD_YEAR;
			} else {

				predicate = ofac.getPredicate(predicateUri, 1,
						new COL_TYPE[] { subjectType });

			}

		} else {
			/*
			 * The predicate is NOT rdf:type
			 */

			terms.add(getOntopTerm(subj, s));

			terms.add(getOntopTerm(obj,o));
			
			// Construct the predicate

			if (p instanceof URIImpl) {
				String predicateUri = p.stringValue();
				predicate = ofac.getPredicate(predicateUri, 2, new COL_TYPE[] {
						subjectType, objectType });
			} else if (p == null) {
				predicate = OBDAVocabulary.QUEST_TRIPLE_PRED;
				terms.add(1, ofac.getVariable(pred.getName()));
			}
		}
		// Construct the atom
		Function atom = ofac.getFunction(predicate, terms);
		result.addFirst(atom);

		// Collections.sort(vars, comparator);
		List<Term> newvars = new LinkedList<Term>();
		for (Variable var : vars) {
			newvars.add(var);
		}

		Predicate answerPred = ofac.getPredicate("ans" + i, vars.size());
		Function head = ofac.getFunction(answerPred, newvars);

		CQIE newrule = ofac.getCQIE(head, result);
		pr.appendRule(newrule);
	}
	
	private Term getOntopTerm(Var subj, Value s) {
		Term result = null;
		if (s == null) {
			result = ofac.getVariable(subj.getName());
		} else if (s instanceof LiteralImpl) {
			LiteralImpl object = (LiteralImpl) s;
			COL_TYPE objectType = getDataType(object);
			ValueConstant constant = getConstant(object);

			// v1.7: We extend the syntax such that the data type of a
			// constant
			// is defined using a functional symbol.
			Function dataTypeFunction = null;
			if (objectType == COL_TYPE.LITERAL) {
				// If the object has type LITERAL, check any language
				// tag!
				String lang = object.getLanguage();
				if (lang != null) lang = lang.toLowerCase();
				Predicate functionSymbol = ofac
						.getDataTypePredicateLiteral();
				Constant languageConstant = null;
				if (lang != null && !lang.equals("")) {
					languageConstant = ofac.getConstantLiteral(lang,
							COL_TYPE.LITERAL);
					dataTypeFunction = ofac.getFunction(
							functionSymbol, constant, languageConstant);
					result = dataTypeFunction;
				} else {
					dataTypeFunction = ofac.getFunction(
							functionSymbol, constant);
					result = dataTypeFunction;
				}
			} else {
				// For other supported data-types
				Predicate functionSymbol = getDataTypePredicate(objectType);
				dataTypeFunction = ofac.getFunction(functionSymbol,
						constant);
				result= dataTypeFunction;
			}
		} else if (s instanceof URIImpl) {
			URIImpl subject = (URIImpl) s;
			COL_TYPE subjectType = COL_TYPE.OBJECT;
			
			String subject_URI = subject.stringValue();
			subject_URI = decodeURIEscapeCodes(subject_URI);
			

			if (uriRef != null) {
				/* if in the Semantic Index mode */
				int id = uriRef.getId(s.stringValue());
				
				result = ofac.getFunction(ofac.getUriTemplatePredicate(1), 
							ofac.getConstantLiteral(String.valueOf(id), COL_TYPE.INTEGER));
			} else {
				result = uriTemplateMatcher.generateURIFunction(subject_URI);
			}
		}
		
		return result;
	}
	
	/***
	 * Given a string representing a URI, this method will return a new String in which all percent encoded characters (e.g., %20) will
	 * be restored to their original characters (e.g., ' '). This is necessary to transform some URIs into the original dtabase values.
	 * @param encodedURI
	 * @return
	 */
	private String decodeURIEscapeCodes(String encodedURI) {
		int length = encodedURI.length();
		StringBuilder strBuilder = new StringBuilder(length+20);
		
		char[] codeBuffer = new char[2];
		
		for (int ci = 0; ci < length; ci++) {
			char c = encodedURI.charAt(ci);

			if (c != '%') {
				// base case, the character is a normal character, just
				// append
				strBuilder.append(c);
				continue;
			}

			/*
			 * found a escape, processing the code and replacing it by
			 * the original value that should be found on the DB. This
			 * should not be used all the time, only when working in
			 * virtual mode... we need to fix this with a FLAG.
			 */

			// First we get the 2 chars next to %
			codeBuffer[0] = encodedURI.charAt(ci + 1);
			codeBuffer[1] = encodedURI.charAt(ci + 2);

			// now we check if they match any of our escape wodes, if
			// they do the char to be inserted is put in codeBuffer
			// otherwise
			String code = String.copyValueOf(codeBuffer);
			if (code.equals("%20")) {
				strBuilder.append(' ');
			} else if (code.equals("%21")) {
				strBuilder.append('!');
			} else if (code.equals("%40")) {
				strBuilder.append('@');
			} else if (code.equals("%23")) {
				strBuilder.append('#');
			} else if (code.equals("%24")) {
				strBuilder.append('$');
			} else if (code.equals("%26")) {
				strBuilder.append('&');
			} else if (code.equals("%42")) {
				strBuilder.append('*');
			} else if (code.equals("%28")) {
				strBuilder.append('(');
			} else if (code.equals("%29")) {
				strBuilder.append(')');
			} else if (code.equals("%5B")) {
				strBuilder.append('[');
			} else if (code.equals("%5C")) {
				strBuilder.append(']');
			} else if (code.equals("%2C")) {
				strBuilder.append(',');
			} else if (code.equals("%3B")) {
				strBuilder.append(';');
			} else if (code.equals("%3A")) {
				strBuilder.append(':');
			} else if (code.equals("%3F")) {
				strBuilder.append('?');
			} else if (code.equals("%3D")) {
				strBuilder.append('=');
			} else if (code.equals("%2B")) {
				strBuilder.append('+');
			} else if (code.equals("%22")) {
				strBuilder.append('\'');
			} else if (code.equals("%2F")) {
				strBuilder.append('/');
			} else {
				// This was not an escape code, so we just append the
				// characters and continue;
				strBuilder.append(codeBuffer);
			}
			ci += 2;

		}
		return strBuilder.toString();

	}
	
	private static class TermComparator implements Comparator<Term> {

		@Override
		public int compare(Term arg0, Term arg1) {
			return arg0.toString().compareTo(arg1.toString());
		}

	}

	public Set<Variable> getVariables(List<org.openrdf.query.algebra.Var> list) {
		Set<Variable> vars = new HashSet<Variable>();
		for (org.openrdf.query.algebra.Var variable : list) {
			if (!variable.hasValue()) { // if it has value, then its a constant
				String name = variable.getName();
				Variable var = ofac.getVariable(name);
				vars.add(var);
			}
		}
		return vars;
	}
	public Set<Variable> getBindVariables(TupleExpr te) {
		Set<String> names = te.getAssuredBindingNames();
		Set<Variable> vars = new HashSet<Variable>();
		for (String name : names) {
				Variable var = ofac.getVariable(name);
				vars.add(var);
			}
		return vars;
	}
	
	public Set<Variable> getBindVariables(List<ExtensionElem> elements) {
		Set<Variable> vars = new HashSet<Variable>();
		for (ExtensionElem el : elements) {
				String name = el.getName();
				Variable var = ofac.getVariable(name);
				vars.add(var);
			}
		return vars;
	}
	
	public Set<Variable> getVariables(TupleExpr te) {
		Set<Variable> result = new LinkedHashSet<Variable>();
		if (te instanceof StatementPattern) {
			result.addAll(getVariables(((StatementPattern) te).getVarList()));
		} else if (te instanceof BinaryTupleOperator) {
			result.addAll(getVariables(((BinaryTupleOperator) te).getLeftArg()));
			result.addAll(getVariables(((BinaryTupleOperator) te).getRightArg()));
		} else if (te instanceof UnaryTupleOperator) {
				if (te instanceof Extension) {
					result.addAll(getBindVariables(((Extension) te).getElements()));
				}
			result.addAll(getVariables(((UnaryTupleOperator) te).getArg()));
		} else {
			throw new RuntimeException("Operator not supported: " + te);
		}
		return result;
	}
	
	//private Variable getFreshVariable(int[] count) {
	//	count[0] += 1;
	//	return ofac.getVariable("VAR" + count[0]);
	//}

	public ValueConstant getConstant(LiteralImpl literal) {
		URI type = literal.getDatatype();
		COL_TYPE objectType = getDataType(literal);
		String value = literal.getLabel();
		ValueConstant constant = ofac.getConstantLiteral(value, objectType);

		/*
		 * Validating that the value is correct (lexically) with respect to the
		 * specified datatype
		 */
		
		boolean valid = true;
		if (type != null) {
			valid = XMLDatatypeUtil.isValidValue(value, type);
		}
		if (!valid)
			throw new RuntimeException(
					"Invalid lexical form for datatype. Found: " + value);
		return constant;

	}


	private Predicate getDataTypePredicate(COL_TYPE dataType)
			throws RuntimeException {
        //we do not consider the case of literal because it has already been checked @see #getOntopTerm
		switch (dataType) {

		case STRING:
			return ofac.getDataTypePredicateString();
		case INTEGER:
			return ofac.getDataTypePredicateInteger();
        case NEGATIVE_INTEGER:
            return ofac.getDataTypePredicateNegativeInteger();
        case INT:
            return ofac.getDataTypePredicateInt();
        case POSITIVE_INTEGER:
            return ofac.getDataTypePredicatePositiveInteger();
        case NON_POSITIVE_INTEGER:
            return ofac.getDataTypePredicateNonPositiveInteger();
        case NON_NEGATIVE_INTEGER:
            return ofac.getDataTypePredicateNonNegativeInteger();
        case UNSIGNED_INT:
            return ofac.getDataTypePredicateUnsignedInt();
        case LONG:
             return ofac.getDataTypePredicateLong();
		case DECIMAL:
			return ofac.getDataTypePredicateDecimal();
		case DOUBLE:
			return ofac.getDataTypePredicateDouble();
        case FLOAT:
            return ofac.getDataTypePredicateFloat();
		case DATETIME:
			return ofac.getDataTypePredicateDateTime();
		case BOOLEAN:
			return ofac.getDataTypePredicateBoolean();
		case DATE:
			return ofac.getDataTypePredicateDate();
		case TIME:
			return ofac.getDataTypePredicateTime();
		case YEAR:
			return ofac.getDataTypePredicateYear();
		default:
			throw new RuntimeException("Unknown data type!");
		}
	}

	private COL_TYPE getDataType(LiteralImpl node) {
		COL_TYPE dataType = null;
		URI typeURI = node.getDatatype();
		// if null return literal, and avoid exception
		if (typeURI == null) return COL_TYPE.LITERAL;
		
		final String dataTypeURI = typeURI.stringValue();

		if (dataTypeURI == null) {
			dataType = COL_TYPE.LITERAL;
		} else {
			if (dataTypeURI.equalsIgnoreCase(OBDAVocabulary.RDFS_LITERAL_URI)) {
				dataType = COL_TYPE.LITERAL;
			} else if (dataTypeURI
					.equalsIgnoreCase(OBDAVocabulary.XSD_STRING_URI)) {
				dataType = COL_TYPE.STRING;
			} else if (dataTypeURI.equalsIgnoreCase(OBDAVocabulary.XSD_INT_URI)){
				dataType = COL_TYPE.INT;
            } else if (dataTypeURI
                    .equalsIgnoreCase(OBDAVocabulary.XSD_INTEGER_URI)) {
                dataType = COL_TYPE.INTEGER;
            } else if (dataTypeURI
                    .equalsIgnoreCase(OBDAVocabulary.XSD_NEGATIVE_INTEGER_URI)) {
                dataType = COL_TYPE.NEGATIVE_INTEGER;
            } else if (dataTypeURI
                    .equalsIgnoreCase(OBDAVocabulary.XSD_NON_NEGATIVE_INTEGER_URI)) {
                dataType = COL_TYPE.NON_NEGATIVE_INTEGER;
            } else if (dataTypeURI
                    .equalsIgnoreCase(OBDAVocabulary.XSD_POSITIVE_INTEGER_URI)) {
                dataType = COL_TYPE.POSITIVE_INTEGER;
            } else if (dataTypeURI
                    .equalsIgnoreCase(OBDAVocabulary.XSD_NON_POSITIVE_INTEGER_URI)) {
                dataType = COL_TYPE.NON_POSITIVE_INTEGER;
            } else if (dataTypeURI
                    .equalsIgnoreCase(OBDAVocabulary.XSD_UNSIGNED_INT_URI)) {
                dataType = COL_TYPE.UNSIGNED_INT;
			} else if (dataTypeURI
                    .equalsIgnoreCase(OBDAVocabulary.XSD_LONG_URI)) {
                dataType = COL_TYPE.LONG;
            }
            else if (dataTypeURI
					.equalsIgnoreCase(OBDAVocabulary.XSD_DECIMAL_URI)) {
				// special case for decimal
				String value = node.getLabel().toString();
				if (value.contains(".")) {
					// Put the type as decimal (with fractions).
					dataType = COL_TYPE.DECIMAL;
				} else {
					// Put the type as integer (decimal without fractions).
					dataType = COL_TYPE.INTEGER;
				}
			} else if (dataTypeURI
					.equalsIgnoreCase(OBDAVocabulary.XSD_FLOAT_URI)) {
				dataType = COL_TYPE.FLOAT;
            } else if (dataTypeURI
                    .equalsIgnoreCase(OBDAVocabulary.XSD_DOUBLE_URI)) {
                dataType = COL_TYPE.DOUBLE;
			} else if (dataTypeURI
					.equalsIgnoreCase(OBDAVocabulary.XSD_DATETIME_URI)) {
				dataType = COL_TYPE.DATETIME;
			} else if (dataTypeURI
					.equalsIgnoreCase(OBDAVocabulary.XSD_BOOLEAN_URI)) {
				dataType = COL_TYPE.BOOLEAN;
			} else if (dataTypeURI
					.equalsIgnoreCase(OBDAVocabulary.XSD_DATE_URI)) {
				dataType = COL_TYPE.DATE;
			} else if (dataTypeURI
					.equalsIgnoreCase(OBDAVocabulary.XSD_TIME_URI)) {
				dataType = COL_TYPE.TIME;
			} else if (dataTypeURI
					.equalsIgnoreCase(OBDAVocabulary.XSD_YEAR_URI)) {
				dataType = COL_TYPE.YEAR;
			} else {
				throw new RuntimeException("Unsupported datatype: "
						+ dataTypeURI.toString());
			}
		}
		return dataType;
	}

	private Term getBooleanTerm(ValueExpr expr) {
		if (expr instanceof Var) {
			return getVariableTerm((Var) expr);
		} else if (expr instanceof org.openrdf.query.algebra.ValueConstant) {
			return getConstantFunctionTerm((org.openrdf.query.algebra.ValueConstant) expr);
		} else if (expr instanceof UnaryValueOperator) {
			return getBuiltinFunctionTerm((UnaryValueOperator) expr);
		} else if (expr instanceof BinaryValueOperator) {
			if (expr instanceof Regex) { // sesame regex is Binary, Jena N-ary
				Regex reg = (Regex) expr;
				ValueExpr arg1 = reg.getLeftArg(); 
				ValueExpr arg2 = reg.getRightArg(); 
				ValueExpr flags = reg.getFlagsArg();
				Term term1 = getBooleanTerm(arg1);
				Term term2 = getBooleanTerm(arg2);
				Term term3 = (flags != null) ? getBooleanTerm(flags) : ofac.getConstantNULL();
				return ofac.getFunction(
						OBDAVocabulary.SPARQL_REGEX, term1, term2, term3);
			}
			BinaryValueOperator function = (BinaryValueOperator) expr;
			ValueExpr arg1 = function.getLeftArg(); // get the first argument
			ValueExpr arg2 = function.getRightArg(); // get the second argument
			Term term1 = getBooleanTerm(arg1);
			Term term2 = getBooleanTerm(arg2);
			// Construct the boolean function
			// TODO Change the method name because ExprFunction2 is not only for
			// boolean functions
			return getBooleanFunction(function, term1, term2);
		} else if (expr instanceof Bound){
			
			return ofac.getFunction(OBDAVocabulary.IS_NOT_NULL, getVariableTerm(((Bound) expr).getArg()));
		} else {
			throw new RuntimeException("The builtin function "
					+ expr.toString() + " is not supported yet!");
		}
	}
	
	//private Function getVariableTermIntoBoolFunction(Var expr) {
	//	return ofac.getFunction(OBDAVocabulary.IS_TRUE, getVariableTerm(expr));
	//}
	
	private Term getVariableTerm(Var expr) {
		return getOntopTerm(expr, expr.getValue());
		
	}

	private Function getConstantFunctionTerm(org.openrdf.query.algebra.ValueConstant expr) {
		Function constantFunction = null;
		Value v = expr.getValue();

		if (v instanceof LiteralImpl) {
			LiteralImpl lit = (LiteralImpl)v;
			URI type = lit.getDatatype();
			if (type == null) {
				return ofac.getFunction(ofac
						.getDataTypePredicateLiteral(), ofac.getConstantLiteral(
						v.stringValue(), COL_TYPE.LITERAL));
			}
			if ( (type == XMLSchema.INTEGER) || type.equals(XMLSchema.INTEGER)) constantFunction = ofac.getFunction(ofac
					.getDataTypePredicateInteger(), ofac.getConstantLiteral(
							lit.integerValue() + "", COL_TYPE.INTEGER));
            else if ( (type == XMLSchema.LONG) || type.equals(XMLSchema.LONG)) constantFunction = ofac.getFunction(ofac
                    .getDataTypePredicateLong(), ofac.getConstantLiteral(
                    lit.longValue() + "", COL_TYPE.LONG));
			else if ((type == XMLSchema.DECIMAL)  || type.equals(XMLSchema.DECIMAL)) constantFunction = ofac.getFunction(ofac
					.getDataTypePredicateDecimal(), ofac.getConstantLiteral(
							lit.decimalValue() + "", COL_TYPE.DECIMAL));
            else if ((type == XMLSchema.DECIMAL)  || type.equals(XMLSchema.DECIMAL)) constantFunction = ofac.getFunction(ofac
                    .getDataTypePredicateDecimal(), ofac.getConstantLiteral(
                    lit.decimalValue() + "", COL_TYPE.DECIMAL));
			else if ((type == XMLSchema.DOUBLE) || type.equals(XMLSchema.DOUBLE)) constantFunction = ofac.getFunction(ofac
					.getDataTypePredicateDouble(), ofac.getConstantLiteral(
							lit.doubleValue() + "", COL_TYPE.DOUBLE));
            else if ((type == XMLSchema.FLOAT) || type.equals(XMLSchema.FLOAT)) constantFunction = ofac.getFunction(ofac
                    .getDataTypePredicateFloat(), ofac.getConstantLiteral(
                    lit.floatValue() + "", COL_TYPE.FLOAT));
            else if ((type == XMLSchema.NEGATIVE_INTEGER) || type.equals(XMLSchema.NEGATIVE_INTEGER)) constantFunction = ofac.getFunction(ofac
                    .getDataTypePredicateNegativeInteger(), ofac.getConstantLiteral(
                    lit.integerValue() + "", COL_TYPE.NEGATIVE_INTEGER));
            else if ((type == XMLSchema.NON_POSITIVE_INTEGER) || type.equals(XMLSchema.NON_POSITIVE_INTEGER)) constantFunction = ofac.getFunction(ofac
                    .getDataTypePredicateNonPositiveInteger(), ofac.getConstantLiteral(
                    lit.integerValue() + "", COL_TYPE.NON_POSITIVE_INTEGER));
            else if ((type == XMLSchema.INT) || type.equals(XMLSchema.INT)) constantFunction = ofac.getFunction(ofac
                    .getDataTypePredicateInt(), ofac.getConstantLiteral(
                    lit.intValue() + "", COL_TYPE.INT));
            else if ((type == XMLSchema.NON_NEGATIVE_INTEGER) || type.equals(XMLSchema.NON_NEGATIVE_INTEGER)) constantFunction = ofac.getFunction(ofac
                    .getDataTypePredicateNonNegativeInteger(), ofac.getConstantLiteral(
                    lit.integerValue() + "", COL_TYPE.NON_NEGATIVE_INTEGER));
            else if ((type == XMLSchema.POSITIVE_INTEGER) || type.equals(XMLSchema.POSITIVE_INTEGER)) constantFunction = ofac.getFunction(ofac
                    .getDataTypePredicatePositiveInteger(), ofac.getConstantLiteral(
                    lit.integerValue() + "", COL_TYPE.POSITIVE_INTEGER));
            else if ((type == XMLSchema.UNSIGNED_INT) || type.equals(XMLSchema.UNSIGNED_INT)) constantFunction = ofac.getFunction(ofac
                    .getDataTypePredicateUnsignedInt(), ofac.getConstantLiteral(
                    lit.intValue() + "", COL_TYPE.UNSIGNED_INT));
			else if ((type == XMLSchema.DATETIME) || type.equals(XMLSchema.DATETIME)) 
				constantFunction = ofac.getFunction(ofac.getDataTypePredicateDateTime(), ofac.getConstantLiteral(
						lit.calendarValue() + "", COL_TYPE.DATETIME));
            else if ((type == XMLSchema.GYEAR) || type.equals(XMLSchema.GYEAR))
                constantFunction = ofac.getFunction(ofac.getDataTypePredicateYear(), ofac.getConstantLiteral(
                        lit.calendarValue() + "", COL_TYPE.YEAR));
            else if ((type == XMLSchema.DATE) || type.equals(XMLSchema.DATE))
                constantFunction = ofac.getFunction(ofac.getDataTypePredicateDate(), ofac.getConstantLiteral(
                        lit.calendarValue() + "", COL_TYPE.DATE));
            else if ((type == XMLSchema.TIME) || type.equals(XMLSchema.TIME))
                constantFunction = ofac.getFunction(ofac.getDataTypePredicateTime(), ofac.getConstantLiteral(
                        lit.calendarValue() + "", COL_TYPE.TIME));
			else if ((type == XMLSchema.BOOLEAN) || type.equals(XMLSchema.BOOLEAN)) constantFunction = ofac.getFunction(ofac
					.getDataTypePredicateBoolean(), ofac.getConstantLiteral(
							lit.booleanValue() + "", COL_TYPE.BOOLEAN));
			else if ((type == XMLSchema.STRING) || type.equals(XMLSchema.STRING)) constantFunction = ofac.getFunction(ofac
					.getDataTypePredicateString(), ofac.getConstantLiteral(
							lit.stringValue() + "", COL_TYPE.STRING));
			else if ((type == RDFS.LITERAL) || type.equals(RDFS.LITERAL)) constantFunction = ofac.getFunction(ofac
					.getDataTypePredicateLiteral(), ofac.getConstantLiteral(
							lit.stringValue() + "", COL_TYPE.LITERAL));
			else {
				// its some custom type
					constantFunction = ofac.getFunction(ofac
							.getUriTemplatePredicate(1), ofac.getConstantLiteral(
							type.toString(), COL_TYPE.OBJECT));
			}
		} else if (v instanceof URIImpl) {

                constantFunction = uriTemplateMatcher.generateURIFunction(v.stringValue());
                if (constantFunction.getArity() == 1)
                    constantFunction = ofac.getFunction(ofac
                            .getUriTemplatePredicate(1), ofac.getConstantLiteral(
                            ((URIImpl) v).stringValue(), COL_TYPE.OBJECT));

			
		}
		
		return constantFunction;
	}

	private Function getBuiltinFunctionTerm(UnaryValueOperator expr) {
		Function builtInFunction = null;
		if (expr instanceof Not) {
			ValueExpr arg = expr.getArg();
			Term term = getBooleanTerm(arg);
			builtInFunction = ofac.getFunction(OBDAVocabulary.NOT, term);
		}
		/*
		 * The following expressions only accept variable as the parameter
		 */

		else if (expr instanceof IsLiteral) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_IS_LITERAL, getBooleanTerm( expr.getArg()));
			
		} else if (expr instanceof IsURI) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_IS_URI, getBooleanTerm( expr.getArg()));
			
		} else if (expr instanceof Str) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_STR, getBooleanTerm( expr.getArg()));
			
		} else if (expr instanceof Datatype) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_DATATYPE, getBooleanTerm( expr.getArg()));
		
		} else if (expr instanceof IsBNode) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_IS_BLANK, getBooleanTerm( expr.getArg()));
							
		} else if (expr instanceof Lang) {
			ValueExpr arg = expr.getArg();
			if (arg instanceof Var) {
				builtInFunction = ofac.getFunction(
						OBDAVocabulary.SPARQL_LANG,
						getVariableTerm((Var) arg));
			}
		} else if (expr instanceof Count) {
			if (expr.getArg() != null) {
				Function function = ofac.getFunction(OBDAVocabulary.SPARQL_COUNT, getBooleanTerm( expr.getArg()));
				builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateInteger(),function);
			} else { // Its COUNT(*)
				
				Function function = ofac.getFunction(OBDAVocabulary.SPARQL_COUNT, ofac.getVariable("*"));
				builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateInteger(),function);
			}
		} else if (expr instanceof Avg) {
			
			builtInFunction  = ofac.getFunction(OBDAVocabulary.SPARQL_AVG, getBooleanTerm( expr.getArg()));
			//builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);
			
		} else if (expr instanceof Sum) {
			builtInFunction  =  ofac.getFunction(OBDAVocabulary.SPARQL_SUM, getBooleanTerm( expr.getArg()));
			//builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);
			
		} else if (expr instanceof Min) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_MIN, getBooleanTerm( expr.getArg()));
			//builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);
			
		} else if (expr instanceof Max) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_MAX, getBooleanTerm( expr.getArg()));
			//builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);
			
		} 
		else {
			throw new RuntimeException("The builtin function "
					+ expr.toString() + " is not supported yet!");
		}
		return builtInFunction;
	}

	private Function getBooleanFunction(BinaryValueOperator expr, Term term1,
			Term term2) {
		Function function = null;
		// The AND and OR expression
		if (expr instanceof And) {
			function = ofac.getFunctionAND(term1, term2);
		} else if (expr instanceof Or) {
			function = ofac.getFunctionOR(term1, term2);
		}
		// The other expressions
		else if (expr instanceof SameTerm){
			function = ofac.getFunctionEQ(term1, term2);
		} else if (expr instanceof Compare) {
			CompareOp operator = ((Compare) expr).getOperator();
			if (operator == Compare.CompareOp.EQ)
				function = ofac.getFunctionEQ(term1, term2);
			else if (operator == Compare.CompareOp.GE)
				function = ofac.getFunctionGTE(term1, term2);
			else if (operator == Compare.CompareOp.GT)
				function = ofac.getFunctionGT(term1, term2);
			else if (operator == Compare.CompareOp.LE)
				function = ofac.getFunctionLTE(term1, term2);
			else if (operator == Compare.CompareOp.LT)
				function = ofac.getFunctionLT(term1, term2);
			else if (operator == Compare.CompareOp.NE)
				function = ofac.getFunctionNEQ(term1, term2);
		} else if (expr instanceof MathExpr) {
			MathOp mop = ((MathExpr)expr).getOperator();
			if (mop == MathOp.PLUS) 
				function = ofac.getFunctionAdd(term1, term2);
			else if (mop == MathOp.MINUS)
				function = ofac.getFunctionSubstract(term1, term2);
			else if (mop == MathOp.MULTIPLY) 
				function = ofac.getFunctionMultiply(term1, term2);
		} else if (expr instanceof LangMatches) {
			function = ofac.getLANGMATCHESFunction(term1, toLowerCase(term2));
		} else {
			throw new IllegalStateException("getBooleanFunction does not understand the expression " + expr);
		}
		return function;
	}

	private Term toLowerCase(Term term) {
		Term output = term;
		if (term instanceof Function) {
			Function f = (Function) term;
			Predicate functor = f.getFunctionSymbol();
			if (functor instanceof DataTypePredicate) {
				Term functionTerm = f.getTerm(0);
				if (functionTerm instanceof Constant) {
					Constant c = (Constant) functionTerm;
					output = ofac.getFunction(functor, 
							 ofac.getConstantLiteral(c.getValue().toLowerCase(), 
							 c.getType()));
				}
			}
		}
		return output;
	}
	
	public void getSignature(ParsedQuery query, List<String> signatureContainer) {
		signatureContainer.clear();
		if (query instanceof ParsedTupleQuery || query instanceof ParsedGraphQuery) {
			TupleExpr te = query.getTupleExpr();
			signatureContainer.addAll(te.getBindingNames());
		}
	}
	
//	public void getSignature(Query query, List<String> signatureContainer) {
//		signatureContainer.clear();
//		if (query.isSelectType() || query.isDescribeType()) {
//			signatureContainer.addAll(query.getResultVars());
//
//		} else if (query.isConstructType()) {
//			Template constructTemplate = query.getConstructTemplate();
//			for (Triple triple : constructTemplate.getTriples()) {
//				/*
//				 * Check if the subject, predicate, object is a variable.
//				 */
//				Node subject = triple.getSubject(); // subject
//				if (subject instanceof com.hp.hpl.jena.sparql.core.Var) {
//					String vs = ((com.hp.hpl.jena.sparql.core.Var) subject).getName();
//					signatureContainer.add(vs);
//				}
//				Node predicate = triple.getPredicate(); // predicate
//				if (predicate instanceof com.hp.hpl.jena.sparql.core.Var) {
//					String vs = ((com.hp.hpl.jena.sparql.core.Var) predicate).getName();
//					signatureContainer.add(vs);
//				}
//				Node object = triple.getObject(); // object
//				if (object instanceof com.hp.hpl.jena.sparql.core.Var) {
//					String vs = ((com.hp.hpl.jena.sparql.core.Var) object).getName();
//					signatureContainer.add(vs);
//				}
//			}
//		}
//	}

	public void setSemanticIndexUriRef(SemanticIndexURIMap uriRef) {
		this.uriRef = uriRef;
	}
}
