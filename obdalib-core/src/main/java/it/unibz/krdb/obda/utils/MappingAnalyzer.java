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

import it.unibz.krdb.obda.model.CQIE;
import it.unibz.krdb.obda.model.Constant;
import it.unibz.krdb.obda.model.DatalogProgram;
import it.unibz.krdb.obda.model.Function;
import it.unibz.krdb.obda.model.OBDADataFactory;
import it.unibz.krdb.obda.model.OBDAMappingAxiom;
import it.unibz.krdb.obda.model.OBDASQLQuery;
import it.unibz.krdb.obda.model.Predicate;
import it.unibz.krdb.obda.model.Predicate.COL_TYPE;
import it.unibz.krdb.obda.model.Term;
import it.unibz.krdb.obda.model.Variable;
import it.unibz.krdb.obda.model.impl.OBDADataFactoryImpl;
import it.unibz.krdb.obda.model.impl.OBDAVocabulary;
import it.unibz.krdb.obda.parser.SQLQueryTranslator;
import it.unibz.krdb.sql.DBMetadata;
import it.unibz.krdb.sql.DataDefinition;
import it.unibz.krdb.sql.api.RelationJSQL;
import it.unibz.krdb.sql.api.SelectJSQL;
import it.unibz.krdb.sql.api.SelectionJSQL;
import it.unibz.krdb.sql.api.VisitedQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.create.table.ColDataType;

//import com.hp.hpl.jena.iri.IRI;

public class MappingAnalyzer {

	private List<OBDAMappingAxiom> mappingList;
	private DBMetadata dbMetaData;

	private SQLQueryTranslator translator;

	private static final OBDADataFactory dfac = OBDADataFactoryImpl
			.getInstance();

	/**
	 * Creates a mapping analyzer by taking into account the OBDA model.
	 */
	public MappingAnalyzer(List<OBDAMappingAxiom> mappingList,
			DBMetadata dbMetaData) {
		this.mappingList = mappingList;
		this.dbMetaData = dbMetaData;

		translator = new SQLQueryTranslator(dbMetaData);
	}

