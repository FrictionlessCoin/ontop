package org.obda.reformulation.dllite;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.obda.query.domain.Atom;
import org.obda.query.domain.CQIE;
import org.obda.query.domain.DatalogProgram;
import org.obda.query.domain.Query;
import org.obda.query.domain.imp.DatalogProgramImpl;
import org.obda.reformulation.domain.Assertion;
import org.obda.reformulation.domain.PositiveInclusion;
import org.obda.reformulation.domain.imp.ApplicabilityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DLRPerfectReformulator implements QueryRewriter {

	private QueryAnonymizer				anonymizer		= null;
	private AtomUnifier					unifier			= null;
	private PositiveInclusionApplicator	piApplicator	= null;
	private List<Assertion>				assertions		= null;
	ApplicabilityChecker				checker			= new ApplicabilityChecker();

	Logger								log				= LoggerFactory.getLogger(DLRPerfectReformulator.class);

	public DLRPerfectReformulator(List<Assertion> ass) {
		this.assertions = ass;
		piApplicator = new PositiveInclusionApplicator();
		unifier = new AtomUnifier();
		anonymizer = new QueryAnonymizer();
	}

	/***
	 * Reformulates the query. Internally, the queries are stored in a List,
	 * however, a HashSet is used in parallel to detect when new queries are
	 * being generated. In the HashSet we store the Integer that identifies the
	 * query's string (getHash).
	 * 
	 * 
	 * @param q
	 * @return
	 * @throws Exception
	 */
	private DatalogProgram reformulate(DatalogProgram q) throws Exception {

		DatalogProgramImpl prog = new DatalogProgramImpl();
		List<CQIE> queries = q.getRules();
		prog.appendRule(queries);
		HashSet<Integer> newRules = new HashSet<Integer>();
		boolean loopagain = true;
		while (loopagain) {
			loopagain = false;
			Iterator<CQIE> it = queries.iterator();
			LinkedList<CQIE> newSet = new LinkedList<CQIE>();
			while (it.hasNext()) {
				CQIE cqie = it.next();
				newRules.add(cqie.hashCode());
				List<Atom> body = cqie.getBody();
				Iterator<Atom> bit = body.iterator();
				// Part A
				while (bit.hasNext()) {
					Atom currentAtom = bit.next();
					Iterator<Assertion> ait = assertions.iterator();
					while (ait.hasNext()) {
						Assertion ass = ait.next();
						if (ass instanceof PositiveInclusion) {
							PositiveInclusion pi = (PositiveInclusion) ass;
							if (checker.isPIApplicable(pi, currentAtom)) {
								CQIE newquery = piApplicator.applyPI(cqie, pi);
								if (newRules.add(newquery.hashCode())) {
									newSet.add(newquery);
									loopagain = true;
								}
							}
						}
					}
				}
				// Part B unification
				for (int i = 0; i < body.size(); i++) {
					for (int j = i + 1; j < body.size(); j++) {
						if (i != j) {
							CQIE newQuery = unifier.unify(cqie, i, j);
							if (newQuery != null) {
								newSet.add(anonymizer.anonymize(newQuery));
								loopagain = true;
							}
						}
					}
				}

			}
			// prog.appendRule(newSet);
			queries = newSet;
			prog.appendRule(queries);
		}
		return prog;
	}

	// if not an instance of DatalogProgramImpl or if not
	// a UCQ then return invalid argument exception
	// if a predicate in the query is not a special predicate and is not
	// in the factory, then
	// reformulates according to PerfectRef
	// #############################

	public Query rewrite(Query input) throws Exception {

		if (!(input instanceof DatalogProgram)) {
			throw new Exception("Rewriting exception: The input must be a DatalogProgram instance");
		}

		DatalogProgram prog = (DatalogProgram) input;

		log.info("Starting query rewrting. Received query: \n{}", prog.toString());
		
		if (!prog.isUCQ()) {
			throw new Exception("Rewriting exception: The input is not a valid union of conjuctive queries");
		}

		/* Query preprocessing */
		log.info("Anonymizing the query");
		QueryAnonymizer ano = new QueryAnonymizer();
		DatalogProgram anonymizedProgram = ano.anonymize(prog);
		
		log.debug("Reformulating");
		DatalogProgram reformulation = reformulate(anonymizedProgram);
		log.debug("Done reformulating. Output: \n{}", reformulation.toString());
		
		return reformulation;

	}

	@Override
	public void updateAssertions(List<Assertion> ass) {

		this.assertions = ass;
	}

}
