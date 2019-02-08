/**
 *
 */
package reader;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.nuxeo.ecm.automation.client.jaxrs.util.IOUtils;

import common.OracleConnector;

/**
 * @author loopingz
 *
 */
public class OracleReader extends AbstractReader {
    protected Statement stmt = null;
    protected ResultSet rset = null;
    protected Connection connection = null;
    protected String url = null;
    protected String username = null;
    protected String password = null;
    protected String database = null;
    private ResultSetMetaData metadata;
    protected int numberOfColumns;

    public OracleReader(CommandLine cmd) {
    	username = cmd.getOptionValue("oracle-user");
    	password = cmd.getOptionValue("oracle-password");
    	url = cmd.getOptionValue("oracle-url"); // Default: "192.168.2.30:1521" ?\
    	database = cmd.getOptionValue("oracle-db", "FIRSTVOX");
    }
    public static boolean enabledOptions(CommandLine cmd) {
		return cmd.hasOption("oracle-url");
	}

    public static void addOptions(Options options) {
    	options.addOption(Option.builder().longOpt("oracle-url").hasArg().desc("Oracle URL to connect to").build());
    	options.addOption(Option.builder().longOpt("oracle-user").hasArg().desc("Oracle User to connect with").build());
    	options.addOption(Option.builder().longOpt("oracle-password").hasArg().desc("Oracle Password to connect with").build());
    	options.addOption(Option.builder().longOpt("oracle-db").hasArg().desc("Oracle database to connect to (default: FIRSTVOX)").build());
    }
	/* (non-Javadoc)
	 * @see reader.IReader#open(java.lang.String[])
	 */
	@Override
	public void open() {
		try {
			openDatabase();
		} catch (SQLException | ClassNotFoundException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected void openDatabase() throws ClassNotFoundException, SQLException, IOException {
        OracleConnector orc = new OracleConnector(url, username, password, database);
        connection = orc.establishConnection();


        String query = IOUtils.read(this.getClass().getResourceAsStream("OracleQuery.sql"));

        System.out.println(query);
        stmt = connection.createStatement ();
        rset = stmt.executeQuery (query);

        metadata = rset.getMetaData();
        numberOfColumns = metadata.getColumnCount();
	}

	@Override
	public void close() {
		try {
			if (rset != null) {
				rset.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		try {
			if (stmt != null) {
				stmt.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		try {
			if (connection != null) {
				connection.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getString(String id) {
		try {
			return cleanString(rset.getString(id));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Integer getInt(String id) {
		try {
			return rset.getInt(id);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/* (non-Javadoc)
	 * @see reader.IReader#next()
	 */
	@Override
	public boolean next() {
		// TODO Auto-generated method stub
		try {
			return rset.next();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public String[] getRow() {
		String[] result = new String[numberOfColumns];
		int i = 0;
        for(i=0; i < numberOfColumns; i++) {
        	try {
				result[i] = rset.getString(i);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				result[i] = "";
			}
        }
        return result;
	}

    @Override
    public String printRow() {

        ArrayList<String> row = new ArrayList<String>();

        try {
            int i = 1;
            while(i <= numberOfColumns) {

                row.add(metadata.getColumnName(i) + "=" + rset.getString(i++));
            }
            return row.toString();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

	@Override
	public String getString(Integer col) {
		try {
			return cleanString(rset.getString(col));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
}
