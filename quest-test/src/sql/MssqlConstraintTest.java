package it.unibz.krdb.sql;

public class MssqlConstraintTest extends AbstractConstraintTest {

	public MssqlConstraintTest(String method) {
		super(method);
	}

	@Override
	protected String getConnectionPassword() {
		return "obdaps83";
	}

	@Override
	protected String getConnectionString() {
		return "jdbc:sqlserver://10.7.20.91;databaseName=dbconstraints";
	}

	@Override
	protected String getConnectionUsername() {
		return "mssql";
	}

	@Override
	protected String getDriverName() {
		return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
	}
}
