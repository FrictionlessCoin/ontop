package it.unibz.krdb.obda.utils;

/*
 * #%L
 * ontop-obdalib-core
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

import it.unibz.krdb.obda.model.OBDAMappingAxiom;
import it.unibz.krdb.obda.parser.SQLQueryTranslator;
import it.unibz.krdb.sql.DBMetadata;
import it.unibz.krdb.sql.ViewDefinition;
import it.unibz.krdb.sql.api.RelationJSQL;
import it.unibz.krdb.sql.api.VisitedQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;

import net.sf.jsqlparser.JSQLParserException;



/**
 * This class is in charge of parsing the mappings and obtaining the list of schema names 
 * and tables.Afterwards from this list we obtain the metadata (not in this class).
 * @author Dag
 *
 */
public class MappingParser {
	
	private ArrayList<OBDAMappingAxiom> mappingList;
	private SQLQueryTranslator translator;
	private ArrayList<ParsedMapping> parsedMappings;
	private ArrayList<RelationJSQL> realTables; // Tables that are not view definitions
	
	public MappingParser(Connection conn, ArrayList<OBDAMappingAxiom> mappingList) throws SQLException{
		this.mappingList = mappingList;
		this.translator = new SQLQueryTranslator(conn);
		this.parsedMappings = this.parseMappings();
	}
	

	/**
	 * Called by Quest when fetching non-full metadata
	 * Only metadata for the tables in this list is extracted by Quest
	 * 
	 * @return The tables (same as getTables) but without those that are created by the sqltranslator as view definitions
	 * @throws JSQLParserException 
	 */
	public ArrayList<RelationJSQL> getRealTables() throws JSQLParserException{
		if(this.realTables == null){
			ArrayList<RelationJSQL> _realTables = this.getTables();
			ArrayList<RelationJSQL> removeThese = new ArrayList<RelationJSQL>();
			for(ViewDefinition vd : translator.getViewDefinitions()){
				for(RelationJSQL rel : _realTables){
					if(rel.getFullName().equals(vd.getName()))
						removeThese.add(rel);
				}
			}
			for(RelationJSQL remRel : removeThese){
				_realTables.remove(remRel);
			}
			this.realTables = _realTables;
		}
		return this.realTables;
	}
	
	/**
	 * Returns the list of parsed mapping objects.
	 * "Parsed" only means that sql part is parsed
	 * Called by Quest.setuprepository
	 * 
	 * @return
	 */
	public ArrayList<ParsedMapping> getParsedMappings(){
		return parsedMappings;
	}
	
	public ArrayList<RelationJSQL> getTables() throws JSQLParserException{
		ArrayList<RelationJSQL> tables = new ArrayList<RelationJSQL>();
		for(ParsedMapping pm : parsedMappings){
			VisitedQuery query = pm.getSourceQueryParsed();
			ArrayList<RelationJSQL> queryTables = query.getTableSet();
			for(RelationJSQL table : queryTables){
				if (!(tables.contains(table))){
					tables.add(table);
				}
			}
		}
		return tables;
	}
	
	
	
	
	/**
	 * Adds the view definitions created by the SQLQueryTranslator during parsing to the metadata
	 * 
	 * This must be separated out, since the parsing must be done before metadata extraction
	 */
	public void addViewDefs(DBMetadata metadata){
		for (ViewDefinition vd : translator.getViewDefinitions()){
			metadata.add(vd);
		}
	}
	
	/**
	 * 	Parses the mappingList (Actually, only the source sql is parsed.)
	 * This is necessary to separate the parsing, such that this can be done before the
	 * table schema extraction
	 * 
	 * @return List of parsed mappings
	 */
	private ArrayList<ParsedMapping> parseMappings() {
		LinkedList<String> errorMessage = new LinkedList<String>();
		ArrayList<ParsedMapping> parsedMappings = new ArrayList<ParsedMapping>();
		for (OBDAMappingAxiom axiom : this.mappingList) {
			try {
				ParsedMapping parsed = new ParsedMapping(axiom, translator);
				parsedMappings.add(parsed);
			} catch (Exception e) {
				errorMessage.add("Error in mapping with id: " + axiom.getId() + " \n Description: "
						+ e.getMessage() + " \nMapping: [" + axiom.toString() + "]");
				
			}
		}
		if (errorMessage.size() > 0) {
			StringBuilder errors = new StringBuilder();
			for (String error: errorMessage) {
				errors.append(error + "\n");
			}
			final String msg = "There was an error parsing the following mappings. Please correct the issue(s) to continue.\n" + errors.toString();
			RuntimeException r = new RuntimeException(msg);
			throw r;
		}
		return parsedMappings;
				
	}
	

}
