package it.unibz.krdb.obda.reformulation.tests;

import it.unibz.krdb.obda.io.DataManager;
import it.unibz.krdb.obda.io.QueryStorageManager;
import it.unibz.krdb.obda.model.OBDADataFactory;
import it.unibz.krdb.obda.model.OBDADataSource;
import it.unibz.krdb.obda.model.OBDAModel;
import it.unibz.krdb.obda.model.OBDAResultSet;
import it.unibz.krdb.obda.model.OBDAStatement;
import it.unibz.krdb.obda.model.impl.OBDADataFactoryImpl;
import it.unibz.krdb.obda.model.impl.RDBMSourceParameterConstants;
import it.unibz.krdb.obda.owlapi.OBDAOWLReasonerFactory;
import it.unibz.krdb.obda.owlapi.ReformulationPlatformPreferences;
import it.unibz.krdb.obda.owlrefplatform.core.QuestConstants;
import it.unibz.krdb.obda.owlrefplatform.core.QuestOWL;
import it.unibz.krdb.obda.owlrefplatform.core.QuestOWLFactory;
import it.unibz.krdb.obda.querymanager.QueryController;
import it.unibz.krdb.obda.querymanager.QueryControllerGroup;
import it.unibz.krdb.obda.querymanager.QueryControllerQuery;
import it.unibz.krdb.sql.JDBCConnectionManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import org.semanticweb.owl.apibinding.OWLManager;
import org.semanticweb.owl.model.OWLOntology;
import org.semanticweb.owl.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * The following tests take the Stock exchange scenario and execute the queries
 * of the scenario to validate the results. The validation is simple, we only
 * count the number of distinct tuples returned by each query, which we know in
 * advance.
 * 
 * We execute the scenario in different modes, virtual, classic, with and
 * without optimizations.
 * 
 * The data is obtained from an inmemory database with the stock exchange
 * tuples. If the scenario is run in classic, this data gets imported
 * automatically by the reasoner.
 * 
 * 
 * @author mariano
 * 
 */
public class StockExchangeTest extends TestCase {

	// TODO We need to extend this test to import the contents of the mappings
	// into OWL and repeat everything taking form OWL

	private OBDADataFactory fac;
	private OBDADataSource stockDB;
	private Connection conn;

	Logger log = LoggerFactory.getLogger(this.getClass());
	private OBDAModel obdaModel;
	private OWLOntology ontology;

	List<TestQuery> testQueries = new LinkedList<TestQuery>();

	public class TestQuery {
		public String id = "";
		public String query = "";
		public int distinctTuples = -1;
	}

	public class Result {
		public String id = "";
		public String query = "";
		public int distinctTuples = -1;
		public long timeelapsed = -1;
	}

	@Override
	public void setUp() throws Exception {
		/*
		 * Initializing and H2 database with the stock exchange data
		 */

		String driver = "org.h2.Driver";
		String url = "jdbc:h2:mem:stockclient1";
		String username = "sa";
		String password = "";

		fac = OBDADataFactoryImpl.getInstance();
		stockDB = fac.getDataSource(URI.create("http://www.obda.org/ABOXDUMP" + System.currentTimeMillis()));
		stockDB.setParameter(RDBMSourceParameterConstants.DATABASE_DRIVER, driver);
		stockDB.setParameter(RDBMSourceParameterConstants.DATABASE_PASSWORD, password);
		stockDB.setParameter(RDBMSourceParameterConstants.DATABASE_URL, url);
		stockDB.setParameter(RDBMSourceParameterConstants.DATABASE_USERNAME, username);
		stockDB.setParameter(RDBMSourceParameterConstants.IS_IN_MEMORY, "true");
		stockDB.setParameter(RDBMSourceParameterConstants.USE_DATASOURCE_FOR_ABOXDUMP, "true");

		conn = JDBCConnectionManager.getJDBCConnectionManager().getConnection(stockDB);

		Statement st = conn.createStatement();

		FileReader reader = new FileReader("src/test/resources/test/stockexchange-postgres.sql");
		BufferedReader in = new BufferedReader(reader);
		StringBuilder bf = new StringBuilder();
		String line = in.readLine();
		while (line != null) {
			bf.append(line);
			line = in.readLine();
		}

		st.executeUpdate(bf.toString());
		conn.commit();

		/*
		 * Loading the ontology and obda model
		 */

		String owlfile = "src/test/resources/test/stockexchange-unittest.owl";
		String obdafile = "src/test/resources/test/stockexchange-unittest.obda";

		// Loading the OWL file
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		ontology = manager.loadOntologyFromPhysicalURI((new File(owlfile)).toURI());

		// Loading the OBDA data
		obdaModel = fac.getOBDAModel();
		DataManager ioManager = new DataManager(obdaModel);
		ioManager.loadOBDADataFromURI(new File(obdafile).toURI(), ontology.getURI(), obdaModel.getPrefixManager());

		/*
		 * Loading the queries (we have 11 queries)
		 */

		QueryController qcontroller = new QueryController();
		QueryStorageManager qman = new QueryStorageManager(qcontroller);

		qman.loadQueries(new File(obdafile).toURI());

		/* These are the distinct tuples that we know each query returns */
		int[] tuples = { 7, 1, 4, 1, 1, 2, 2, 1, 4, 3, 3 };
		int current = 0;
		for (QueryControllerGroup group : qcontroller.getGroups()) {
			for (QueryControllerQuery query : group.getQueries()) {
				TestQuery tq = new TestQuery();
				tq.id = query.getID();
				tq.query = query.getQuery();
				tq.distinctTuples = tuples[current];
				testQueries.add(tq);
				current += 1;
			}
		}

	}

