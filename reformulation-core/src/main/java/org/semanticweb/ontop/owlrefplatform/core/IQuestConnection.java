package org.semanticweb.ontop.owlrefplatform.core;

import org.semanticweb.ontop.model.OBDAConnection;
import org.semanticweb.ontop.model.OBDAException;
import org.semanticweb.ontop.owlrefplatform.core.execution.SIQuestStatement;

import java.sql.Connection;

/**
 * Creates IQuestStatement (mandatory) and SIQuestStatement (optional).
 *
 * TODO: rename it (in the future) QuestConnection.
 */
public interface IQuestConnection extends OBDAConnection {

	/**
	 * For both modes.
	 */
	@Override
	IQuestStatement createStatement() throws OBDAException;

	/**
	 * For the classic mode.
	 * MAY NOT BE SUPPORTED by certain implementations.
	 */
	SIQuestStatement createSIStatement() throws OBDAException;
}