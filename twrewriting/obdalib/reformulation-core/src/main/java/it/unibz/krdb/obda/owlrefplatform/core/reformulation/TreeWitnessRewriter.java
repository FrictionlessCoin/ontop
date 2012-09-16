package it.unibz.krdb.obda.owlrefplatform.core.reformulation;

import it.unibz.krdb.obda.model.Atom;
import it.unibz.krdb.obda.model.CQIE;
import it.unibz.krdb.obda.model.DatalogProgram;
import it.unibz.krdb.obda.model.OBDADataFactory;
import it.unibz.krdb.obda.model.OBDAException;
import it.unibz.krdb.obda.model.OBDAQuery;
import it.unibz.krdb.obda.model.Predicate;
import it.unibz.krdb.obda.model.Term;
import it.unibz.krdb.obda.model.impl.AnonymousVariable;
import it.unibz.krdb.obda.model.impl.BooleanOperationPredicateImpl;
import it.unibz.krdb.obda.model.impl.OBDADataFactoryImpl;
import it.unibz.krdb.obda.ontology.Axiom;
import it.unibz.krdb.obda.ontology.BasicClassDescription;
import it.unibz.krdb.obda.ontology.ClassDescription;
import it.unibz.krdb.obda.ontology.OClass;
import it.unibz.krdb.obda.ontology.Ontology;
import it.unibz.krdb.obda.ontology.OntologyFactory;
import it.unibz.krdb.obda.ontology.Property;
import it.unibz.krdb.obda.ontology.PropertySomeRestriction;
import it.unibz.krdb.obda.ontology.impl.OntologyFactoryImpl;
import it.unibz.krdb.obda.owlrefplatform.core.basicoperations.CQCUtilities;
import it.unibz.krdb.obda.utils.QueryUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeWitnessRewriter implements QueryRewriter {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static OBDADataFactory fac = OBDADataFactoryImpl.getInstance();
	private static OntologyFactory ontFactory = OntologyFactoryImpl.getInstance();
	
	private static final Logger log = LoggerFactory.getLogger(TreeWitnessRewriter.class);

	private TreeWitnessReasonerLite reasoner = new TreeWitnessReasonerLite();
	
	private Map<Predicate, Predicate> extPredicateMap = new HashMap<Predicate, Predicate>();
	private Map<Predicate, List<CQIE>> extPredicateDP = new HashMap<Predicate, List<CQIE>>();
	
	private Map<TreeWitnessGenerator, List<Atom>> genconPredicateDP = new HashMap<TreeWitnessGenerator, List<Atom>>();
	
	private Ontology sigma = null;
	
	@Override
	public void setTBox(Ontology ontology) {
		double startime = System.currentTimeMillis();

		reasoner.setTBox(ontology);
		
		extPredicateMap.clear();
		extPredicateDP.clear();
		genconPredicateDP.clear();
		
		double endtime = System.currentTimeMillis();
		double tm = (endtime - startime) / 1000;
		time += tm;
		log.debug(String.format("setTBox time: %.3f s (total %.3f s)", tm, time));
	}
	
	@Override
	public void setCBox(Ontology sigma) {
		log.debug("SET SIGMA");
		this.sigma = sigma;
		for (Axiom ax : sigma.getAssertions()) {
			log.debug("SIGMA: " + ax);
		}
		extPredicateMap.clear();
		extPredicateDP.clear();
		genconPredicateDP.clear();
	}

	@Override
	public void initialize() {
		// TODO Auto-generated method stub
	}
	
	/*
	 *  EXT atoms cache
	 */

	private  Atom getExtAtom(Atom a, Set<Predicate> usedExts) throws URISyntaxException {
		if (a.getArity() == 1)
			return getExtAtom(a.getPredicate(), a.getTerm(0), usedExts);
		else
			return getExtAtom(a.getPredicate(), a.getTerm(0), a.getTerm(1), usedExts);
	}

	private  Atom getExtAtom(Atom a, Term r0, Set<Predicate> usedExts) throws URISyntaxException {
		if (a.getArity() == 1)
			return getExtAtom(a.getPredicate(), r0, usedExts);
		else {
			// assert(a.getArity() == 2);
			Term t0 = ((a.getTerm(0) instanceof AnonymousVariable) ? fac.getNondistinguishedVariable() : r0);
			Term t1 = ((a.getTerm(1) instanceof AnonymousVariable) ? fac.getNondistinguishedVariable() : r0);
			return getExtAtom(a.getPredicate(), t0, t1, usedExts);
		}
	}

	private Atom getExtAtom(Predicate p, Term t, Set<Predicate> usedExts) throws URISyntaxException {
		Predicate ext = extPredicateMap.get(p);
		if (ext == null) {
			 List<CQIE> dp = new ArrayList<CQIE>(10);
			 ext = fac.getClassPredicate(getEXTname(p.getName()));
			 Term x = fac.getVariable("x");
			 Term y = fac.getNondistinguishedVariable(); // fac.getVariable("y");
			 Atom extAtom = fac.getAtom(ext, x); 		  					 
			 for (ClassDescription subc : reasoner.getSubConcepts(p)) 
				 if (subc instanceof OClass) 
					 dp.add(fac.getCQIE(extAtom, fac.getAtom(((OClass)subc).getPredicate(), x)));
				 else if (subc instanceof PropertySomeRestriction) {
					 PropertySomeRestriction twg = (PropertySomeRestriction)subc;
					 dp.add(fac.getCQIE(extAtom, (!twg.isInverse()) ? 
								fac.getAtom(twg.getPredicate(), x, y) : fac.getAtom(twg.getPredicate(), y, x))); 
				 }					 
			 log.debug("DP FOR " + p + " IS " + dp);
			 if (dp.size() > 1) {			 
				 dp = CQCUtilities.removeContainedQueries(dp, true, sigma, false);
				 log.debug("SIMPLIFIED DP FOR " + p + " IS " + dp);
			 }
			 if (dp.size() == 1) 
				 ext = p;
			 else
				 extPredicateDP.put(p, dp);
			 extPredicateMap.put(p, ext);
		}
		usedExts.add(p);
		return fac.getAtom(ext, t);
	}

	private  Atom getExtAtom(Predicate p, Term t1, Term t2, Set<Predicate> usedExts) throws URISyntaxException {
		if (p instanceof BooleanOperationPredicateImpl) {
			return fac.getAtom(p, t1, t2);			
		}
			
		Predicate ext = extPredicateMap.get(p);
		if (ext == null) {
			 List<CQIE> dp = new ArrayList<CQIE>(10);
			 ext = fac.getObjectPropertyPredicate(getEXTname(p.getName()));
			 Term x = fac.getVariable("x");
			 Term y = fac.getVariable("y");
			 Atom extAtom = fac.getAtom(ext, x, y);					 
			 for (Property sub : reasoner.getSubProperties(p))
				dp.add(fac.getCQIE(extAtom, (!sub.isInverse()) ? 
							fac.getAtom(sub.getPredicate(), x, y) : fac.getAtom(sub.getPredicate(), y, x))); 
			 log.debug("DP FOR " + p + " IS " + dp);
			 if (dp.size() > 1) {
				 dp = CQCUtilities.removeContainedQueries(dp, true, sigma, false);
				 log.debug("SIMPLIFIED DP FOR " + p + " IS " + dp);
			 }
			 if (dp.size() == 1) 
				 ext = p;
			 else
				 extPredicateDP.put(p, dp);
			 extPredicateMap.put(p, ext);
		}
		usedExts.add(p);
		return fac.getAtom(ext, t1, t2);
	}

	private static URI getEXTname(URI name) throws URISyntaxException {
		return new URI(name.getScheme(), name.getSchemeSpecificPart(), "EXT_" + name.getFragment());
	}

	
	/*
	 * Generating Concept atoms cache
	 */
			
	private List<Atom> getGenConAtoms(TreeWitnessGenerator some) throws URISyntaxException  {
		List<Atom> atoms = genconPredicateDP.get(some);
		if (atoms == null) {
			 List<CQIE> dp = new LinkedList<CQIE>();
			 
			 URI propURI = some.getProperty().getPredicate().getName();
			 URI genconURI = new URI(propURI.getScheme(), propURI.getSchemeSpecificPart(), "GEN"); 
			 			//+ propURI.getFragment()
						//+ (some.getProperty().isInverse() ? "_I_" : "_") 
						//+ ((some.getFiller() != null) ?  some.getFiller().getPredicate().getName().getFragment() : "T"));
					 
			 Term x = fac.getVariable("x");
			 Atom genAtom = fac.getAtom(fac.getClassPredicate(genconURI), x);
			 log.debug("RULES FOR GENERATING CONCEPT " + some);
			 for (BasicClassDescription subc : some.getConcepts()) {
				log.debug("  SUBCONCEPT: " + subc);
				if (subc instanceof OClass) {
					 dp.add(fac.getCQIE(genAtom, fac.getAtom(((OClass)subc).getPredicate(), x)));
				}
				else if (subc instanceof PropertySomeRestriction) {
					 PropertySomeRestriction twg = (PropertySomeRestriction)subc;
					 dp.add(fac.getCQIE(genAtom, (!twg.isInverse()) ? 
								fac.getAtom(twg.getPredicate(), x, fac.getNondistinguishedVariable()) : 
									fac.getAtom(twg.getPredicate(), fac.getNondistinguishedVariable(), x)));  						 
				}					
			 }
			 log.debug("DP FOR " + some + " IS " + dp);
			 if (dp.size() > 1) {
				 dp = CQCUtilities.removeContainedQueries(dp, true, sigma, false);
				 log.debug("SIMPLIFIED DP FOR " + some + " IS " + dp);
			 }
			 atoms = new ArrayList<Atom>(dp.size());
			 for (CQIE rule : dp) 
				atoms.add(rule.getBody().get(0));
			 genconPredicateDP.put(some, atoms);
		}
		return atoms;		
	}
	
	/*
	 * rewrites a given connected CQ with the rules put into output
	 */
	
	private void rewriteCQ(QueryConnectedComponent cc, Atom headAtom, DatalogProgram output) throws URISyntaxException {
		Set<Predicate> exts = new HashSet<Predicate>();
		
		Set<TreeWitness> tws = getReducedSetOfTreeWitnesses(cc);
		log.debug("TREE WITNESSES FOUND: " + tws.size());
		for (TreeWitness tw : tws) 
			log.debug(" " + tw);
				
		{
			// DETACHED TREE WITNESS
			if (cc.hasNoFreeTerms()) {
				Set<TreeWitnessGenerator> generators = new HashSet<TreeWitnessGenerator>();
				for (TreeWitness tw : tws) 
					if (tw.getDomain().containsAll(cc.getVariables())) {
						log.debug("TREE WITNESS " + tw + " COVERS THE QUERY");
						for (TreeWitnessGenerator some : reasoner.getGenerators())
							if (isGenerated(some, tw.getRootType())) 
								generators.add(some);
					}
				boolean saturated = true;
				do {
					Set<TreeWitnessGenerator> delta = new HashSet<TreeWitnessGenerator>();
					for (TreeWitnessGenerator gen : generators)
						for (BasicClassDescription g : gen.getConcepts()) {
							for (TreeWitnessGenerator some : reasoner.getGenerators()) {
								if (reasoner.getSubConcepts(g).contains(some.getFiller()) || 
										reasoner.getSubConcepts(g).contains(some.getRoleEndType())) {
									saturated = false;
									delta.add(some);
								}		 		
 							}
						}
					saturated = !generators.addAll(delta);
				} while (!saturated);						
				
				Term x = fac.getNondistinguishedVariable(); //fac.getVariable("x");
				for (TreeWitnessGenerator gen : generators) {
					log.debug("DETACHED GENERATED BY: " + gen);
					for (Atom a : getGenConAtoms(gen))
						output.appendRule(fac.getCQIE(headAtom, getExtAtom(a, x, exts))); 
				}				
			}
		}

		MinimalCQProducer mainbody = new MinimalCQProducer(reasoner, cc.getFreeVariables()); 
		int pairIndex = 0;
				
		for (QueryConnectedComponent.Edge edge : cc.getEdges()) {
			log.debug("EDGE " + edge);
			List<Atom> extAtoms = new ArrayList<Atom>(edge.getAtoms().size()); 
			for (Atom aa : edge.getAtoms()) 
				extAtoms.add(getExtAtom(aa, exts));
			
			Atom edgeAtom = null;	 // null means the edge has no tree witnesses associated to it			
			for (TreeWitness tw : tws)
				if (tw.getDomain().contains(edge.getTerm0()) && tw.getDomain().contains(edge.getTerm1())) {
					if (edgeAtom == null) {
						URI atomURI = edge.getAtoms().iterator().next().getPredicate().getName();
						URI edgeURI = new URI(atomURI.getScheme(), atomURI.getSchemeSpecificPart(), 
												"Q_" + (++pairIndex) + "_" + atomURI.getFragment());

						edgeAtom = fac.getAtom(fac.getPredicate(edgeURI, cc.getVariables().size()), cc.getVariables());
						mainbody.add(edgeAtom);				
						output.appendRule(fac.getCQIE(edgeAtom, extAtoms));						
					}
					// CREATE TREE WITNESS FORMULAS
					MinimalCQProducer twf = new MinimalCQProducer(reasoner, cc.getVariables()); 
					Iterator<Term> i = tw.getRoots().iterator();
					Term r0 = i.next();
					while (i.hasNext()) 
						twf.add(fac.getEQAtom(i.next(), r0));
					
					for (Atom c : tw.getRootType()) {
						if (c.getArity() == 1)
							twf.add(getExtAtom(c.getPredicate(), r0, exts));
						else //(c.getArity() == 2)
							twf.add(getExtAtom(c.getPredicate(), r0, r0, exts));
					}
					for (Atom a : getGenConAtoms(tw.getGenerator())) {
						List<Atom> twfa = new ArrayList<Atom>(twf.getBody().size() + 1); 
						twfa.add(getExtAtom(a, r0, exts));
						twfa.addAll(twf.getBody());
						output.appendRule(fac.getCQIE(edgeAtom, twfa));
					}
				}
			if (edgeAtom == null)	// no tree witnesses -- direct insertion into the main body
				mainbody.addAll(extAtoms);
		}
		// if no binary predicates 
		// TODO: change!!!
		// if (cc.getEdges().size() == 0)
		//	for (Atom a : cqie.getBody())
		//		mainbody.add(getExtAtom(a, exts));

		output.appendRule(fac.getCQIE(headAtom, mainbody.getBody())); 
				
		// EXTENSIONS		
		for (Predicate pred : exts) { 
			 List<CQIE> dp = extPredicateDP.get(pred);			 
			 if (dp != null) 
				 output.appendRule(dp); // NEED TO CLONE?				 
		 }
	}
	
	private double time = 0;
	
	@Override
	public OBDAQuery rewrite(OBDAQuery input) throws OBDAException {
		
		double startime = System.currentTimeMillis();
		
		DatalogProgram dp = (DatalogProgram) input;
		DatalogProgram output = fac.getDatalogProgram(); // (cqie.getHead().getPredicate());

		try {
			for (CQIE cqie : dp.getRules()) {
				List<QueryConnectedComponent> ccs = QueryConnectedComponent.getQueryConnectedComponents(cqie);	
				Atom cqieAtom = cqie.getHead();
			
				if (ccs.size() == 1) {
					QueryConnectedComponent cc = ccs.get(0);
					log.debug("CONNECTED COMPONENT (" + cc.getFreeVariables() + ")" + " EXISTS " + cc.getQuantifiedVariables() + " WITH EDGES " + cc.getEdges());
					rewriteCQ(cc, cqieAtom, output); 				
				}
				else {
					URI cqieURI = cqieAtom.getPredicate().getName();
					List<Atom> ccBody = new ArrayList<Atom>(ccs.size());
					int ccNumber = 0;
					for (QueryConnectedComponent cc : ccs) {
						log.debug("CONNECTED COMPONENT (" + cc.getFreeVariables() + ")" + " EXISTS " + cc.getQuantifiedVariables() + " WITH EDGES " + cc.getEdges());
				
						URI ccURI = new URI(cqieURI.getScheme(), cqieURI.getSchemeSpecificPart(),  
							 "CC_" + cqieURI.getFragment() + "_" + ccNumber++);					
						Atom ccAtom = fac.getAtom(fac.getPredicate(ccURI, cc.getFreeVariables().size()), cc.getFreeVariables());

						rewriteCQ(cc, ccAtom, output); 
						ccBody.add(ccAtom);
					}
					output.appendRule(fac.getCQIE(cqieAtom, ccBody));
				}
			}
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
		Predicate queryPredicate = dp.getRules().get(0).getHead().getPredicate();
		log.debug("REWRITTEN PROGRAM\n" + output);			
		if (output.getRules().size() > 1) {
			output = DatalogQueryServices.flatten(output, queryPredicate, "Q_");
			output = DatalogQueryServices.flatten(output, queryPredicate, "CC_");
			log.debug("Q-FLATTENED PROGRAM\n" + output);
			if (output.getRules().size() > 1) {
				output = CQCUtilities.removeContainedQueriesSorted(output, true, sigma);
				log.debug("PROGRAM AFTER CQC CONTAINMENT\n" + output);			
				if (output.getRules().size() != output.getRules(queryPredicate).size()) {
					output = DatalogQueryServices.flatten(output, queryPredicate, "EXT_");
					log.debug("EXT-FLATTENED PROGRAM\n" + output);
				}
			}
		}
		QueryUtils.copyQueryModifiers(input, output);

		double endtime = System.currentTimeMillis();
		double tm = (endtime - startime) / 1000;
		time += tm;
		log.debug(String.format("Rewriting time: %.3f s (total %.3f s)", tm, time));
		
		return output;
	}

	
	private Set<TreeWitness> getReducedSetOfTreeWitnesses(QueryConnectedComponent query) {
		Set<TreeWitness> treewitnesses = getTreeWitnesses(query);

		Set<TreeWitness> subtws = new HashSet<TreeWitness>(treewitnesses.size());
		for (TreeWitness tw : treewitnesses) {
			boolean subsumed = false;
			for (TreeWitness tw1 : treewitnesses)
				if (!tw.equals(tw1) && tw.getDomain().equals(tw1.getDomain()) && tw.getRoots().equals(tw1.getRoots())) {
					TreeWitnessGenerator twg = tw.getGenerator();
					TreeWitnessGenerator twg1 = tw1.getGenerator();
					if(reasoner.getSubConcepts(twg.getFiller()).contains(twg1.getFiller())) {
						if (reasoner.getSubProperties(twg.getProperty()).contains(twg1.getProperty())) {
							log.debug("SUBSUMED: " + tw + " BY " + tw1);
							subsumed = true;
							break;
						}
					}
				}
			if (!subsumed)
				subtws.add(tw);
		}
		return subtws;
	}

	private Set<TreeWitness> getTreeWitnesses(QueryConnectedComponent query) {
		log.debug("QUANTIFIED VARIABLES: " + query.getQuantifiedVariables());
		
		Set<TreeWitness> treewitnesses = new HashSet<TreeWitness>();
		
		for (Term v : query.getQuantifiedVariables()) {
			log.debug("VARIABLE " + v); 
			
			List<TreeWitness> tws = getTreeWitnessesForEdge(query, query.getIncidentEdges(v), Collections.singleton(v), 
					Collections.singleton(v), new HashSet<Atom>());
			if (tws != null)
				treewitnesses.addAll(tws);
		}
		
		if (treewitnesses.size() > 0) {
			Set<TreeWitness> delta = new HashSet<TreeWitness>(); 
			do 
				for (TreeWitness tw : treewitnesses) 
					if (tw.allRootsBound()) {
						Set<TreeWitness> twa = new HashSet<TreeWitness>(); 
						twa.add(tw);
						saturateTreeWitnesses(query, treewitnesses, delta, new HashSet<QueryConnectedComponent.Edge>(), twa); 
					}
					else
						log.debug("IGNORING " + tw + " DUE TO NON-BOUND ROOTS");
			while (treewitnesses.addAll(delta));
		}
		return treewitnesses;
	}

	private void saturateTreeWitnesses(QueryConnectedComponent query, Set<TreeWitness> completeTWs, Set<TreeWitness> delta, Set<QueryConnectedComponent.Edge> handle, Set<TreeWitness> merged) { 
		boolean saturated = true; 
		
		for (QueryConnectedComponent.Edge edge : query.getEdges()) { 
			if (handle.contains(edge)) {
				log.debug("HANDLE " + handle + " ALREADY CONTAINS EDGE " + edge);
				continue;
			}
			for (TreeWitness tw : merged) { 
				Term edgeRoot = null; 
				Term edgeNonRoot = null;
				if (tw.getRoots().contains(edge.getTerm0())) { 
					edgeRoot = edge.getTerm0(); 
					edgeNonRoot = edge.getTerm1(); 
				} 
				else if (tw.getRoots().contains(edge.getTerm1())) { 
					edgeRoot = edge.getTerm1(); 
					edgeNonRoot = edge.getTerm0(); 
				} 
				else 
					continue;
				
				boolean found = false;
				for (TreeWitness twa : merged)
					if (twa.getDomain().contains(edgeNonRoot)) {
						//log.debug("  HOWEVER IS CONTAINED IN " + twa);
						found = true;
					}
				if (found)
					continue;
				
				log.debug("EDGE " + edge + " IS ADJACENT TO THE TREE WITNESS " + tw); 

				
				saturated = false; 
				for (TreeWitness twa : completeTWs)  
					if (twa.allRootsBound() && 
							twa.getRoots().contains(edgeRoot) && 
							twa.getDomain().contains(edgeNonRoot)) {
						Set<TreeWitness> newMerged = new HashSet<TreeWitness>(merged); 
						newMerged.add(twa);
						log.debug("    ATTACHING A TREE WITNESS " + twa);
						saturateTreeWitnesses(query, completeTWs, delta, handle, newMerged); 
					} 
				
				Set<QueryConnectedComponent.Edge> newHandle = new HashSet<QueryConnectedComponent.Edge>(handle); 
				newHandle.add(edge);
				log.debug("    ATTACHING A HANDLE " + edge);
				saturateTreeWitnesses(query, completeTWs, delta, newHandle, merged);  
			} 
		}

		// the roots of tree witnesses in merged are bound variables
				
		if (saturated && (handle.size() != 0)) { 
			log.debug("CHECKING WHETHER THE HANDLE " + handle + " CAN BE ATTACHED TO THE FOLLOWING: "); 
			Set<Term> intdomain = new HashSet<Term>();
			Set<Term> introots = new HashSet<Term>();
			Set<Atom> endtype = new HashSet<Atom>(); 
			for (TreeWitness tw : merged) {
				log.debug("  " + tw);
				intdomain.addAll(tw.getDomain());
				introots.addAll(tw.getRoots());
				endtype.addAll(tw.getRootType());
			}
			List<TreeWitness> tws = getTreeWitnessesForEdge(query, handle, introots, intdomain, endtype);
			if (tws != null)
				delta.addAll(tws);
		}
	}
	 
	// returns a list of tree witnesses or null
	
	private List<TreeWitness> getTreeWitnessesForEdge(QueryConnectedComponent query, Set<QueryConnectedComponent.Edge> handle, Set<Term> introots, Set<Term> intdomain, Set<Atom> endtype) {		
		List<Property> props = new ArrayList<Property>(); 
		Set<Atom> roottype = new HashSet<Atom>(); 
		Set<Term> roots = new HashSet<Term>(); 

		for (QueryConnectedComponent.Edge edge : handle)
			for (Atom a : edge.getAtoms()) {
				if ((a.getArity() == 1) || ((a.getArity() == 2) && a.getTerm(0).equals(a.getTerm(1)))) {
						// unary predicate or loop
						if (introots.contains(a.getTerm(0))) {
							endtype.add(a);
							if (a.getArity() == 2) {
								log.debug("        NO LOOPS ALLOWED AT ENDPOINTS: " + a);
								return null; 
							}
						}
						else
							roottype.add(a);
				}						
				else {
					assert (a.getArity() == 2); 
					if (a.getPredicate() instanceof BooleanOperationPredicateImpl) {
						log.debug("        NO BOOLEAN OPERATION PREDICATES ALLOWED IN PROPERTIES: " + a);
						return null; 
					}
 						
					if (introots.contains(a.getTerm(1))) {
						props.add(ontFactory.createProperty(a.getPredicate(), false));
						roots.add(a.getTerm(0)); 
					} 
					else {
						assert (introots.contains(a.getTerm(0)));
						props.add(ontFactory.createProperty(a.getPredicate(), true)); 
						roots.add(a.getTerm(1)); 
					}
				}
			} 
		// TODO: edges with all terms among roots!

		if (handle.size() == 0)
			return null;			

		log.debug("  PROPERTIES " + props);
		log.debug("  ENDTYPE " + endtype);
		log.debug("  ROOTTYPE " + roottype);
		
		List<TreeWitness> treewitnesses = new LinkedList<TreeWitness>();
 
		for (TreeWitnessGenerator g : reasoner.getGenerators()) 
			if (isTreeWitness(g, props, endtype)) { 
				TreeWitness tw = new TreeWitness(g, roots, 
						query.getQuantifiedVariables().containsAll(roots), roottype, 
						intdomain); 
				log.debug("TREE WITNESS: " + tw);
				treewitnesses.add(tw); 
			} 
				
		return treewitnesses;
	}
	
	 private boolean isTreeWitness(TreeWitnessGenerator g, List<Property> properties, Set<Atom> endtype) { 
		log.debug("      CHECKING " + g);		

		// BIG TODO: CACHE PROPERTIES AND CONCEPTS FOR TREE WITNESSES
		
		for (Property p : properties) {
			if (!reasoner.getSubProperties(p).contains(g.getProperty())) {
				log.debug("        PROPERTY TOO SPECIFIC: " + p + " OF CLASS " + p.getPredicate().getClass() + " FOR " + g.getProperty());
				return false;
			}
			else
				log.debug("        PROPERTY IS FINE: " + p + " FOR " + g.getProperty());
		}
		if (!isGenerated(g, endtype))
			return false;
		
		log.debug("         ALL MATCHED"); 
		return true; 
	}

	private boolean isGenerated(TreeWitnessGenerator g, Set<Atom> endtype) {
		for (Atom a : endtype) {
			 assert (a.getArity() == 1);
			 BasicClassDescription con = ontFactory.createClass(a.getPredicate());
			 if (!reasoner.getSubConcepts(con).contains(g.getFiller()) && 
					 !reasoner.getSubConcepts(con).contains(g.getRoleEndType())) {
				 log.debug("        ENDTYPE TOO SPECIFIC: " + con + " FOR " + g.getFiller() + " AND " + g.getRoleEndType());
				 return false;
			 }
			 else
				 log.debug("        ENDTYPE IS FINE: " + con + " FOR " + g.getFiller());
		}
		return true;
	}
	 
}
