/*******************************************************************************
 * Copyright (c) 2004 - 2011 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/
package org.eclipse.data.sampledb;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plugin class for Sample DB This class initializes a private copy of the
 * Sample database by unzipping the DB files to a subdir in the TEMP directory.
 * A private copy is required because (1) Derby 10.1.2.1 has a bug which
 * disabled BIRT read-only access to a JAR'ed DB. (See
 * http://issues.apache.org/jira/browse/DERBY-854) (2) BIRT instances in
 * multiple JVMs may try to access the sample DB (such when preview mode). We
 * will corrupt the DB if a single copy of the DB is used
 */
public class SampledbActivator implements BundleActivator, SampleDBConstants {

	protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

	private static String dbDir;

	private static int startCount = 0;

	private BundleContext context;

	private static Driver derbyDriver;

	/**
	 * @see org.eclipse.core.runtime.Plugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		LOGGER.info("Sampledb plugin starts up. Current startCount="
				+ startCount);
		synchronized (SampledbActivator.class) {
			if (++startCount == 1) {
				// First time to start for this instance of JVM
				// initialze database directory now
				init();
			}
		}
		String dbUrl = getDBUrl();

		// Copy connection properties and replace user and password with fixed
		// value
		Properties props = new Properties();
		props.put(USER, SAMPLE_DB_SCHEMA);
		props.put(PASSWORD, EMPTY_VALUE);

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Getting Sample DB JDBC connection. DriverClass="
					+ DERBY_DRIVER_CLASS + ", Url=" + dbUrl);
		}

		// initClassLoaders();
		try {
			getDerbyDriver().connect(dbUrl, props);
		} catch (Exception e) {
			System.out.println();
		}
		DataSource ds = getDataSource();
		Dictionary<String, String> sprops = new Hashtable<String, String>();
		sprops.put(DATASOURCE_NAME, SAMPLE_DB_SCHEMA);
		sprops.put(OSGI_JNDI_SERVICE_NAME, DATASOURCE + FILE_DELIM
				+ SAMPLE_DB_SCHEMA);
		// Register a new TimeServiceImpl with the above props
		context.registerService(DataSource.class, ds, sprops);
		this.context = context;
	}

	private DataSource getDataSource() {
		BasicDataSource ds = new BasicDataSource();
		ds.setDriverClassName(DERBY_DRIVER_CLASS);
		ds.setUsername(SAMPLE_DB_SCHEMA);
		ds.setPassword(EMPTY_VALUE);
		ds.setUrl(getDBUrl());
		return ds;
	}

	/**
	 * Gets a new instance of Derby JDBC Driver
	 */
	private synchronized Driver getDerbyDriver() throws SQLException {

		try {
			derbyDriver = (Driver) Class.forName(DERBY_DRIVER_CLASS, true,
					this.getClass().getClassLoader()).newInstance();
		} catch (Exception e) {
			LOGGER.error("Failed to load Derby embedded driver: "
					+ DERBY_DRIVER_CLASS, e);
			throw new SQLException(e.getLocalizedMessage());

		}
		return derbyDriver;
	}