	public DatalogProgram constructDatalogProgram() {
		DatalogProgram datalog = dfac.getDatalogProgram();
		LinkedList<String> errorMessage = new LinkedList<String>();
		for (OBDAMappingAxiom axiom : mappingList) {
			try {
				// Obtain the target and source query from each mapping axiom in
				// the model.
				CQIE targetQuery = (CQIE) axiom.getTargetQuery();

				// Get the parsed sql, since it is already parsed by the mapping
				// parser
				// consider also MetaMappingExpander
				// VisitedQuery queryParsed = ...;

				OBDASQLQuery sourceQuery = (OBDASQLQuery) axiom
						.getSourceQuery();

				// Construct the SQL query tree from the source query
				VisitedQuery queryParsed = translator
						.constructParser(sourceQuery.toString());

				// Create a lookup table for variable swapping
				LookupTable lookupTable = createLookupTable(queryParsed);

				// We can get easily the table from the SQL
				ArrayList<RelationJSQL> tableList = queryParsed.getTableSet();

				// Construct the body from the source query
				ArrayList<Function> atoms = new ArrayList<Function>();
				for (RelationJSQL table : tableList) {
					// Construct the URI from the table name
					String tableName = table.getFullName();
//					String tableName = table.getTableName();
					//String predicateName = tableName;
//					String predicateName = table.getTableName();

					// Construct the predicate using the table name
					int arity = dbMetaData.getDefinition(tableName)
							.countAttribute();
					
					
					
					Predicate predicate = dfac.getPredicate(tableName,
							arity);

					// Swap the column name with a new variable from the lookup
					// table
					List<Term> terms = new ArrayList<Term>();
					for (int i = 1; i <= arity; i++) {
						String columnName = dbMetaData
								.getFullQualifiedAttributeName(tableName,
										table.getAlias(), i);
						String termName = lookupTable.lookup(columnName);
						if (termName == null) {
							throw new RuntimeException("Column '" + columnName
									+ "'was not found in the lookup table: ");
						}
						Term term = dfac.getVariable(termName);
						terms.add(term);
					}
					// Create an atom for a particular table
					Function atom = dfac.getFunction(predicate, terms);
					atoms.add(atom);
				}

				// For the join conditions WE STILL NEED TO CONSIDER NOT EQUI
				// JOIN
				ArrayList<Expression> joinConditions = queryParsed.getJoinCondition();
				for (Expression predicate : joinConditions) {

					Function atom = getFunction(predicate, lookupTable);
					atoms.add(atom);
				}

				// For the selection "where" clause conditions
				SelectionJSQL selection = queryParsed.getSelection();
				if (selection != null) {

					// Stack for filter function
					Stack<Function> filterFunctionStack = new Stack<Function>();

					Expression conditions = selection.getRawConditions();
					Function filterFunction = getFunction(conditions,
							lookupTable);
					filterFunctionStack.push(filterFunction);

					// The filter function stack must have 1 element left
					if (filterFunctionStack.size() == 1) {
						Function filterFunct = filterFunctionStack.pop();
						Function atom = dfac.getFunction(
								filterFunct.getFunctionSymbol(),
								filterFunct.getTerms());
						atoms.add(atom);
					} else {
						throwInvalidFilterExpressionException(filterFunctionStack);
					}

				}

				// Construct the head from the target query.
				List<Function> atomList = targetQuery.getBody();
				// for (Function atom : atomList) {
				Iterator<Function> atomListIter = atomList.iterator();

				while (atomListIter.hasNext()) {
					Function atom = atomListIter.next();
					List<Term> terms = atom.getTerms();
					List<Term> newterms = new LinkedList<Term>();
					for (Term term : terms) {
						newterms.add(updateTerm(term, lookupTable));
					}
					Function newhead = dfac.getFunction(atom.getPredicate(),
							newterms);
					CQIE rule = dfac.getCQIE(newhead, atoms);
					datalog.appendRule(rule);
				}

			} catch (Exception e) {
				errorMessage.add("Error in mapping with id: " + axiom.getId()
						+ " \n Description: " + e.getMessage()
						+ " \nMapping: [" + axiom.toString() + "]");

			}
		}

		if (errorMessage.size() > 0) {
			StringBuilder errors = new StringBuilder();
			for (String error : errorMessage) {
				errors.append(error + "\n");
			}
			final String msg = "There was an error analyzing the following mappings. Please correct the issue(s) to continue.\n"
					+ errors.toString();
			RuntimeException r = new RuntimeException(msg);
			throw r;
		}
		return datalog;
	}

	private void throwInvalidFilterExpressionException(
			Stack<Function> filterFunctionStack) {
		StringBuilder filterExpression = new StringBuilder();
		while (!filterFunctionStack.isEmpty()) {
			filterExpression.append(filterFunctionStack.pop());
		}
		throw new RuntimeException("Illegal filter expression: "
				+ filterExpression.toString());
	}

	/**
	 * Methods to create a {@link Function} starting from a
	 * {@link IsNullExpression}
	 * 
	 * @param pred
	 *            IsNullExpression
	 * @param lookupTable
	 * @return a function from the OBDADataFactory
	 */
	private Function getFunction(IsNullExpression pred, LookupTable lookupTable) {

		Expression column = pred.getLeftExpression();
		String columnName = column.toString();
		String variableName = lookupTable.lookup(columnName);
		if (variableName == null) {
			throw new RuntimeException(
					"Unable to find column name for variable: " + columnName);
		}
		Term var = dfac.getVariable(variableName);

		if (!pred.isNot()) {
			return dfac.getFunctionIsNull(var);
		} else {
			return dfac.getFunctionIsNotNull(var);
		}
	}
	
	/**
	 * Methods to create a {@link Function} starting from a
	 * {@link IsNullExpression}
	 * 
	 * @param pred
	 *            IsNullExpression
	 * @param lookupTable
	 * @return a function from the OBDADataFactory
	 */
	private Function getFunction(CastExpression pred, LookupTable lookupTable) {

		Expression column = pred.getLeftExpression();
		String columnName = column.toString();
		String variableName = lookupTable.lookup(columnName);
		if (variableName == null) {
			throw new RuntimeException(
					"Unable to find column name for variable: " + columnName);
		}
		Term var = dfac.getVariable(variableName);

		ColDataType datatype= pred.getType();
		

		
		Term var2 = null;
		
		//first value is a column, second value is a datatype. It can  also have the size
		
		return dfac.getFunctionCast(var, var2);
		
		
	}

