package it.unibz.krdb.obda.sesame.r2rml;

/*
 * #%L
 * ontop-obdalib-sesame
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
/**
 * @author timea bagosi
 * Class responsible to write an r2rml turtle file given an obda model
 */
import it.unibz.krdb.obda.io.PrefixManager;
import it.unibz.krdb.obda.model.CQIE;
import it.unibz.krdb.obda.model.DataTypePredicate;
import it.unibz.krdb.obda.model.Function;
import it.unibz.krdb.obda.model.OBDAMappingAxiom;
import it.unibz.krdb.obda.model.OBDAModel;
import it.unibz.krdb.obda.model.OBDAQuery;
import it.unibz.krdb.obda.model.Predicate;
import it.unibz.krdb.obda.model.Term;
import it.unibz.krdb.obda.model.URIConstant;
import it.unibz.krdb.obda.model.URITemplatePredicate;
import it.unibz.krdb.obda.model.ValueConstant;
import it.unibz.krdb.obda.model.Variable;
import it.unibz.krdb.obda.model.impl.BNodePredicateImpl;
import it.unibz.krdb.obda.model.impl.FunctionalTermImpl;
import it.unibz.krdb.obda.model.impl.OBDAVocabulary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.callimachusproject.io.TurtleStreamWriter;
import org.openrdf.model.Graph;
import org.openrdf.model.Model;
import org.openrdf.model.Statement;
import org.openrdf.model.impl.GraphImpl;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.Rio;
import org.openrdf.rio.helpers.StatementCollector;
import org.openrdf.rio.turtle.TurtleWriter;

import eu.optique.api.mapping.R2RMLMappingManager;
import eu.optique.api.mapping.R2RMLMappingManagerFactory;
import eu.optique.api.mapping.TriplesMap;


public class R2RMLWriter {
	
	private BufferedWriter out;
	private List<OBDAMappingAxiom> mappings;
	private URI sourceUri;
	private PrefixManager prefixmng;
	
	public R2RMLWriter(File file, OBDAModel obdamodel, URI sourceURI)
	{
		try {
			this.out = new BufferedWriter(new FileWriter(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.sourceUri = sourceURI;
		this.mappings = obdamodel.getMappings(sourceUri);
		this.prefixmng = obdamodel.getPrefixManager(); 
	}
	
	public R2RMLWriter(OBDAModel obdamodel, URI sourceURI)
	{
		this.sourceUri = sourceURI;	
		this.mappings = obdamodel.getMappings(sourceUri);
		this.prefixmng = obdamodel.getPrefixManager(); 
	}

	/**
	 * call this method if you need the RDF Graph
	 * that represents the R2RML mappings
	 * @return an RDF Graph
	 */
	@Deprecated
	public Graph getGraph() {
		OBDAMappingTransformer transformer = new OBDAMappingTransformer();
		List<Statement> statements = new ArrayList<Statement>();
		
		for (OBDAMappingAxiom axiom: this.mappings) {
			List<Statement> statements2 = transformer.getStatements(axiom,prefixmng);
			statements.addAll(statements2);
		}
		@SuppressWarnings("deprecation")
		Graph g = new GraphImpl(); 
		g.addAll(statements);
		return g;
	}

	public Collection <TriplesMap> getTriplesMaps() {
		OBDAMappingTransformer transformer = new OBDAMappingTransformer();
		Collection<TriplesMap> coll = new LinkedList<TriplesMap>();
		for (OBDAMappingAxiom axiom: this.mappings) {
			TriplesMap tm = transformer.getTriplesMap(axiom, prefixmng);
			coll.add(tm);
		}
		return coll;
	}
	
	/**
	 * the method to write the R2RML mappings
	 * from an rdf Model to a file
	 * @param file the ttl file to write to
	 */
	public void write(File file)
	{
		try {
			R2RMLMappingManager mm = R2RMLMappingManagerFactory.getSesameMappingManager();
			Collection<TriplesMap> coll = getTriplesMaps();
			Model out = mm.exportMappings(coll, Model.class);			
			FileOutputStream fos = new FileOutputStream(file);
			Rio.write(out, fos, RDFFormat.TURTLE);
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * "Pretty" R2RML output
	 * @param file
	 */
	public void writePretty(File file) {
		try {
			R2RMLMappingManager mm = R2RMLMappingManagerFactory.getSesameMappingManager();
			Collection<TriplesMap> coll = getTriplesMaps();
			Model m = mm.exportMappings(coll, Model.class);			
			FileWriter fw = new FileWriter(file);
			TurtleWriter writer = new TurtleStreamWriter(fw, null);
			writer.startRDF();
			Map<String, String> map = prefixmng.getPrefixMap();
			for (String key : map.keySet()) {
				//System.out.println(key + "->" + map.get(key));
				writer.handleNamespace(key, map.get(key));
			}
			Iterator<Statement> it = m.iterator();
			while(it.hasNext()){
				writer.handleStatement(it.next());
			}
			writer.endRDF();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	public static void main(String args[])
	{
		String file = "/Users/mindaugas/r2rml/test2.ttl";
		R2RMLReader reader = new R2RMLReader(file);

		R2RMLWriter writer = new R2RMLWriter(reader.readModel(URI.create("test")),URI.create("test"));
		File out = new File("/Users/mindaugas/r2rml/out.ttl");
//		Graph g = writer.getGraph();
//		Iterator<Statement> st = g.iterator();
//		while (st.hasNext())
//			System.out.println(st.next());
		writer.writePretty(out);
		
	}
}
