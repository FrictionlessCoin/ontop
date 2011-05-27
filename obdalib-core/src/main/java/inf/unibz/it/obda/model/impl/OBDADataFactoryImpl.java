package inf.unibz.it.obda.model.impl;

import inf.unibz.it.obda.model.Atom;
import inf.unibz.it.obda.model.CQIE;
import inf.unibz.it.obda.model.DataSource;
import inf.unibz.it.obda.model.DatalogProgram;
import inf.unibz.it.obda.model.Function;
import inf.unibz.it.obda.model.OBDADataFactory;
import inf.unibz.it.obda.model.OBDAModel;
import inf.unibz.it.obda.model.Predicate;
import inf.unibz.it.obda.model.Term;
import inf.unibz.it.obda.model.URIConstant;
import inf.unibz.it.obda.model.ValueConstant;
import inf.unibz.it.obda.model.Variable;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.sun.msv.datatype.xsd.XSDatatype;


public class OBDADataFactoryImpl  implements OBDADataFactory {

	private static OBDADataFactoryImpl instance = null;

	protected OBDADataFactoryImpl(){
		// protected constructor prevents instantiation from other classes.
	}

	public static OBDADataFactory getInstance(){
		if(instance == null){
			instance = new OBDADataFactoryImpl();
		}
		return instance;
	}
	
	public OBDAModel getOBDAModel() {
		return new OBDAModelImpl();
	}

	public PredicateImpl createPredicate(URI name, int arity) {
		return new PredicateImpl(name, arity);
	}
	
	@Override
	public URIConstant createURIConstant(URI uri) {
		return new URIConstantImpl(uri);
	}

	@Override
	public ValueConstant createValueConstant(String value) {
		return new ValueConstantImpl(value, null);
	}

	@Override
	public ValueConstant createValueConstant(String value, XSDatatype type) {
		return new ValueConstantImpl(value, type);
	}

	@Override
	public Variable createVariable(String name) {
		return new VariableImpl(name, null);
	}

	@Override
	public Variable createVariable(String name, XSDatatype type) {
		return new VariableImpl(name, type);
	}

	@Override
	public Variable createUndistinguishedVariable() {
		return new UndistinguishedVariable();
	}

	@Override
	public Function createFunctionalTerm(Predicate functor, List<Term> arguments){
		return new FunctionalTermImpl(functor, arguments);
	}
	
	@Override
	public Function createFunctionalTerm(Predicate functor, Term term1){
		return new FunctionalTermImpl(functor, Collections.singletonList(term1));
	}
	
	@Override
	public Function createFunctionalTerm(Predicate functor, Term term1, Term term2){
		LinkedList<Term> terms = new LinkedList<Term>();
		terms.add(term1);
		terms.add(term2);
		return new FunctionalTermImpl(functor, terms);
	}

	@Override
	public DataSource getDataSource(URI id) {
		return new DataSourceImpl(id);
	}

	@Override
	public Atom getAtom(Predicate predicate, List<Term> terms) {
		return new AtomImpl(predicate, terms);
	}
	
	@Override
	public Atom getAtom(Predicate predicate, Term term1) {
		return new AtomImpl(predicate, Collections.singletonList(term1));
	}
	
	@Override
	public Atom getAtom(Predicate predicate, Term term1, Term term2) {
		LinkedList<Term> terms = new LinkedList<Term>();
		terms.add(term1);
		terms.add(term2);
		return new AtomImpl(predicate, terms);
	}

	@Override
	public CQIE getCQIE(Atom head, List<Atom> body) {
		return new CQIEImpl( head,  body);
	}
	
	@Override
	public CQIE getCQIE(Atom head, Atom body) {
		return new CQIEImpl( head,  Collections.singletonList(body));
	}

	@Override
	public DatalogProgram getDatalogProgram() {
		return new DatalogProgramImpl();
	}
	
	@Override
	public DatalogProgram getDatalogProgram(CQIE rule) {
		DatalogProgram p = new DatalogProgramImpl();
		p.appendRule(rule);
		return p;
	}
	
	@Override
	public DatalogProgram getDatalogProgram(List<CQIE> rules) {
		DatalogProgram p = new DatalogProgramImpl();
		p.appendRule(rules);
		return p;
	}

}