	/**
	 * Recursive methods to create a {@link Function} starting from a
	 * {@link BinaryExpression} We consider all possible values of the left and
	 * right expressions
	 * 
	 * @param pred
	 * @param lookupTable
	 * @return
	 */
	private Function getFunction(Expression pred, LookupTable lookupTable) {
		if (pred instanceof BinaryExpression) {
			return getFunction((BinaryExpression) pred, lookupTable);
		} else if (pred instanceof IsNullExpression) {
			return getFunction((IsNullExpression) pred, lookupTable);
		} else if (pred instanceof Parenthesis) {
			Expression inside = ((Parenthesis) pred).getExpression();
			return getFunction(inside, lookupTable);
		} else if (pred instanceof Between) {
			Between between = (Between) pred;
			Expression left = between.getLeftExpression();
			Expression e1 = between.getBetweenExpressionStart();
			Expression e2 = between.getBetweenExpressionEnd();

			GreaterThanEquals gte = new GreaterThanEquals();
			gte.setLeftExpression(left);
			gte.setRightExpression(e1);

			MinorThanEquals mte = new MinorThanEquals();
			mte.setLeftExpression(left);
			mte.setRightExpression(e2);

			AndExpression ande = new AndExpression(gte, mte);
			return getFunction(ande, lookupTable);
		} else if (pred instanceof InExpression) {
			InExpression inExpr = (InExpression)pred;
			Expression left = inExpr.getLeftExpression();
			ExpressionList ilist = (ExpressionList)inExpr.getRightItemsList();
			
			List<EqualsTo> eqList = new ArrayList<EqualsTo>();
			for (Expression item : ilist.getExpressions()) {
				EqualsTo eq = new EqualsTo();
				eq.setLeftExpression(left);
				eq.setRightExpression(item);
				eqList.add(eq);
			}
			int size = eqList.size();
			if (size > 1) {
				OrExpression or = new OrExpression(eqList.get(size - 1),
												   eqList.get(size - 2));
				for (int i = size - 3; i >= 0; i--) {
					OrExpression orexpr = new OrExpression(eqList.get(i), or);
					or = orexpr;
				}
				return getFunction(or, lookupTable);
			} else {
				return getFunction(eqList.get(0), lookupTable);
			}
		} else
			return null;
	}

	/**
	 * Recursive methods to create a {@link Function} starting from a
	 * {@link BinaryExpression} We consider all possible values of the left and
	 * right expressions
	 * 
	 * @param pred
	 * @param lookupTable
	 * @return
	 */

	private Function getFunction(BinaryExpression pred, LookupTable lookupTable) {
		Expression left = pred.getLeftExpression();
		Expression right = pred.getRightExpression();

		//left term can be function or column variable
		Term t1 = null;
		t1 = getFunction(left, lookupTable);
		if (t1 == null) {
			t1 = getVariable(left, lookupTable);
		}
		if(t1 == null)
			throw new RuntimeException("Unable to find column name for variable: " +left);

		//right term can be function, column variable or data value
		Term t2 = null;
		t2 = getFunction(right, lookupTable);
		if (t2 == null) {
			t2 = getVariable(right, lookupTable);
		}
		if (t2 == null) {
			t2 = getValueConstant(right, lookupTable);
		}
		
		//get boolean operation
		String op = pred.getStringExpression();
		Function funct = null;
		if (op.equals("="))
			funct = dfac.getFunctionEQ(t1, t2);
		else if (op.equals(">"))
			funct = dfac.getFunctionGT(t1, t2);
		else if (op.equals("<"))
			funct = dfac.getFunctionLT(t1, t2);
		else if (op.equals(">="))
			funct = dfac.getFunctionGTE(t1, t2);
		else if (op.equals("<="))
			funct = dfac.getFunctionLTE(t1, t2);
		else if (op.equals("<>") || op.equals("!="))
			funct = dfac.getFunctionNEQ(t1, t2);
		else if (op.equals("AND"))
			funct = dfac.getFunctionAND(t1, t2);
		else if (op.equals("OR"))
			funct = dfac.getFunctionOR(t1, t2);
		else if (op.equals("+"))
			funct = dfac.getFunctionAdd(t1, t2);
		else if (op.equals("-"))
			funct = dfac.getFunctionSubstract(t1, t2);
		else if (op.equals("*"))
			funct = dfac.getFunctionMultiply(t1, t2);
		else if (op.equals("LIKE"))
			funct = dfac.getFunctionLike(t1, t2);
		else
			throw new RuntimeException("Unknown opertor: " + op);

		return funct;

	}

