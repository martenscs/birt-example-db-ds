package org.eclipse.data.sampledb;

/**
 * Wrap the constants used in SampleDB.
 */
public interface SampleDBConstants {

	public static final String DERBY_DRIVER_CLASS = "org.apache.derby.jdbc.EmbeddedDriver";
	public static final String SAMPLE_DB_SCHEMA = "ClassicModels";
	public static final String SAMPLE_DB_NAME = "BirtSample";
	public static final String SAMPLE_DB_JAR_FILE = "BirtSample.jar";
	public static final String SAMPLE_DB_HOME_DIR = "db";
	public static final String EMPTY_VALUE = "";
	public static final String PASSWORD = "password";
	public static final String USER = "user";
	public static final String DATASOURCE = "datasource";
	public static final String OSGI_JNDI_SERVICE_NAME = "osgi.jndi.service.name";
	public static final String DATASOURCE_NAME = "datasource.name";
	public static final String FILE_DELIM = "/";
	public static final String JDBC_DERBY_CLASSPATH_BIRT_SAMPLE = "jdbc:derby:classpath:BirtSample";
	public static final String JDBC_DERBY = "jdbc:derby:";
	public static final String JDBC_DERBY_SHUTDOWN_TRUE = "jdbc:derby:;shutdown=true";
}
