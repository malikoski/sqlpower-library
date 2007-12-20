package ca.sqlpower.sql.jdbcwrapper;

import java.sql.ResultSet;
import java.sql.Statement;

public class GenericStatementDecorator extends StatementDecorator {

	protected GenericStatementDecorator(ConnectionDecorator connection,
			Statement statement) {
		super(connection, statement);
	}

	@Override
	protected ResultSet makeResultSetDecorator(ResultSet rs) {
		return new GenericResultSetDecorator(this, rs);
	}

}
