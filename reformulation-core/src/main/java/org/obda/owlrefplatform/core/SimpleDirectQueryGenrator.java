package org.obda.owlrefplatform.core;

import inf.unibz.it.obda.api.io.PrefixManager;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.obda.query.domain.Atom;
import org.obda.query.domain.CQIE;
import org.obda.query.domain.Constant;
import org.obda.query.domain.DatalogProgram;
import org.obda.query.domain.Term;
import org.obda.query.domain.Variable;
import org.obda.query.domain.imp.UndistinguishedVariable;
import org.obda.reformulation.domain.DLLiterOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The implementation of the sql generator for the direct mapping approach
 * 
 * @author Manfred Gerstgrasser
 * 
 */

public class SimpleDirectQueryGenrator implements SourceQueryGenerator {

	private SimpleDirectViewManager			viewmanager	= null;
	private DLLiterOntology					ontology	= null;
	private HashMap<String, List<Object[]>>	termMap		= null;
	private HashMap<String, List<Object[]>>	constMap	= null;
	private Map<Atom, String>				aliasMapper	= null;
	private PrefixManager					manager		= null;
	private int								counter		= 1;
	
	Logger log = LoggerFactory.getLogger(SimpleDirectQueryGenrator.class);

	public SimpleDirectQueryGenrator(PrefixManager man, DLLiterOntology onto, Set<URI> uris) {

		ontology = onto;
		manager = man;
		viewmanager = new SimpleDirectViewManager(manager, ontology, uris);
		aliasMapper = new HashMap<Atom, String>();
	}

	/**
	 * Generates the final sql query for the given datalog program
	 */
	public String generateSourceQuery(DatalogProgram query) throws Exception {
		log.debug("Simple SQL generator. Generating SQL query for input query: \n\n{}\n\n", query);
		StringBuffer sb = new StringBuffer();
		List<CQIE> rules = query.getRules();
		Iterator<CQIE> it = rules.iterator();
		while (it.hasNext()) {
			if (sb.length() > 0) {
				sb.append("\n");
				sb.append("UNION");
				sb.append("\n");
			}
			if (isDPBoolean(query)) {
				sb.append("(");
				sb.append(generateSourceQuery(it.next()));
				sb.append(")");
			} else {
				sb.append(generateSourceQuery(it.next()));
			}
		}
		String output = sb.toString();
		log.debug("SQL query generated: \n\n{}\n\n", output);
		return output;
	}

	/**
	 * generates the sql query for a single CQIE
	 * 
	 * @param query
	 *            the SQIE
	 * @return the sql query for it
	 * @throws Exception
	 */
	private String generateSourceQuery(CQIE query) throws Exception {

		createAuxIndex(query);
		StringBuffer sb = new StringBuffer();
		String fromclause = generateFromClause(query);
		sb.append("SELECT ");
		sb.append(generateSelectClause(query));
		sb.append(" FROM ");
		sb.append(fromclause);
		String wc = generateWhereClause(query);
		if (wc.length() > 0) {
			sb.append(" WHERE ");
			sb.append(wc);
		}
		if (query.isBoolean()) {
			sb.append(" LIMIT 1");
		}
		return sb.toString();

	}

	/**
	 * Generates the select clause for the given query
	 * 
	 * @param query
	 *            the CQIE
	 * @return the select clause
	 * @throws Exception
	 */
	private String generateSelectClause(CQIE query) throws Exception {
		StringBuffer sb = new StringBuffer();
		if (query.isBoolean()) {
			sb.append("TRUE AS x ");
		} else {
			Atom head = query.getHead();
			List<Term> headterms = head.getTerms();
			Iterator<Term> it = headterms.iterator();
			while (it.hasNext()) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				Term t = it.next();
				List<Object[]> list = termMap.get(t.getName());
				if (list != null && list.size() > 0) {
					Object[] obj = list.get(0);
					String table = aliasMapper.get(((Atom) obj[0]));
					String column = "term" + obj[1];
					StringBuffer aux = new StringBuffer();
					aux.append(table);
					aux.append(".");
					aux.append(column);
					aux.append(" as ");
					aux.append(t.getName());
					sb.append(aux.toString());
				}
			}
		}