	private Term getVariable(Expression pred, LookupTable lookupTable) {
		String termName = "";
		if (pred instanceof Column) {
			termName = lookupTable.lookup(pred.toString());
			if (termName == null) {
				return null;
			}
			return dfac.getVariable(termName);
		}
		return null;
	}
	/**
	 * Return a valueConstant or Variable constructed from the given expression
	 * 
	 * @param pred
	 *            the expression to process
	 * @param lookupTable
	 *            in case of variable
	 * @return constructed valueconstant or variable
	 */
	private Term getValueConstant(Expression pred, LookupTable lookupTable) {
		String termRightName = "";
		if (pred instanceof Column) {
			// if the columns contains a boolean value
			String columnName = ((Column) pred).getColumnName();
			if (columnName.toLowerCase().equals("true")
					|| columnName.toLowerCase().equals("false")) {
				return dfac
						.getConstantLiteral(columnName, COL_TYPE.BOOLEAN);
			}
			else 
				throw new RuntimeException(
						"Unable to find column name for variable: "
								+ columnName);
			
		}
		else if (pred instanceof StringValue) {
			termRightName = ((StringValue) pred).getValue();
			return dfac.getConstantLiteral(termRightName, COL_TYPE.STRING);

		} else if (pred instanceof DateValue) {
			termRightName = ((DateValue) pred).getValue().toString();
			return dfac.getConstantLiteral(termRightName, COL_TYPE.DATETIME);

		} else if (pred instanceof TimeValue) {
			termRightName = ((TimeValue) pred).getValue().toString();
			return dfac.getConstantLiteral(termRightName, COL_TYPE.DATETIME);

		} else if (pred instanceof TimestampValue) {
			termRightName = ((TimestampValue) pred).getValue().toString();
			return dfac.getConstantLiteral(termRightName, COL_TYPE.DATETIME);

		} else if (pred instanceof LongValue) {
			termRightName = ((LongValue) pred).getStringValue();
			return dfac.getConstantLiteral(termRightName, COL_TYPE.INTEGER);

		} else if (pred instanceof DoubleValue) {
			termRightName = ((DoubleValue) pred).toString();
			return dfac.getConstantLiteral(termRightName, COL_TYPE.DOUBLE);

		} else {
			termRightName = pred.toString();
			return dfac.getConstantLiteral(termRightName, COL_TYPE.LITERAL);

		}
	}

	/**
	 * Returns a new term with the updated references.
	 */
	private Term updateTerm(Term term, LookupTable lookupTable) {
		Term result = null;

		if (term instanceof Variable) {
			Variable var = (Variable) term;
			String varName = var.getName();
			String termName = lookupTable.lookup(varName);
			if (termName == null) {
				final String msg = String
						.format("Error in identifying column name \"%s\", please check the query source in the mappings.\nPossible reasons:\n1. The name is ambiguous, or\n2. The name is not defined in the database schema.",
								var);
				throw new RuntimeException(msg);
			}
			result = dfac.getVariable(termName);

		} else if (term instanceof Function) {
			Function func = (Function) term;
			List<Term> terms = func.getTerms();
			List<Term> newterms = new LinkedList<Term>();
			for (Term innerTerm : terms) {
				newterms.add(updateTerm(innerTerm, lookupTable));
			}
			result = dfac.getFunction(func.getFunctionSymbol(), newterms);
		} else if (term instanceof Constant) {
			result = term.clone();
		}
		return result;
	}