	@Override
	public void tearDown() throws Exception {
		conn.close();

	}

	private void runTests(ReformulationPlatformPreferences p) throws Exception {

		// Creating a new instance of the reasoner
		OBDAOWLReasonerFactory factory = new QuestOWLFactory();

		factory.setPreferenceHolder(p);

		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		QuestOWL reasoner = (QuestOWL) factory.createReasoner(manager);
		reasoner.setPreferences(p);
		reasoner.loadOntologies(Collections.singleton(ontology));
		reasoner.loadOBDAModel(obdaModel);

		// One time classification call.
		reasoner.classify();

		// Now we are ready for querying
		OBDAStatement st = reasoner.getStatement();

		List<Result> summaries = new LinkedList<StockExchangeTest.Result>();

		int qc = 0;
		for (TestQuery tq : testQueries) {
			log.debug("Executing query: {}", qc);
			log.debug("Query: {}", tq.query);
			// if (qc == 7)
			// continue;
			qc += 1;

			long start = System.currentTimeMillis();
			OBDAResultSet rs = st.executeQuery(tq.query);
			long end = System.currentTimeMillis();

			int count = 0;
			while (rs.nextRow()) {
				count += 1;
			}

			Result summary = new Result();
			summary.id = tq.id;
			summary.query = tq.query;
			summary.timeelapsed = end - start;
			summary.distinctTuples = count;
			summaries.add(summary);
		}

		/* Closing resources */
		reasoner.disconnect();
		reasoner.dispose();

		boolean fail = false;
		/* Comparing and printing results */

		int totaltime = 0;
		for (int i = 0; i < testQueries.size(); i++) {
			TestQuery tq = testQueries.get(i);
			Result summary = summaries.get(i);
			totaltime += summary.timeelapsed;
			fail = fail | tq.distinctTuples != summary.distinctTuples;
			String out = "Query: %3d   Tup. Ex.: %6d Tup. ret.: %6d    Time elapsed: %6.3f s";
			log.debug(String.format(out, i, tq.distinctTuples, summary.distinctTuples, (double) summary.timeelapsed / (double) 1000));

		}
		log.debug("==========================");
		log.debug(String.format("Total time elapsed: %6.3f s", (double) totaltime / (double) 1000));
		assertFalse(fail);
	}

	public void testSiEqSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.CLASSIC);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_MAPPINGS, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_ONTOLOGY, "false");

		p.setCurrentValueOf(ReformulationPlatformPreferences.DBTYPE, QuestConstants.SEMANTIC);
		runTests(p);

	}

	public void testSiEqNoSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.CLASSIC);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_MAPPINGS, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_ONTOLOGY, "false");

		p.setCurrentValueOf(ReformulationPlatformPreferences.DBTYPE, QuestConstants.SEMANTIC);
		runTests(p);

	}

	public void testSiNoEqSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.CLASSIC);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_MAPPINGS, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_ONTOLOGY, "false");

		p.setCurrentValueOf(ReformulationPlatformPreferences.DBTYPE, QuestConstants.SEMANTIC);
		runTests(p);
	}

	public void testSiNoEqNoSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.CLASSIC);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_MAPPINGS, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_ONTOLOGY, "false");

		p.setCurrentValueOf(ReformulationPlatformPreferences.DBTYPE, QuestConstants.SEMANTIC);
		runTests(p);
	}

	/*
	 * Direct
	 */

	public void testDiEqSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.CLASSIC);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_MAPPINGS, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_ONTOLOGY, "false");

		p.setCurrentValueOf(ReformulationPlatformPreferences.DBTYPE, QuestConstants.DIRECT);
		runTests(p);

	}

	public void testDiEqNoSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.CLASSIC);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_MAPPINGS, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_ONTOLOGY, "false");

		p.setCurrentValueOf(ReformulationPlatformPreferences.DBTYPE, QuestConstants.DIRECT);
		runTests(p);

	}

	public void testDiNoEqSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.CLASSIC);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_MAPPINGS, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_ONTOLOGY, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.DBTYPE, QuestConstants.DIRECT);
		runTests(p);
	}

	public void testDiNoEqNoSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.CLASSIC);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_MAPPINGS, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OBTAIN_FROM_ONTOLOGY, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.DBTYPE, QuestConstants.DIRECT);
		runTests(p);
	}

	public void testViEqSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.VIRTUAL);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "true");

		runTests(p);

	}

	public void testViEqNoSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.VIRTUAL);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "true");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "false");

		runTests(p);
	}

	public void testViNoEqSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.VIRTUAL);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "true");

		runTests(p);

	}

	public void testViNoEqNoSig() throws Exception {

		ReformulationPlatformPreferences p = new ReformulationPlatformPreferences();
		p.setCurrentValueOf(ReformulationPlatformPreferences.ABOX_MODE, QuestConstants.VIRTUAL);
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_EQUIVALENCES, "false");
		p.setCurrentValueOf(ReformulationPlatformPreferences.OPTIMIZE_TBOX_SIGMA, "false");

		runTests(p);
	}
}