		return sb.toString();
	}

	/**
	 * Generates the from clause for the given query
	 * 
	 * @param query
	 *            the CQIE
	 * @return the from clause
	 * @throws Exception
	 */
	private String generateFromClause(CQIE query) throws Exception {
		StringBuffer sb = new StringBuffer();

		List<Atom> body = query.getBody();
		Iterator<Atom> it = body.iterator();
		while (it.hasNext()) {
			Atom a = it.next();
			if (sb.length() > 0) {
				sb.append(", ");
			}
			String table = viewmanager.getTranslatedName(a);
			String tablealias = getAlias();
			aliasMapper.put(a, tablealias);
			sb.append(table + " as " + tablealias);
		}
		return sb.toString();
	}

	/**
	 * Generates the where clause for the given query
	 * 
	 * @param query
	 *            the CQIE
	 * @return the where clause
	 * @throws Exception
	 */
	private String generateWhereClause(CQIE query) throws Exception {
		StringBuffer sb = new StringBuffer();

		Set<String> keys = termMap.keySet();
		Iterator<String> it = keys.iterator();
		HashSet<String> equalities = new HashSet<String>();
		while (it.hasNext()) {
			List<Object[]> list = termMap.get(it.next());
			if (list != null && list.size() > 1) {
				Object[] obj1 = list.get(0);
				String t1 = aliasMapper.get(((Atom) obj1[0]));
				String col1 = "term" + obj1[1].toString();
				for (int i = 1; i < list.size(); i++) {
					Object[] obj2 = list.get(i);
					String t2 = aliasMapper.get(((Atom) obj2[0]));
					String col2 = "term" + obj2[1].toString();
					StringBuffer aux = new StringBuffer();
					aux.append(t1);
					aux.append(".");
					aux.append(col1);
					aux.append("=");
					aux.append(t2);
					aux.append(".");
					aux.append(col2);
					equalities.add(aux.toString());
				}
			}
		}

		Iterator<String> it2 = constMap.keySet().iterator();
		while (it2.hasNext()) {
			String con = it2.next();
			List<Object[]> list = constMap.get(con);
			for (int i = 0; i < list.size(); i++) {
				Object[] obj2 = list.get(i);
				String t2 = aliasMapper.get(((Atom) obj2[0]));
				String col2 = "term" + obj2[1].toString();
				StringBuffer aux = new StringBuffer();
				aux.append(t2);
				aux.append(".");
				aux.append(col2);
				aux.append("='");
				aux.append(con);
				aux.append("'");
				equalities.add(aux.toString());
			}
		}

		Iterator<String> equ_it = equalities.iterator();
		while (equ_it.hasNext()) {
			if (sb.length() > 0) {
				sb.append(" AND ");
			}
			sb.append(equ_it.next());
		}
		return sb.toString();
	}

	/**
	 * creates the term and constant occurrence index, which will simplify the
	 * translation for the given CQIE
	 * 
	 * @param the
	 *            CQIE
	 */
	private void createAuxIndex(CQIE q) {

		termMap = new HashMap<String, List<Object[]>>();
		constMap = new HashMap<String, List<Object[]>>();
		List<Atom> body = q.getBody();
		Iterator<Atom> it = body.iterator();
		while (it.hasNext()) {
			Atom atom = it.next();
			List<Term> terms = atom.getTerms();
			int pos = 0;
			Iterator<Term> term_it = terms.iterator();
			while (term_it.hasNext()) {
				Term t = term_it.next();
				if (t instanceof Variable) {
					if (!(t instanceof UndistinguishedVariable)) {
						Object[] obj = new Object[2];
						obj[0] = atom;
						obj[1] = pos;
						List<Object[]> list = termMap.get(t.getName());
						if (list == null) {
							list = new Vector<Object[]>();
						}
						list.add(obj);
						termMap.put(t.getName(), list);
					}
				} else if (t instanceof Constant) {
					Object[] obj = new Object[2];
					obj[0] = atom;
					obj[1] = pos;
					List<Object[]> list = constMap.get(t.getName());
					if (list == null) {
						list = new Vector<Object[]>();
					}
					list.add(obj);
					constMap.put(t.getName(), list);
				}
				pos++;
			}
		}
	}

	@Override
	public void update(PrefixManager man, DLLiterOntology onto, Set<URI> uris) {
		this.ontology = onto;
		viewmanager = new SimpleDirectViewManager(man, onto, uris);

	}

	private String getAlias() {
		return "t_" + counter++;
	}

	@Override
	public ViewManager getViewManager() {
		return viewmanager;
	}

	private boolean isDPBoolean(DatalogProgram dp) {

		List<CQIE> rules = dp.getRules();
		Iterator<CQIE> it = rules.iterator();
		boolean bool = true;
		while (it.hasNext() && bool) {
			CQIE query = it.next();
			Atom a = query.getHead();
			if (a.getTerms().size() != 0) {
				bool = false;
			}
		}
		return bool;
	}
}