	/**
	 * @see org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		this.setContext(context);
		LOGGER.debug("Sampledb plugin stopping. Current startCount="
				+ startCount);
		synchronized (SampledbActivator.class) {
			if (startCount >= 1) {
				if (--startCount == 0) {
					// Last one to stop for this instance of JVM
					// Clean up Derby and temp files
					cleanUp();
				}
			}
		}
		ungetService(context);
	}

	@SuppressWarnings("rawtypes")
	private void ungetService(BundleContext context)
			throws InvalidSyntaxException {
		ServiceReference[] refs = context.getServiceReferences(
				DataSource.class.getCanonicalName(), "(" + DATASOURCE_NAME
						+ "=" + SAMPLE_DB_SCHEMA + ")");
		if (refs == null || refs.length == 0 || refs.length > 1) {

		} else
			context.ungetService(refs[0]);
	}

	private void cleanUp() throws Exception {
		// Stop Derby engine
		shutdownDerby();
		// Clean up database files
		removeDatabase();

		dbDir = null;
	}

	/**
	 * Initialization for first time startup in this instance of JVM
	 */
	private void init() throws IOException {
		assert dbDir == null;

		// Create and remember our private directory under system temp
		// Name it "BIRTSampleDB_$timestamp$_$classinstanceid$"
		String tempDir = System.getProperty("java.io.tmpdir");
		String timeStamp = String.valueOf(System.currentTimeMillis());
		String instanceId = Integer.toHexString(hashCode());
		dbDir = tempDir + "/BIRTSampleDB_" + timeStamp + "_" + instanceId;
		LOGGER.debug("Creating Sampledb database at location " + dbDir);
		(new File(dbDir)).mkdir();

		// Set up private copy of Sample DB in system temp directory

		// Get an input stream to read DB Jar file
		// handle getting db jar file on both OSGi and OSGi-less platforms
		String dbEntryName = SAMPLE_DB_HOME_DIR + FILE_DELIM
				+ SAMPLE_DB_JAR_FILE;

		URL fileURL = SampledbActivator.class.getResource(dbEntryName);

		if (fileURL == null) {
			fileURL = this.getClass().getClassLoader().getResource(dbEntryName);
			if (fileURL == null) {
				String errMsg = "INTERNAL ERROR: SampleDB DB file not found: "
						+ dbEntryName;
				LOGGER.error(errMsg);
				throw new RuntimeException(errMsg);
			}
		}

		// Copy entries in the DB jar file to corresponding location in db dir
		InputStream dbFileStream = new BufferedInputStream(fileURL.openStream());
		ZipInputStream zipStream = new ZipInputStream(dbFileStream);
		ZipEntry entry;
		while ((entry = zipStream.getNextEntry()) != null) {
			File entryFile = new File(dbDir, entry.getName());
			if (entry.isDirectory()) {
				entryFile.mkdir();
			} else {
				// Copy zip entry to local file
				OutputStream os = new FileOutputStream(entryFile);
				byte[] buf = new byte[4000];
				int len;
				while ((len = zipStream.read(buf)) > 0) {
					os.write(buf, 0, len);
				}
				os.close();
			}
		}

		zipStream.close();
		dbFileStream.close();
	}

	/**
	 * Gets Derby connection URL
	 */
	public static String getDBUrl() {
		if (dbDir == null) {
			return JDBC_DERBY_CLASSPATH_BIRT_SAMPLE;
		}
		return JDBC_DERBY + dbDir + FILE_DELIM + SAMPLE_DB_NAME;
	}

	/**
	 * Shuts down the Derby
	 */
	private void shutdownDerby() {
		try {
			getDerbyDriver().connect(JDBC_DERBY_SHUTDOWN_TRUE, null);

		} catch (SQLException e) {
			LOGGER.info(e.getMessage());
		}
	}

	/**
	 * Deletes all files created for the Derby database
	 */
	private void removeDatabase() {
		LOGGER.debug("Removing Sampledb DB directory at location " + dbDir);
		File dbDirFile = new File(dbDir);
		if (!removeDirectory(dbDirFile)) {
			assert dbDirFile != null;
			dbDirFile.deleteOnExit();
			LOGGER.debug("Fail to remove one or more file in temp db directory,but it will be removed when the VM exits: "
					+ dbDir);
		}
	}

	/**
	 * Do a best-effort removal of directory.
	 */
	static boolean removeDirectory(File dir) {
		assert dir != null && dir.isDirectory();
		boolean success = true;
		String[] children = dir.list();
		for (int i = 0; i < children.length; i++) {
			File child = new File(dir, children[i]);
			if (child.isDirectory()) {
				if (!removeDirectory(child)) {
					success = false;
				}
			} else {
				if (!child.delete()) {
					success = false;
				}
			}
		}
		if (!dir.delete()) {
			success = false;
		}
		return success;
	}

	public BundleContext getContext() {
		return context;
	}

	public void setContext(BundleContext context) {
		this.context = context;
	}
}
