package sesameWrapper;

import it.unibz.krdb.obda.model.OBDAException;
import it.unibz.krdb.obda.owlrefplatform.core.QuestConstants;
import it.unibz.krdb.obda.owlrefplatform.core.QuestDBConnection;
import it.unibz.krdb.obda.owlrefplatform.core.QuestPreferences;
import it.unibz.krdb.obda.owlrefplatform.questdb.QuestDBVirtualStore;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;

import org.openrdf.repository.RepositoryException;

public class SesameVirtualRepo extends SesameAbstractRepo {

	private QuestDBVirtualStore virtualStore;
	private QuestDBConnection questDBConn;

	public SesameVirtualRepo(String name, String tboxFile, String obdaFile, boolean existential, String rewriting)
			throws Exception {
		super();

		QuestPreferences pref = new QuestPreferences();
		pref.setCurrentValueOf(QuestPreferences.ABOX_MODE, QuestConstants.VIRTUAL);
		if (existential)
			pref.setCurrentValueOf(QuestPreferences.REWRITE, "true");
		else
			pref.setCurrentValueOf(QuestPreferences.REWRITE, "false");
		if (rewriting.equals("TreeWitness"))
			pref.setCurrentValueOf(QuestPreferences.REFORMULATION_TECHNIQUE, QuestConstants.TW);
		else if (rewriting.equals("Default"))
			pref.setCurrentValueOf(QuestPreferences.REFORMULATION_TECHNIQUE, QuestConstants.UCQBASED);
		
		URI obdaURI = (new File(obdaFile)).toURI(); 
		URI tboxURI = (new File(tboxFile)).toURI(); 
		this.virtualStore = new QuestDBVirtualStore(name, tboxURI, obdaURI, pref);
		questDBConn = virtualStore.getConnection();
	}

	public SesameVirtualRepo(String name, String tboxFile, String obdaFile, String configFileName) throws Exception {
		super();
		QuestPreferences pref = new QuestPreferences();
		if (!configFileName.isEmpty()) {
			File configFile = new File(URI.create(configFileName));
			pref.readDefaultPropertiesFile(new FileInputStream(configFile));
		} else {
			pref.readDefaultPropertiesFile();
		}
		URI obdaURI = URI.create(obdaFile);
		URI tboxURI = URI.create(tboxFile);
		this.virtualStore = new QuestDBVirtualStore(name, tboxURI, obdaURI, pref);
		questDBConn = virtualStore.getConnection();
	}
	
	@Override
	public QuestDBConnection getQuestConnection() throws OBDAException {
		return questDBConn;
	}

	@Override
	public boolean isWritable() throws RepositoryException {
		// Checks whether this repository is writable, i.e.
		// if the data contained in this repository can be changed.
		// The writability of the repository is determined by the writability
		// of the Sail that this repository operates on.
		return false;
	}
	
	@Override
	public void shutDown() throws RepositoryException {
		super.shutDown();
		try {
			questDBConn.close();
		} catch (OBDAException e) {
			e.printStackTrace();
		}
	}

	public String getType() {
		return QuestConstants.VIRTUAL;
	}

}