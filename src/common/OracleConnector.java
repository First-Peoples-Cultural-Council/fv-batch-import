package common;

import java.sql.Connection;
import java.sql.SQLException;
import oracle.jdbc.pool.OracleDataSource;

public class OracleConnector {
    protected OracleDataSource ods = null;
    protected Connection connection = null;

    protected String url = null;
    protected String user = null;
    protected String pass = null;
    protected String schema = null;

    public OracleConnector(String url, String user, String pass, String schema) {
        super();

        this.url = url;
        this.user = user;
        this.pass = pass;
        this.schema = schema;
    }

    public Connection establishConnection() throws ClassNotFoundException, SQLException{
        Class.forName("oracle.jdbc.driver.OracleDriver");

        ods = new OracleDataSource();

        ods.setURL("jdbc:oracle:thin:@//" + url + "/" + schema);
        ods.setUser(user);
        ods.setPassword(pass);
        connection = ods.getConnection();

        connection.createStatement().execute("alter session set current_schema=" + schema);

        return connection;
    }

    public OracleDataSource getOds() {
        return ods;
    }

    public void setOds(OracleDataSource ods) {
        this.ods = ods;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

}