	private LookupTable createLookupTable(VisitedQuery queryParsed) throws JSQLParserException {
		LookupTable lookupTable = new LookupTable();

		// Collect all the possible column names from tables.
		ArrayList<RelationJSQL> tableList = queryParsed.getTableSet();

		// Collect all known column aliases
		HashMap<String, String> aliasMap = queryParsed.getAliasMap();
		
		int offset = 0; // the index offset

		for (RelationJSQL table : tableList) {
			
			String tableName = table.getTableName();
			String fullName = table.getFullName();
			String tableGivenName = table.getGivenName();
			DataDefinition def = dbMetaData.getDefinition(fullName);
			
			
				 if (def == null) {
					 throw new RuntimeException("Definition not found for table '" + tableGivenName + "'.");
				 }
			
			
			int size = def.countAttribute();

			for (int i = 1; i <= size; i++) {
				// assigned index number
				int index = i + offset;
				
				// simple attribute name
//				String columnName = dbMetaData.getAttributeName(tableName, i);
				String columnName = dbMetaData.getAttributeName(fullName, i);
				
				lookupTable.add(columnName, index);
				
				String lowercaseColumn= columnName.toLowerCase();
				
				
				
				if (aliasMap.containsKey(lowercaseColumn)) { // register the alias name, if any
						lookupTable.add(aliasMap.get(lowercaseColumn), columnName);
					}
				
				
				
				// attribute name with table name prefix
				String tableColumnName = tableName + "." + columnName;
				lookupTable.add(tableColumnName, index);
				
								
				// attribute name with table name prefix
				String tablecolumnname = tableColumnName.toLowerCase();
				if (aliasMap.containsKey(tablecolumnname))
				{ // register the alias name, if any
					lookupTable.add(aliasMap.get(tablecolumnname), tableColumnName);
				}	
				
				
				// attribute name with table given name prefix
				String givenTableColumnName = tableGivenName + "." + columnName;
				lookupTable.add(givenTableColumnName, tableColumnName);
				
				String giventablecolumnname= givenTableColumnName.toLowerCase();
				if (aliasMap.containsKey(giventablecolumnname)) { // register the alias name, if any
					lookupTable.add(aliasMap.get(giventablecolumnname), tableColumnName);
				}
				
				
				// full qualified attribute name
				String qualifiedColumnName = dbMetaData.getFullQualifiedAttributeName(fullName, i);
//				String qualifiedColumnName = dbMetaData.getFullQualifiedAttributeName(tableName, i);
				lookupTable.add(qualifiedColumnName, tableColumnName);
				String qualifiedcolumnname = qualifiedColumnName.toLowerCase();
				if (aliasMap.containsKey(qualifiedcolumnname)) { // register the alias name, if any
					lookupTable.add(aliasMap.get(qualifiedcolumnname), tableColumnName);
				}
				
				// full qualified attribute name using table alias
				String tableAlias = table.getAlias();
				if (tableAlias!=null) {
					String qualifiedColumnAlias = dbMetaData.getFullQualifiedAttributeName(fullName, tableAlias, i);
//					String qualifiedColumnAlias = dbMetaData.getFullQualifiedAttributeName(tableName, tableAlias, i);
					lookupTable.add(qualifiedColumnAlias, index);		
						String aliasColumnName = tableAlias.toLowerCase() + "." + lowercaseColumn;
						if (aliasMap.containsKey(aliasColumnName)) { // register the alias name, if any
							lookupTable.add(aliasMap.get(aliasColumnName), qualifiedColumnAlias);
						}
					
				}
				
				//check if we do not have subselect with alias name assigned
				for(SelectJSQL subSelect: queryParsed.getSubSelectSet()){
					String subSelectAlias = subSelect.getAlias();
					if (subSelectAlias!=null) {
						String aliasColumnName = subSelectAlias.toLowerCase() + "." + lowercaseColumn;
						lookupTable.add(aliasColumnName, index);
						if (aliasMap.containsKey(aliasColumnName)) { // register the alias name, if any
							lookupTable.add(aliasMap.get(aliasColumnName), aliasColumnName);
						}
					}
				}
			}
			offset += size;
		}
		return lookupTable;
	}
}
