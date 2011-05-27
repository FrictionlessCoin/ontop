package inf.unibz.it.obda.model;

import java.net.URI;
import java.util.Set;

public interface DataSource {

	public abstract void setParameter(String parameter_uri, String value);

	public abstract URI getSourceID();

	public abstract void setNewID(URI newid);

	public abstract String getParameter(String parameter_uri);

	public abstract Set<Object> getParameters();

	public abstract void setEnabled(boolean enabled);

	public abstract boolean isEnabled();

	public abstract void setRegistred(boolean registred);

	public abstract boolean isRegistred();

}