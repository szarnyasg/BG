/**                                                                                                                                                                                
 * Copyright (c) 2012 USC Database Laboratory All rights reserved. 
 *
 * Authors:  Sumita Barahmand and Shahram Ghandeharizadeh                                                                                                                            
 *                                                                                                                                                                                 
 * Licensed under the Apache License, Version 2.0 (the "License"); you                                                                                                             
 * may not use this file except in compliance with the License. You                                                                                                                
 * may obtain a copy of the License at                                                                                                                                             
 *                                                                                                                                                                                 
 * http://www.apache.org/licenses/LICENSE-2.0                                                                                                                                      
 *                                                                                                                                                                                 
 * Unless required by applicable law or agreed to in writing, software                                                                                                             
 * distributed under the License is distributed on an "AS IS" BASIS,                                                                                                               
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or                                                                                                                 
 * implied. See the License for the specific language governing                                                                                                                    
 * permissions and limitations under the License. See accompanying                                                                                                                 
 * LICENSE file.                                                                                                                                                                   
 */

package memcached;


import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.PropertyConfigurator;

import common.CacheUtilities;

import com.meetup.memcached.MemcachedClient;
import com.meetup.memcached.SockIOPool;

import edu.usc.bg.base.ByteArrayByteIterator;
import edu.usc.bg.base.ByteIterator;
import edu.usc.bg.base.Client;
import edu.usc.bg.base.DB;
import edu.usc.bg.base.DBException;
import edu.usc.bg.base.ObjectByteIterator;
class ImgHandler{
	BufferedInputStream _bis;
	int _length;
	public BufferedInputStream get_bis() {
		return _bis;
	}
	public int get_length() {
		return _length;
	}
	ImgHandler(BufferedInputStream bs, int length){
		_length = length;
		_bis = bs;
	}
	
}



public class WhalinMemcachedFinalOracleBGClient extends DB implements JdbcDBMemCachedClientConstants {
	private static String FSimagePath = "";
	private HashMap<String, ImgHandler> imageHandlers = null;
	private static int INPUTSTREAM_TIMEOUT = 10000000;
	private static boolean ManageCache = false;
	private static boolean verbose = false;
	private static String LOGGER = "ERROR, A1";
	private static boolean initialized = false;
	private boolean shutdown = false;
	private Properties props;
	private static final String DEFAULT_PROP = "";
	private static final int CACHE_START_WAIT_TIME = 10000;
	private ConcurrentMap<Integer, PreparedStatement> newCachedStatements;
	private PreparedStatement preparedStatement = null;
	private Connection conn;
	StartProcess st;
	//String cache_cmd = "C:\\PSTools\\psexec \\\\"+COSARServer.cacheServerHostname+" -u shahram -p 2Shahram C:\\cosar\\configurable\\TCache.NetworkInterface.exe C:\\cosar\\configurable\\V2gb.xml ";
	//String cache_cmd_stop = "java -jar C:\\BG\\ShutdownCOSAR.jar";
	
	//private static Vector<MemcachedClient> cacheclient_vector = new Vector<MemcachedClient>();
	SockIOPool cacheConnectionPool;
	private MemcachedClient cacheclient = null;
	private static String cache_hostname = "";
	private static Integer cache_port = -1;
	private boolean isInsertImage;
	
	private static final int MAX_NUM_RETRIES = 10;
	private static final int TIMEOUT_WAIT_MILI = 100;
	
	//String cache_cmd = "C:\\cosar\\conficacheclientle\\TCache.NetworkInterface.exe C:\\cosar\\configurable\\V2gb.xml ";
	public static final int CACHE_POOL_NUM_CONNECTIONS = 400;
	
	private static AtomicInteger NumThreads = null;
	private static Semaphore crtcl = new Semaphore(1, true);
	private static int GETFRNDCNT_STMT = 2;
	private static int GETPENDCNT_STMT = 3;
	private static int GETRESCNT_STMT = 4;
	private static int GETPROFILE_STMT = 5;
	private static int GETPROFILEIMG_STMT = 6;
	private static int GETFRNDS_STMT = 7;
	private static int GETFRNDSIMG_STMT = 8;
	private static int GETPEND_STMT = 9;
	private static int GETPENDIMG_STMT = 10;
	private static int REJREQ_STMT = 11;
	private static int ACCREQ_STMT = 12;
	private static int INVFRND_STMT = 13;
	private static int UNFRNDFRND_STMT = 14;
	private static int GETTOPRES_STMT = 15;
	private static int GETRESCMT_STMT = 16;
	private static int POSTCMT_STMT = 17;
	private static int IMAGE_SIZE_GRAN = 1024;
	private int THUMB_IMAGE_SIZE = 2*1024;
	
	private static final double TTL_RANGE_PERCENT = 0.2; 
	private boolean useTTL = false;
	private int TTLvalue = 0;
	private boolean compressPayload = false;

	private boolean StoreImageInFS(String userid, byte[] image, boolean profileimg){
		boolean result = true;
		String ext = "thumbnail";
		
		if (profileimg) ext = "profile";
		
		String ImageFileName = FSimagePath+"\\img"+userid+ext;
		
		File tgt = new File(ImageFileName);
		if ( tgt.exists() ){
			if (! tgt.delete() ) {
				System.out.println("Error, file exists and failed to delete");
				return false;
			}
		}

		//Write the file
		try{
			FileOutputStream fos = new FileOutputStream(ImageFileName);
			fos.write(image);
			fos.close();
		}catch(Exception ex){
			System.out.println("Error in writing the file"+ImageFileName);
			ex.printStackTrace(System.out);
		}

		return result;
	}
	
	 private byte[] GetImageFromFS(String userid, boolean profileimg){
         int filelength = 0;
         String ext = "thumbnail";
         byte[] imgpayload = null;
         BufferedInputStream bis = null;
        
         if (profileimg) ext = "profile";
        
         String ImageFileName = FSimagePath+"\\img"+userid+ext;
        int attempt = 100;
        while(attempt>0){
	         try {
	              /*   if(imageHandlers.get(ImageFileName) != null){
	    	        	 imgpayload = new byte[imageHandlers.get(ImageFileName).get_length()];
	    	        	 bis = imageHandlers.get(ImageFileName).get_bis();
	    	        	 filelength = imageHandlers.get(ImageFileName).get_length();
	    	         }else{
	    	        	 File fsimage = new File(ImageFileName); 
	    	             filelength = (int) fsimage.length();
	    	             imgpayload = new byte[filelength];
	    	             bis = new BufferedInputStream(new FileInputStream(fsimage));
	    	             ImgHandler newImage = new ImgHandler(bis, filelength);
	    	             imageHandlers.put(ImageFileName, newImage);
	    	             bis.mark(INPUTSTREAM_TIMEOUT);
	    	         }
	    	       
	    	        if(bis.available() == 0)
	    	        	bis.reset();
	    	        	
	                 int read = 0;
	                 int numRead = 0;
	                 while (read < filelength && (numRead=bis.read(imgpayload, read, filelength - read    ) ) >= 0) {
	                         read = read + numRead;
	                 }*/
		        	 FileInputStream fis = null;
		        	 DataInputStream dis = null;
		        	 File fsimage = new File(ImageFileName); 
		             filelength = (int) fsimage.length();
		             imgpayload = new byte[filelength];
		             fis = new FileInputStream(fsimage);
		             dis = new DataInputStream(fis);
		             int read = 0;
		             int numRead = 0;
		             while (read < filelength && (numRead=dis.read(imgpayload, read, filelength - read    ) ) >= 0) {
		                     read = read + numRead;
		             }
		             dis.close();
		             fis.close();
		             break;
	         } catch (IOException e) {
	                 // TODO Auto-generated catch block
	               //  e.printStackTrace(System.out);
	        	 attempt--;
	         }
        }


         return imgpayload;
 }

	
	private String getCacheCmd()
	{		
		return "C:\\PSTools\\psexec \\\\"+cache_hostname+" -u shahram -p 2Shahram C:\\memcached\\memcached.exe -d start ";
	}
	
	private String getCacheStopCmd()
	{
		return "C:\\PSTools\\psexec \\\\"+cache_hostname+" -u shahram -p 2Shahram C:\\memcached\\memcached.exe -d stop ";
	}
	
	private PreparedStatement createAndCacheStatement(int stmttype, String query) throws SQLException{
		PreparedStatement newStatement = conn.prepareStatement(query);
		PreparedStatement stmt = newCachedStatements.putIfAbsent(stmttype, newStatement);
		if (stmt == null) return newStatement;
		else return stmt;
	}


	private static int incrementNumThreads() {
        int v;
        do {
            v = NumThreads.get();
        } while (!NumThreads.compareAndSet(v, v + 1));
        return v + 1;
    }
	
	private static int decrementNumThreads() {
        int v;
        do {
            v = NumThreads.get();
        } while (!NumThreads.compareAndSet(v, v - 1));
        return v - 1;
    }
	
	private void cleanupAllConnections() {
		//close all image handlers
				if(imageHandlers != null){
					Set<String> keys = imageHandlers.keySet();
					Iterator<String> it = keys.iterator();
					while(it.hasNext()){
						try {
							imageHandlers.get(it.next()).get_bis().close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
		try {
			//close all cached prepare statements
			Set<Integer> statementTypes = newCachedStatements.keySet();
			Iterator<Integer> it = statementTypes.iterator();
			while(it.hasNext()){
				int stmtType = it.next();
				if(newCachedStatements.get(stmtType) != null) newCachedStatements.get(stmtType).close();
			}
			if(conn != null) conn.close();
		} catch (SQLException e) {
			e.printStackTrace(System.out);
		}
	}

	/**
	 * Initialize the database connection and set it up for sending requests to the database.
	 * This must be called once per client.
	 */


	@Override

	public boolean init() throws DBException {
		
		props = getProperties();
		String urls = props.getProperty(CONNECTION_URL, DEFAULT_PROP);
		String user = props.getProperty(CONNECTION_USER, DEFAULT_PROP);
		String passwd = props.getProperty(CONNECTION_PASSWD, DEFAULT_PROP);

		FSimagePath = props.getProperty(FS_PATH, DEFAULT_PROP);
		if(!FSimagePath.equals("")){
			imageHandlers = new HashMap<String, ImgHandler>();
			//imageBytes = new HashMap<String, byte[]>();
		}
		String driver = props.getProperty(DRIVER_CLASS, DEFAULT_PROP);

		cache_hostname = props.getProperty(MEMCACHED_SERVER_HOST, MEMCACHED_SERVER_HOST_DEFAULT);
		cache_port = Integer.parseInt(props.getProperty(MEMCACHED_SERVER_PORT, MEMCACHED_SERVER_PORT_DEFAULT));
		
		ManageCache = Boolean.parseBoolean(
				props.getProperty(MANAGE_CACHE_PROPERTY, 
						MANAGE_CACHE_PROPERTY_DEFAULT));
		
		isInsertImage = Boolean.parseBoolean(props.getProperty(Client.INSERT_IMAGE_PROPERTY, Client.INSERT_IMAGE_PROPERTY_DEFAULT));
		TTLvalue = Integer.parseInt(props.getProperty(TTL_VALUE, TTL_VALUE_DEFAULT));
		useTTL = (TTLvalue != 0);
		
		compressPayload = Boolean.parseBoolean(props.getProperty(ENABLE_COMPRESSION_PROPERTY, ENABLE_COMPRESSION_PROPERTY_DEFAULT));
		
		try {
			if (driver != null) {
				Class.forName(driver);
			}
			for (String url: urls.split(",")) {
				System.out.println("Adding shard node URL: " + url);
				conn = DriverManager.getConnection(url, user, passwd);
				// Since there is no explicit commit method in the DB interface, all
				// operations should auto commit.
				conn.setAutoCommit(true);
			}

			newCachedStatements = new ConcurrentHashMap<Integer, PreparedStatement>();

		} catch (ClassNotFoundException e) {
			System.out.println("Error in initializing the JDBS driver: " + e);
			e.printStackTrace(System.out);
			return false;
		} catch (SQLException e) {
			System.out.println("Error in database operation: " + e);
			//System.out.println("Continuing execution...");
			e.printStackTrace(System.out);
			return false;
		} catch (NumberFormatException e) {
			System.out.println("Invalid value for fieldcount property. " + e);
			e.printStackTrace(System.out);
			return false;
		}

	

		try {
			crtcl.acquire();
			
			if(NumThreads == null)
			{
				NumThreads = new AtomicInteger();
				NumThreads.set(0);
			}
			
			incrementNumThreads();
			
		}catch (Exception e){
			System.out.println("SQLTrigQR init failed to acquire semaphore.");
			e.printStackTrace(System.out);
		}
		if (initialized) {
			cacheclient = new MemcachedClient();
			//cacheclient_vector.add(cacheclient);
			
			crtcl.release();
			//System.out.println("Client connection already initialized.");
			return true;
		}
		
		//useTTL = Integer.parseInt(props.getProperty("ttlvalue", "0")) != 0;
		
		Properties prop = new Properties();
		prop.setProperty("log4j.rootLogger", "ERROR, A1");
		//prop.setProperty("log4j.appender.A1", "org.apache.log4j.ConsoleAppender");
		prop.setProperty("log4j.appender.A1", "org.apache.log4j.FileAppender");
		prop.setProperty("log4j.appender.A1.File", "whalincache.out");
		prop.setProperty("log4j.appender.A1.Append", "false");
		prop.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
		prop.setProperty("log4j.appender.A1.layout.ConversionPattern", "%-4r %-5p [%t] %37c %3x - %m%n");
		PropertyConfigurator.configure(prop);
		
		//String[] servers = { "192.168.1.1:1624", "192.168.1.1:1625" };
		String[] servers = { cache_hostname + ":" + cache_port };
		cacheConnectionPool = SockIOPool.getInstance();
		cacheConnectionPool.setServers( servers );
		cacheConnectionPool.setFailover( true );
		cacheConnectionPool.setInitConn( 10 ); 
		cacheConnectionPool.setMinConn( 5 );
		cacheConnectionPool.setMaxConn( CACHE_POOL_NUM_CONNECTIONS );
		cacheConnectionPool.setMaintSleep( 30 );
		cacheConnectionPool.setNagle( false );
		cacheConnectionPool.setSocketTO( 3000 );
		cacheConnectionPool.setAliveCheck( true );
		cacheConnectionPool.initialize();
		
		if (ManageCache) {
			System.out.println("Starting Cache: "+this.getCacheCmd());
			//this.st = new StartCOSAR(this.cache_cmd + (RaysConfig.cacheServerPort + i), "cache_output" + i + ".txt"); 
			this.st = new StartProcess(this.getCacheCmd(), "cache_output.txt");
			this.st.start();

			System.out.println("Wait for "+CACHE_START_WAIT_TIME/1000+" seconds to allow Cache to startup.");
			try{
				Thread.sleep(CACHE_START_WAIT_TIME);
			}catch(Exception e)
			{
				e.printStackTrace(System.out);
			}
		}
		
		

		
		
//		builder = new MemcachedClientBuilder( 
//				AddrUtil.getAddresses(cache_hostname + ":" + cache_port));
//		builder.setConnectionPoolSize(CACHE_POOL_NUM_CONNECTIONS);

		initialized = true;
		try {
			cacheclient = new MemcachedClient();
			//cacheclient_vector.add(cacheclient);
						
			crtcl.release();
		} catch (Exception e) {
			System.out.println("MemcacheClient init failed to release semaphore.");
			e.printStackTrace(System.out);
		}
		return true;
	}

	@Override
	public void cleanup(boolean warmup) throws DBException {
		try {
			//System.out.println("************number of threads="+NumThreads);
			if (warmup) decrementNumThreads();
			if (verbose) System.out.println("Cleanup (before warmup-chk):  NumThreads="+NumThreads);
			if(!warmup){
				crtcl.acquire();
								
				decrementNumThreads();
				if (verbose) System.out.println("Cleanup (after warmup-chk):  NumThreads="+NumThreads);
				if (NumThreads.get() > 0){
					crtcl.release();
					//cleanupAllConnections();
					//System.out.println("Active clients; one of them will clean up the cache manager.");
					return;
				}
				
//				for(MemcachedClient client : cacheclient_vector)
//				{
//					client.shutdown();
//				}

				//MemcachedClient cacheclient = new MemcachedClient(
				//		AddrUtil.getAddresses(cache_hostname + ":" + cache_port));
				//cacheclient.printStats();
				//cacheclient.stats();
				//cacheclient.shutdown();
				
				cacheConnectionPool = SockIOPool.getInstance();
				cacheConnectionPool.shutDown();

				if (ManageCache){
					//MemcachedClient cache_conn = new MemcachedClient(COSARServer.cacheServerHostname, COSARServer.cacheServerPort);			
					//cache_conn.shutdownServer();
					System.out.println("Stopping Cache: "+this.getCacheStopCmd());
					//this.st = new StartCOSAR(this.cache_cmd + (RaysConfig.cacheServerPort + i), "cache_output" + i + ".txt"); 
					this.st = new StartProcess(this.getCacheStopCmd(), "cache_output.txt");
					this.st.start();
					System.out.print("Waiting for COSAR to finish.");

					if( this.st != null )
						this.st.join();
					Thread.sleep(10000);
					System.out.println("..Done!");
				}
				cleanupAllConnections();
				shutdown = true;
				crtcl.release();
			}
		} catch (InterruptedException IE) {
			System.out.println("Error in cleanup:  Semaphore interrupt." + IE);
			throw new DBException(IE);
		} catch (Exception e) {

			System.out.println("Error in closing the connection. " + e);
			throw new DBException(e);
		}
	}

	
	
	
	@Override
	//Load phase query not cached
	public int insertEntity(String entitySet, String entityPK, HashMap<String, ByteIterator> values, boolean insertImage) {
		if (entitySet == null) {
			return -1;
		}
		if (entityPK == null) {
			return -1;
		}
		ResultSet rs =null;

		try {
			String query;
			int numFields = values.size();
			
			if(entitySet.equalsIgnoreCase("users") && insertImage && !FSimagePath.equals(""))
				numFields = numFields-2;
			query = "INSERT INTO "+entitySet+" VALUES (";
			//adding the three new attributes
			if(entitySet.equalsIgnoreCase("users"))
				numFields +=3;
			for(int j=0; j<=numFields; j++){
				if(j==(numFields)){
					query+="?)";
					break;
				}else
					query+="?,";
			}

			preparedStatement = conn.prepareStatement(query);
			preparedStatement.setString(1, entityPK);
			int cnt=2;
			for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
				//blobs need to be inserted differently
				if(entry.getKey().equalsIgnoreCase("pic") || entry.getKey().equalsIgnoreCase("tpic") )
					continue;
				
				String field = entry.getValue().toString();
				preparedStatement.setString(cnt, field);
				cnt++;
				if (verbose) System.out.println(""+entry.getKey().toString()+":"+field);
			}
			
			if(entitySet.equalsIgnoreCase("users") && insertImage){
				//create the profile image
				byte[] profileImage = ((ObjectByteIterator)values.get("pic")).toArray();
				InputStream is = new ByteArrayInputStream(profileImage);
				if ( FSimagePath.equals("") ){
					preparedStatement.setBinaryStream(cnt, is, profileImage.length);
					cnt++;
				}
				else
					StoreImageInFS(entityPK, profileImage, true);
				byte[] thumbImage = ((ObjectByteIterator)values.get("tpic")).toArray();
				is = new ByteArrayInputStream(thumbImage);
				
				if (FSimagePath.equals("")){
					preparedStatement.setBinaryStream(cnt, is, thumbImage.length);
					cnt++;
				}else
					StoreImageInFS(entityPK, thumbImage, false);
			}
			if(entitySet.equalsIgnoreCase("users")){
				//pendCount, confCount,resCount
				preparedStatement.setInt(cnt, 0);
				cnt++;
				preparedStatement.setInt(cnt, 0);
				cnt++;
				preparedStatement.setInt(cnt, 0);
				cnt++;
			}else{
				//increament the resource count for the walluserid
				String updateQ = "Update users set rescount=rescount+1 where userid="+values.get("walluserid");
				Statement st = conn.createStatement();
				st.executeUpdate(updateQ);
				st.close();
			}
			rs = preparedStatement.executeQuery();
		} catch (SQLException e) {
			System.out.println("Error in processing insert to table: " + entitySet + e);
			return -2;
		}finally{
			try {
				if (rs != null)
					rs.close();
				if(preparedStatement != null)
					preparedStatement.close();
			} catch (SQLException e) {
				e.printStackTrace(System.out);
				return -1;
			}
		}
		return 0;
	}

	
	
	
	@Override
	public int viewProfile(int requesterID, int profileOwnerID,
			HashMap<String, ByteIterator> result, boolean insertImage, boolean testMode) {

		if (verbose) System.out.print("Get Profile "+requesterID+" "+profileOwnerID);
		ResultSet rs = null;
		int retVal = SUCCESS;
		if(requesterID < 0 || profileOwnerID < 0)
			return -1;

		byte[] payload;
		String key;
		String query="";
		String uid="";
		
		//MemcachedClient cacheclient=null;
		HashMap<String, ByteIterator> SR = new HashMap<String, ByteIterator>(); 

		// Initialize query logging for the send procedure
		//cacheclient.startQueryLogging();
		
		// Check Cache first
		if(insertImage)
		{
			key = "profile"+profileOwnerID;
		}
		else
		{
			key = "profileNoImage"+profileOwnerID;
		}
		try{
			//cacheclient = new MemcachedClient(AddrUtil.getAddresses(cache_hostname + ":" + cache_port));
			payload = common.CacheUtilities.CacheGet(this.cacheclient, key, this.compressPayload);
			if (payload != null && CacheUtilities.unMarshallHashMap(result, payload)){
				if (verbose) System.out.println("... Cache Hit!");
					
				//cacheclient.shutdown();
				return retVal;
			} else if (verbose) System.out.println("... Query DB.");
		} catch (Exception e1) {
			e1.printStackTrace(System.out);
			System.exit(-1);
		} 
		
		try {
			if(insertImage && FSimagePath.equals("")){
				query = "SELECT userid,username, fname, lname, gender, dob, jdate, ldate, address, email, tel, pic, pendcount, confcount, rescount FROM  users WHERE UserID = ?";
				if((preparedStatement = newCachedStatements.get(GETPROFILEIMG_STMT)) == null)
					preparedStatement = createAndCacheStatement(GETPROFILEIMG_STMT, query);	
			}else{
				query = "SELECT userid,username, fname, lname, gender, dob, jdate, ldate, address, email, tel, pendcount, confcount, rescount FROM  users WHERE UserID = ?";
				
				if((preparedStatement = newCachedStatements.get(GETPROFILE_STMT)) == null)
					preparedStatement = createAndCacheStatement(GETPROFILE_STMT, query);	
				
			}
			//preparedStatement = conn.prepareStatement(query);
			preparedStatement.setInt(1, profileOwnerID);
			
			rs = preparedStatement.executeQuery();
			ResultSetMetaData md = rs.getMetaData();
			int col = md.getColumnCount();
			if(rs.next()){
				for (int i = 1; i <= col; i++){
					String col_name = md.getColumnName(i);
					String value ="";
					
					if (col_name.equalsIgnoreCase("userid")){
						uid = rs.getString(col_name);
					}
					if(col_name.equalsIgnoreCase("pic") ){
						// Get as a BLOB
						Blob aBlob = rs.getBlob(col_name);
						byte[] allBytesInBlob = aBlob.getBytes(1, (int) aBlob.length());
						//if test mode dump pic into a file
						if(testMode){
							//dump to file
							try{
								FileOutputStream fos = new FileOutputStream(profileOwnerID+"-proimage.bmp");
								fos.write(allBytesInBlob);
								fos.close();
							}catch(Exception ex){
							}
						}
						
						// TODO: is this copy necessary? maybe when there are multiple rows in the resultset?
						//byte[] val = new byte[allBytesInBlob.length];
						//System.arraycopy( allBytesInBlob, 0, val, 0, allBytesInBlob.length ); 
						SR.put(col_name, new ObjectByteIterator(allBytesInBlob));
						result.put(col_name, new ObjectByteIterator(allBytesInBlob));
					}else if (col_name.equalsIgnoreCase("rescount")){
						SR.put("resourcecount", new ObjectByteIterator(rs.getString(col_name).getBytes())) ;
						result.put("resourcecount", new ObjectByteIterator(rs.getString(col_name).getBytes())) ;
					}else if(col_name.equalsIgnoreCase("pendcount")){
						SR.put("pendingcount", new ObjectByteIterator(rs.getString(col_name).getBytes())) ;
						result.put("pendingcount", new ObjectByteIterator(rs.getString(col_name).getBytes())) ;
					}else if(col_name.equalsIgnoreCase("confcount")){
						SR.put("friendcount", new ObjectByteIterator(rs.getString(col_name).getBytes())) ;
						result.put("friendcount", new ObjectByteIterator(rs.getString(col_name).getBytes())) ;
					}else{
						value = rs.getString(col_name);

						SR.put(col_name, new ObjectByteIterator(value.getBytes()));
						result.put(col_name, new ObjectByteIterator(value.getBytes()));
					}
				}
				
				//Fetch the profile image from the file system
				if (insertImage && !FSimagePath.equals("") ){
					//Get the profile image from the file
					byte[] profileImage = GetImageFromFS(uid, true);
					if(testMode){
						//dump to file
						try{
							FileOutputStream fos = new FileOutputStream(profileOwnerID+"-proimage.bmp");
							fos.write(profileImage);
							fos.close();
						}catch(Exception ex){
						}
					}
					result.put("pic", new ObjectByteIterator(profileImage) );
				}
			}

		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}finally{
			try {
				if (rs != null)
					rs.close();
				if(preparedStatement != null)
					preparedStatement.clearParameters();
					//preparedStatement.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}

		//serialize the result hashmap and insert it in the cache for future use
		payload = CacheUtilities.SerializeHashMap(SR);
		try {
			if(!common.CacheUtilities.CacheSet(this.cacheclient, key, payload, this.useTTL, this.TTLvalue, this.compressPayload))
			{
				throw new Exception("Error calling WhalinMemcached set");
			}
			//while(setResult.isDone() == false);
			//cacheclient.shutdown();
		} catch (Exception e1) {
			System.out.println("Error in ApplicationCacheClient, failed to insert the key-value pair in the cache.");
			e1.printStackTrace(System.out);
			retVal = -2;
		}
		return retVal;
	}


	@Override
	public int listFriends(int requesterID, int profileOwnerID,
			Set<String> fields, Vector<HashMap<String, ByteIterator>> result, boolean insertImage, boolean testMode) {
		if (verbose) System.out.print("List friends... "+profileOwnerID);
		int retVal = SUCCESS;
		String uid = "0";
		ResultSet rs = null;
		if(requesterID < 0 || profileOwnerID < 0)
			return -1;

		//String key = "lsFrds:"+requesterID+":"+profileOwnerID;
		String key;
		String query="";
		//MemcachedClient cacheclient=null;
		if(insertImage)
		{
			key = "lsFrds:"+profileOwnerID;
		}
		else
		{
			key = "lsFrdsNoImage:"+profileOwnerID;
		}
		// Check Cache first
		try{
			//cacheclient = new MemcachedClient(AddrUtil.getAddresses(cache_hostname + ":" + cache_port));
			byte[] payload = common.CacheUtilities.CacheGet(this.cacheclient, key, this.compressPayload);
			if (payload != null){
				if (verbose) System.out.println("... Cache Hit!");
				if (!CacheUtilities.unMarshallVectorOfHashMaps(payload,result))
					System.out.println("Error in ApplicationCacheClient: Failed to unMarshallVectorOfHashMaps");
				
				//cacheclient.shutdown();
				return retVal;
			} else if (verbose) System.out.println("... Query DB.");
		} catch (Exception e1) {
			e1.printStackTrace(System.out);
			System.out.println("Error in ApplicationCacheClient, getListOfFriends failed to serialize a vector of hashmaps.");
			retVal = -2;
		}

		
		try {
			if(insertImage && FSimagePath.equals("")){
				query = "SELECT userid,inviterid, inviteeid, username, fname, lname, gender, dob, jdate, ldate, address,email,tel,tpic FROM users, friendship WHERE ((inviterid=? and userid=inviteeid) or (inviteeid=? and userid=inviterid)) and status = 2";
				//cacheclient.addQuery("SELECT userid, username, fname, lname, gender, dob, jdate, ldate, address,email,tel,tpic FROM users, friendship WHERE ((inviterid="+profileOwnerID+" and userid=inviteeid) or (inviteeid="+profileOwnerID+" and userid=inviterid)) and status = 2");
				if((preparedStatement = newCachedStatements.get(GETFRNDSIMG_STMT)) == null)
					preparedStatement = createAndCacheStatement(GETFRNDSIMG_STMT, query);	
				
			}else{
				query = "SELECT userid,inviterid, inviteeid, username, fname, lname, gender, dob, jdate, ldate, address,email,tel FROM users, friendship WHERE ((inviterid=? and userid=inviteeid) or (inviteeid=? and userid=inviterid)) and status = 2";
				//cacheclient.addQuery("SELECT userid, username, fname, lname, gender, dob, jdate, ldate, address,email,tel FROM users, friendship WHERE ((inviterid="+profileOwnerID+" and userid=inviteeid) or (inviteeid="+profileOwnerID+" and userid=inviterid)) and status = 2");
				if((preparedStatement = newCachedStatements.get(GETFRNDS_STMT)) == null)
					preparedStatement = createAndCacheStatement(GETFRNDS_STMT, query);	
				
			}
			//preparedStatement = conn.prepareStatement(query);
			preparedStatement.setInt(1, profileOwnerID);
			preparedStatement.setInt(2, profileOwnerID);
			////cacheclient.addQuery("SELECT * FROM users, friendship WHERE ((inviterid="+profileOwnerID+" and userid=inviteeid) or (inviteeid="+profileOwnerID+" and userid=inviterid)) and status = 2");
			rs = preparedStatement.executeQuery();
			int cnt =0;
			while (rs.next()){
				cnt++;
				HashMap<String, ByteIterator> values = new HashMap<String, ByteIterator>();
				if (fields != null) {
					for (String field : fields) {
						String value = rs.getString(field);
						if(field.equalsIgnoreCase("userid"))
							field = "userid";
						values.put(field, new ObjectByteIterator(value.getBytes()));
					}
					result.add(values);
				}else{
					//get the number of columns and their names
					//Statement st = conn.createStatement();
					//ResultSet rst = st.executeQuery("SELECT * FROM users");
					ResultSetMetaData md = rs.getMetaData();
					int col = md.getColumnCount();
					for (int i = 1; i <= col; i++){
						String col_name = md.getColumnName(i);
						String value="";
						if(col_name.equalsIgnoreCase("tpic")){
							//Get as a BLOB
							Blob aBlob = rs.getBlob(col_name);
							byte[] allBytesInBlob = aBlob.getBytes(1, (int) aBlob.length());
							
							if(testMode){
								//dump to file
								try{
									FileOutputStream fos = new FileOutputStream(profileOwnerID+"-"+cnt+"-thumbimage.bmp");
									fos.write(allBytesInBlob);
									fos.close();
								}catch(Exception ex){
								}
							}
							//byte[] val = new byte[allBytesInBlob.length];
							//System.arraycopy( allBytesInBlob, 0, val, 0, allBytesInBlob.length ); 
							values.put(col_name, new ObjectByteIterator(allBytesInBlob));
						}else{
							value = rs.getString(col_name);
							if(col_name.equalsIgnoreCase("userid")){
								uid = value;
								col_name = "userid";
							}
							values.put(col_name, new ObjectByteIterator(value.getBytes()));
						}

					}
					//Fetch the thumbnail image from the file system
					if (insertImage && !FSimagePath.equals("") ){
						byte[] thumbImage = GetImageFromFS(uid, false);
						//Get the thumbnail image from the file
						if(testMode){
							//dump to file
							try{
								FileOutputStream fos = new FileOutputStream(profileOwnerID+"-"+cnt+"-thumbimage.bmp");
								fos.write(thumbImage);
								fos.close();
							}catch(Exception ex){
							}
						}
						values.put("tpic", new ObjectByteIterator(thumbImage) );
					}
					result.add(values);
				}
			}
		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}finally{
			try {
				if (rs != null)
					rs.close();
				if(preparedStatement != null)
					preparedStatement.clearParameters();
					//preparedStatement.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}

		//serialize the result hashmap and insert it in the cache for future use
		byte[] payload = CacheUtilities.SerializeVectorOfHashMaps(result);
		try {
			if(!common.CacheUtilities.CacheSet(this.cacheclient, key, payload, this.useTTL, this.TTLvalue, this.compressPayload))
			{
				throw new Exception("Error calling WhalinMemcached set");
			}
			//cacheclient.shutdown();
		} catch (Exception e1) {
			System.out.println("Error in ApplicationCacheClient, getListOfFriends failed to insert the key-value pair in the cache.");
			e1.printStackTrace(System.out);
			retVal = -2;
		}

		return retVal;		
	}

	@Override
	public int viewFriendReq(int profileOwnerID,
			Vector<HashMap<String, ByteIterator>> result,  boolean insertImage, boolean testMode) {

		int retVal = SUCCESS;
		ResultSet rs = null;

		if (verbose) System.out.print("viewPendingRequests "+profileOwnerID+" ...");

		if(profileOwnerID < 0)
			return -1;

		String key;
		String query="";
		String uid = "0";
		//MemcachedClient cacheclient=null;
		
		if(insertImage)
		{
			key = "viewPendReq:"+profileOwnerID;
		}
		else
		{
			key = "viewPendReqNoImage:"+profileOwnerID;
		}
		// Check Cache first
		try { 
			//cacheclient = new MemcachedClient(AddrUtil.getAddresses(cache_hostname + ":" + cache_port));
			byte[] payload = common.CacheUtilities.CacheGet(this.cacheclient, key, this.compressPayload);
			if (payload != null){
				if (!CacheUtilities.unMarshallVectorOfHashMaps(payload,result))
					System.out.println("Error in ApplicationCacheClient: Failed to unMarshallVectorOfHashMaps");
				if (verbose) System.out.println("... Cache Hit!");
				//cacheclient.shutdown();
				return retVal;
			} else if (verbose) System.out.println("... Query DB.");
		} catch (Exception e1) {
			e1.printStackTrace(System.out);
			System.out.println("Error in ApplicationCacheClient, viewPendingRequests failed to serialize a vector of hashmaps.");
			retVal = -2;
		}


		try {
			if(insertImage && FSimagePath.equals("")){
				query = "SELECT userid, inviterid, inviteeid, username, fname, lname, gender, dob, jdate, ldate, address,email,tel,tpic FROM users, friendship WHERE inviteeid=? and status = 1 and inviterid = userid";
				if((preparedStatement = newCachedStatements.get(GETPENDIMG_STMT)) == null)
					preparedStatement = createAndCacheStatement(GETPENDIMG_STMT, query);	
				
			}else {
				query = "SELECT userid,inviterid, inviteeid, username, fname, lname, gender, dob, jdate, ldate, address,email,tel FROM users, friendship WHERE inviteeid=? and status = 1 and inviterid = userid";
				if((preparedStatement = newCachedStatements.get(GETPEND_STMT)) == null)
					preparedStatement = createAndCacheStatement(GETPEND_STMT, query);		
			}
			//preparedStatement = conn.prepareStatement(query);
			preparedStatement.setInt(1, profileOwnerID);
			////cacheclient.addQuery("SELECT * FROM users, friendship WHERE inviteeid= and status = 1 and inviterid = userid");
			rs = preparedStatement.executeQuery();
			int cnt=0;
			while (rs.next()){
				cnt++;
				HashMap<String, ByteIterator> values = new HashMap<String, ByteIterator>();
				//get the number of columns and their names
				ResultSetMetaData md = rs.getMetaData();
				int col = md.getColumnCount();
				for (int i = 1; i <= col; i++){
					String col_name = md.getColumnName(i);
					String value = "";
					if(col_name.equalsIgnoreCase("tpic")){
						// Get as a BLOB
						Blob aBlob = rs.getBlob(col_name);
						byte[] allBytesInBlob = aBlob.getBytes(1, (int) aBlob.length());
						
						if(testMode){
							//dump to file
							try{
								FileOutputStream fos = new FileOutputStream(profileOwnerID+"-"+cnt+"-ctthumbimage.bmp");
								fos.write(allBytesInBlob);
								fos.close();
							}catch(Exception ex){
							}
						}
						//byte[] val = new byte[allBytesInBlob.length];
						//System.arraycopy( allBytesInBlob, 0, val, 0, allBytesInBlob.length ); 
						values.put(col_name, new ObjectByteIterator(allBytesInBlob));
					}else{
						value = rs.getString(col_name);
						if(col_name.equalsIgnoreCase("userid")){
							uid = value;
							col_name = "userid";
						}
						values.put(col_name, new ObjectByteIterator(value.getBytes()));
					}

				}
				//Fetch the thumbnail image from the file system
				if (insertImage && !FSimagePath.equals("") ){
					byte[] thumbImage = GetImageFromFS(uid, false);
					//Get the thumbnail image from the file
					if(testMode){
						//dump to file
						try{
							FileOutputStream fos = new FileOutputStream(profileOwnerID+"-"+cnt+"-thumbimage.bmp");
							fos.write(thumbImage);
							fos.close();
						}catch(Exception ex){
						}
					}
					values.put("tpic", new ObjectByteIterator(thumbImage) );
				}
				result.add(values);
			}
		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}finally{
			try {
				if (rs != null)
					rs.close();
				if(preparedStatement != null)
					preparedStatement.clearParameters();
					//preparedStatement.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}

		byte[] payload = CacheUtilities.SerializeVectorOfHashMaps(result);
		try {
			if(!common.CacheUtilities.CacheSet(this.cacheclient, key, payload, this.useTTL, this.TTLvalue, this.compressPayload))
			{
				throw new Exception("Error calling WhalinMemcached set");
			}
			//cacheclient.shutdown();
		} catch (Exception e1) {
			System.out.println("Error in ApplicationCacheClient, getListOfFriends failed to insert the key-value pair in the cache.");
			e1.printStackTrace(System.out);
			retVal = -2;
		}
		return retVal;		
	}

	@Override
	public int acceptFriend(int inviterID, int inviteeID) {

		int retVal = SUCCESS;
		if(inviterID < 0 || inviteeID < 0)
			return -1;
		String query;
		CallableStatement proc = null;
		try {
            proc = conn.prepareCall("{ call ACCEPTFRIEND(?, ?) }");
			proc.setInt(1, inviterID);
		    proc.setInt(2, inviteeID);
		    proc.execute();

			if(!useTTL)
			{
				String key;
				
				if(isInsertImage)
				{
					key = "lsFrds:"+inviterID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "lsFrds:"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					//Invalidate the friendcount for each member
					key = "profile"+inviterID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "profile"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "viewPendReq:"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
				}
				else
				{
					key = "lsFrdsNoImage:"+inviterID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "lsFrdsNoImage:"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					//Invalidate the friendcount for each member
					key = "profileNoImage"+inviterID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "profileNoImage"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "viewPendReqNoImage:"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
				}	
			}
		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}catch (Exception e1) {
			e1.printStackTrace(System.out);
			System.exit(-1);
		}finally{
			try {
				if(proc != null)
					proc.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}
		return retVal;		
	}

	@Override
	public int rejectFriend(int inviterID, int inviteeID) {
		int retVal = SUCCESS;
		if(inviterID < 0 || inviteeID < 0)
			return -1;

		CallableStatement proc = null;
		try {
			proc = conn.prepareCall("{ call REJECTFRIEND(?, ?) }");
			proc.setInt(1, inviterID);
		    proc.setInt(2, inviteeID);
		    proc.execute();

			if (!useTTL)
			{
				String key;
				if(isInsertImage)
				{
					key = "profile"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "viewPendReq:"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
				}
				else
				{
					key = "profileNoImage"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "viewPendReqNoImage:"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
				}
				
				key = "PendingFriendship:"+inviteeID;
				if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
				{
					throw new Exception("Error calling WhalinMemcached delete");
				}
			}
		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}catch (Exception e1) {
			e1.printStackTrace(System.out);
			System.exit(-1);
		}finally{
			try {
				if(proc != null)
					proc.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}
		return retVal;	
	}
	@Override
	//Load phase query not cached
	public int CreateFriendship(int memberA, int memberB) {
		int retVal = SUCCESS;
		if(memberA < 0 || memberB < 0)
			return -1;
		CallableStatement proc = null;
		try {
            proc = conn.prepareCall("{ call INSERTFRIEND(?, ?) }");
			proc.setInt(1, memberA);
		    proc.setInt(2, memberB);
		    proc.execute();
		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}finally{
			try {
				if(proc != null)
					proc.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}
		return retVal;
	}

	@Override
	public int inviteFriend(int inviterID, int inviteeID) {
		int retVal = SUCCESS;
		if(inviterID < 0 || inviteeID < 0)
			return -1;

		CallableStatement proc = null;
		try {
            proc = conn.prepareCall("{ call INVITEFRIEND(?, ?) }");
			proc.setInt(1, inviterID);
		    proc.setInt(2, inviteeID);
		    proc.execute();

			if (!useTTL)
			{
				String key;
				if(isInsertImage)
				{
					key = "profile"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "viewPendReq:"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
				}
				else
				{
					key = "profileNoImage"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "viewPendReqNoImage:"+inviteeID;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
				}
				
				key = "PendingFriendship:"+inviteeID;
				if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
				{
					throw new Exception("Error calling WhalinMemcached delete");
				}
			}
		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}catch (Exception e1) {
			e1.printStackTrace(System.out);
			System.exit(-1);
		}finally{
			try {
				if(proc != null)
					proc.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}
		return retVal;
	}

	@Override
	public int thawFriendship(int friendid1, int friendid2){
		int retVal = SUCCESS;
		if(friendid1 < 0 || friendid2 < 0)
			return -1;

		CallableStatement proc = null;
		try {
            proc = conn.prepareCall("{ call THAWFRIEND(?, ?) }");
			proc.setInt(1, friendid1);
		    proc.setInt(2, friendid2);
		    proc.execute();

			if (!useTTL)
			{				
				//Invalidate exisiting list of friends for each member
				String key;
				
				if(isInsertImage)
				{
					key = "lsFrds:"+friendid1;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "lsFrds:"+friendid2;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					//Invalidate the friendcount for each member
					key = "profile"+friendid1;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "profile"+friendid2;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
				}
				else
				{
					key = "lsFrdsNoImage:"+friendid1;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "lsFrdsNoImage:"+friendid2;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					//Invalidate the friendcount for each member
					key = "profileNoImage"+friendid1;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
		
					key = "profileNoImage"+friendid2;
					if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
					{
						throw new Exception("Error calling WhalinMemcached delete");
					}
				}
				
				key = "ConfirmedFriendship:"+friendid1;
				if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
				{
					throw new Exception("Error calling WhalinMemcached delete");
				}
				
				key = "ConfirmedFriendship:"+friendid2;				
				if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
				{
					throw new Exception("Error calling WhalinMemcached delete");
				}
			}
		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}catch (Exception e1) {
			e1.printStackTrace(System.out);
			System.exit(-1);
		}finally{
			try {
				if(proc != null)
					proc.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}
		return retVal;
	}

	@Override
	public int viewTopKResources(int requesterID, int profileOwnerID, int k,
			Vector<HashMap<String, ByteIterator>> result) {
		int retVal = SUCCESS;
		ResultSet rs = null;
		if(profileOwnerID < 0 || requesterID < 0 || k < 0)
			return -1;
		if (verbose) System.out.print("getTopKResources "+profileOwnerID+" ...");

		String key = "TopKRes:"+profileOwnerID;
		//MemcachedClient cacheclient=null;
		// Check Cache first
		try {
			//cacheclient = new MemcachedClient(AddrUtil.getAddresses(cache_hostname + ":" + cache_port));
			byte[] payload = common.CacheUtilities.CacheGet(this.cacheclient, key, this.compressPayload);
			if (payload != null && CacheUtilities.unMarshallVectorOfHashMaps(payload,result)){
				if (verbose) System.out.println("... Cache Hit!");
				//cacheclient.shutdown();
				return retVal;
			}
			else if (verbose) System.out.print("... Query DB!");
		} catch (Exception e1) {
			e1.printStackTrace(System.out);
			System.exit(-1);
		}

		String query = "SELECT * FROM resources WHERE walluserid = ? AND rownum <? ORDER BY rid desc";
		try {
			if((preparedStatement = newCachedStatements.get(GETTOPRES_STMT)) == null)
				preparedStatement = createAndCacheStatement(GETTOPRES_STMT, query);		
		
			//preparedStatement = conn.prepareStatement(query);
			preparedStatement.setInt(1, profileOwnerID);
			preparedStatement.setInt(2, (k+1));
			rs = preparedStatement.executeQuery();
			while (rs.next()){
				HashMap<String, ByteIterator> values = new HashMap<String, ByteIterator>();
				//get the number of columns and their names
				ResultSetMetaData md = rs.getMetaData();
				int col = md.getColumnCount();
				for (int i = 1; i <= col; i++){
					String col_name = md.getColumnName(i);
					String value = rs.getString(col_name);
					if(col_name.equalsIgnoreCase("rid"))
						col_name = "rid";
					else if(col_name.equalsIgnoreCase("walluserid"))
						col_name = "walluserid";
					else if (col_name.equalsIgnoreCase("creatorid"))
						col_name = "creatorid";
					
					values.put(col_name, new ObjectByteIterator(value.getBytes()));
				}
				result.add(values);
			}
		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}finally{
			try {
				if (rs != null)
					rs.close();
				if(preparedStatement != null)
					preparedStatement.clearParameters();
					//preparedStatement.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}

		if (retVal == SUCCESS){
			//serialize the result hashmap and insert it in the cache for future use
			byte[] payload = CacheUtilities.SerializeVectorOfHashMaps(result);
			try {
				if(!common.CacheUtilities.CacheSet(this.cacheclient, key, payload, this.useTTL, this.TTLvalue, this.compressPayload))
				{
					throw new Exception("Error calling WhalinMemcached set");
				}
				//cacheclient.shutdown();
			} catch (Exception e1) {
				System.out.println("Error in ApplicationCacheClient, getListOfFriends failed to insert the key-value pair in the cache.");
				e1.printStackTrace(System.out);
				retVal = -2;
			}
		}

		return retVal;		
	}

	public int getCreatedResources(int resourceCreatorID, Vector<HashMap<String, ByteIterator>> result) {
		int retVal = SUCCESS;
		ResultSet rs = null;
		Statement st = null;
		if(resourceCreatorID < 0)
			return -1;

		String query = "SELECT * FROM resources WHERE creatorid = "+resourceCreatorID;
		try {
			st = conn.createStatement();
			rs = st.executeQuery(query);
			while (rs.next()){
				HashMap<String, ByteIterator> values = new HashMap<String, ByteIterator>();
				//get the number of columns and their names
				ResultSetMetaData md = rs.getMetaData();
				int col = md.getColumnCount();
				for (int i = 1; i <= col; i++){
					String col_name = md.getColumnName(i);
					String value = rs.getString(col_name);
					if(col_name.equalsIgnoreCase("rid"))
						col_name = "rid";
					values.put(col_name, new ObjectByteIterator(value.getBytes()));
				}
				result.add(values);
			}
		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}finally{
			try {
				if (rs != null)
					rs.close();
				if(st != null)
					st.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}

		return retVal;		
	}


	@Override
	public int viewCommentOnResource(int requesterID, int profileOwnerID,
			int resourceID, Vector<HashMap<String, ByteIterator>> result) {
		if (verbose) System.out.print("Comments of "+resourceID+" ...");
		int retVal = SUCCESS;
		ResultSet rs = null;
		if(profileOwnerID < 0 || requesterID < 0 || resourceID < 0)
			return -1;


		String key = "ResCmts:"+resourceID;
		String query="";
		//MemcachedClient cacheclient=null;
		// Check Cache first
		try {
			//cacheclient = new MemcachedClient(AddrUtil.getAddresses(cache_hostname + ":" + cache_port));
			byte[] payload = common.CacheUtilities.CacheGet(this.cacheclient, key, this.compressPayload);
			if (payload != null){
				if (!CacheUtilities.unMarshallVectorOfHashMaps(payload,result))
					System.out.println("Error in ApplicationCacheClient: Failed to unMarshallVectorOfHashMaps");
				//				for (int i = 0; i < result.size(); i++){
				//					HashMap<String, ByteIterator> myhashmap = result.elementAt(i);
				//					if (myhashmap.get("RID") != null)
				//						if (Integer.parseInt(myhashmap.get("RID").toString()) != resourceID)
				//							System.out.println("ERROR:  Expecting results for "+resourceID+" and got results for resource "+myhashmap.get("RID").toString());
				//						else i=result.size();
				//				}
				if (verbose) System.out.println("... Cache Hit!");
				//cacheclient.shutdown();
				return retVal;
			} else if (verbose) System.out.print("... Query DB!");
		} catch (Exception e1) {
			e1.printStackTrace(System.out);
			System.out.println("Error in ApplicationCacheClient, viewPendingRequests failed to serialize a vector of hashmaps.");
			retVal = -2;
		}

		try {	
			query = "SELECT * FROM manipulation WHERE rid = ?";	
			if((preparedStatement = newCachedStatements.get(GETRESCMT_STMT)) == null)
				preparedStatement = createAndCacheStatement(GETRESCMT_STMT, query);
		
			//preparedStatement = conn.prepareStatement(query);
			preparedStatement.setInt(1, resourceID);
			//cacheclient.addQuery("SELECT * FROM manipulation WHERE rid = "+resourceID);
			rs = preparedStatement.executeQuery();
			while (rs.next()){
				HashMap<String, ByteIterator> values = new HashMap<String, ByteIterator>();
				//get the number of columns and their names
				ResultSetMetaData md = rs.getMetaData();
				int col = md.getColumnCount();
				for (int i = 1; i <= col; i++){
					String col_name = md.getColumnName(i);
					String value = rs.getString(col_name);
					values.put(col_name, new ObjectByteIterator(value.getBytes()));
				}
				result.add(values);
			}
		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		}finally{
			try {
				if (rs != null)
					rs.close();
				if(preparedStatement != null)
					preparedStatement.clearParameters();
					//preparedStatement.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}

		byte[] payload = CacheUtilities.SerializeVectorOfHashMaps(result);
		try {			
			if(!common.CacheUtilities.CacheSet(this.cacheclient, key, payload, this.useTTL, this.TTLvalue, this.compressPayload))
			{
				throw new Exception("Error calling WhalinMemcached set");
			}
			//cacheclient.shutdown();
		} catch (Exception e1) {
			System.out.println("Error in ApplicationCacheClient, getListOfFriends failed to insert the key-value pair in the cache.");
			e1.printStackTrace(System.out);
			retVal = -2;
		}

		return retVal;		
	}

	@Override
	public int postCommentOnResource(int commentCreatorID, int profileOwnerID,
			int resourceID, HashMap<String,ByteIterator> commentValues) {
		int retVal = SUCCESS;

		if(profileOwnerID < 0 || commentCreatorID < 0 || resourceID < 0)
			return -1;

		String query = "INSERT INTO manipulation(mid, creatorid, rid, modifierid, timestamp, type, content) VALUES (?, ?, ?, ?, ?, ?)";
		try {
			if((preparedStatement = newCachedStatements.get(POSTCMT_STMT)) == null)
				preparedStatement = createAndCacheStatement(POSTCMT_STMT, query);
		
			//preparedStatement = conn.prepareStatement(query);
			preparedStatement.setInt(1, Integer.parseInt(commentValues.get("mid").toString()));
			preparedStatement.setInt(2, profileOwnerID);
			preparedStatement.setInt(3, resourceID);
			preparedStatement.setInt(4,commentCreatorID);
			preparedStatement.setString(5,commentValues.get("timestamp").toString());
			preparedStatement.setString(6,commentValues.get("type").toString());
			preparedStatement.setString(7,commentValues.get("content").toString());
		
			preparedStatement.executeUpdate();

			if (!useTTL)
			{
				String key = "ResCmts:"+resourceID;
				//MemcachedClient cacheclient = new MemcachedClient(AddrUtil.getAddresses(cache_hostname + ":" + cache_port));
				if(!common.CacheUtilities.CacheDelete(this.cacheclient, key))
				{
					throw new Exception("Error calling WhalinMemcached delete");
				}
				//cacheclient.shutdown();
			}


		}catch(SQLException sx){
			retVal = -2;
			//sx.printStackTrace(System.out);
		} catch (Exception e1) {
			e1.printStackTrace(System.out);
			System.exit(-1);
		}finally{
			try {
				if(preparedStatement != null)
					preparedStatement.clearParameters();
					//preparedStatement.close();
			} catch (SQLException e) {
				retVal = -2;
				e.printStackTrace(System.out);
			}
		}

		return retVal;		
	}


	@Override
	//init phase query not cached
	public HashMap<String, String> getInitialStats() {
		HashMap<String, String> stats = new HashMap<String, String>();
		Statement st = null;
		ResultSet rs = null;
		String query = "";
		try {
			st = conn.createStatement();
			//get user count
			query = "SELECT count(*) from users";
			rs = st.executeQuery(query);
			if(rs.next()){
				stats.put("usercount",rs.getString(1));
			}else
				stats.put("usercount","0"); //sth is wrong - schema is missing
			if(rs != null ) rs.close();

			//get resources per user
			query = "SELECT avg(rescount) from users";
			rs = st.executeQuery(query);
			if(rs.next()){
				stats.put("resourcesperuser",rs.getString(1));
			}else{
				stats.put("resourcesperuser","0");
			}
			if(rs != null) rs.close();	
			//get number of friends per user
			query = "select avg(confcount) from users" ;
			rs = st.executeQuery(query);
			if(rs.next()){
				stats.put("avgfriendsperuser",rs.getString(1));
			}else
				stats.put("avgfriendsperuser","0");
			if(rs != null) rs.close();
			query = "select avg(pendcount) from users" ;
			rs = st.executeQuery(query);
			if(rs.next()){
				stats.put("avgpendingperuser",rs.getString(1));
			}else
				stats.put("avgpendingperuser","0");
			

		}catch(SQLException sx){
			//sx.printStackTrace(System.out);
		}finally{
			try {
				if(rs != null)
					rs.close();
				if(st != null)
					st.close();
			} catch (SQLException e) {
				e.printStackTrace(System.out);
			}

		}
		return stats;
	}

	//init phase query not cached
	public int queryPendingFriendshipIds(int inviteeid, Vector<Integer> pendingIds){
		Statement st = null;
		ResultSet rs = null;
		String query = "";
		int retVal	 = SUCCESS;
		
		if(inviteeid < 0)
			retVal = -1;

		String key = "PendingFriendship:"+inviteeid;
//		//MemcachedClient cacheclient=null;
//		// Check Cache first
//		try {
//			//cacheclient = new MemcachedClient(AddrUtil.getAddresses(cache_hostname + ":" + cache_port));
//			byte[] payload = common.CacheUtilities.CacheGet(this.cacheclient, key, this.compressPayload);
//			if (payload != null){
//				if (!unMarshallVectorOfInts(payload, pendingIds))
//					System.out.println("Error in ApplicationCacheClient: Failed to unMarshallVector");
//				
//				if (verbose) System.out.println("... Cache Hit!");
//				//cacheclient.shutdown();
//				return retVal;
//			} else if (verbose) System.out.print("... Query DB!");
//		} catch (Exception e1) {
//			e1.printStackTrace(System.out);
//			System.out.println("Error in ApplicationCacheClient, viewPendingRequests failed to serialize a vector of hashmaps.");
//			retVal = -2;
//		}
		
		try {
			st = conn.createStatement();
			query = "SELECT inviterid from friendship where inviteeid='"+inviteeid+"' and status='1'";
			//cacheclient.addQuery(query);
			rs = st.executeQuery(query);
			while(rs.next()){
				pendingIds.add(rs.getInt(1));
			}	
		}catch(SQLException sx){
			//sx.printStackTrace(System.out);
			retVal = -2;
		}finally{
			try {
				if(rs != null)
					rs.close();
				if(st != null)
					st.close();
			} catch (SQLException e) {
				e.printStackTrace(System.out);
				retVal = -2;
			}
		}
//		
//		byte[] payload = SerializeVectorOfInts(pendingIds);
//		try {
//			OperationFuture<Boolean> setResult = CacheSet(key, 0, payload);
//			setResult.get();
//			//cacheclient.shutdown();
//		} catch (Exception e1) {
//			System.out.println("Error in ApplicationCacheClient, getListOfFriends failed to insert the key-value pair in the cache.");
//			e1.printStackTrace(System.out);
//			retVal = -2;
//		}

		return retVal;
	}

	//init phase query not cached
	public int queryConfirmedFriendshipIds(int profileId, Vector<Integer> confirmedIds){
		Statement st = null;
		ResultSet rs = null;
		String query = "";		
		int retVal	 = SUCCESS;
		if(profileId < 0)
			retVal = -1;
		
		try {
			st = conn.createStatement();
			query = "SELECT inviterid, inviteeid from friendship where (inviteeid="+profileId+" OR inviterid="+profileId+") and status='2'";
			//cacheclient.addQuery(query);
			rs = st.executeQuery(query);
			while(rs.next()){
				if(rs.getInt(1) != profileId)
					confirmedIds.add(rs.getInt(1));
				else
					confirmedIds.add(rs.getInt(2));
			}	
		}catch(SQLException sx){
			//sx.printStackTrace(System.out);
			retVal = -2;
		}finally{
			try {
				if(rs != null)
					rs.close();
				if(st != null)
					st.close();
			} catch (SQLException e) {
				e.printStackTrace(System.out);
				retVal = -2;
			}
		}

		return retVal;
	}



	public static void main(String[] args) {
		System.out.println("Hello World");
		
	}


	@Override
	public void createSchema(Properties props){

		Statement stmt = null;

		try {
			stmt = conn.createStatement();

			dropSequence(stmt, "MIDINC");
			dropSequence(stmt, "RIDINC");
			dropSequence(stmt, "USERIDINC");
			dropSequence(stmt, "USERIDS");

			dropTable(stmt, "friendship");
			dropTable(stmt, "manipulation");
			dropTable(stmt, "resources");
			dropTable(stmt, "users");

			stmt.executeUpdate("CREATE SEQUENCE  MIDINC  MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 201 CACHE 20 NOORDER  NOCYCLE");
			stmt.executeUpdate("CREATE SEQUENCE  RIDINC  MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 21 CACHE 20 NOORDER  NOCYCLE ");
			stmt.executeUpdate("CREATE SEQUENCE  USERIDINC  MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 21 CACHE 20 NOORDER  NOCYCLE ");
			stmt.executeUpdate("CREATE SEQUENCE  USERIDS  MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER  NOCYCLE");

			stmt.executeUpdate("CREATE TABLE FRIENDSHIP"
					+ "(INVITERID NUMBER, INVITEEID NUMBER,"
					+ "STATUS NUMBER DEFAULT 1" + ") NOLOGGING");

			stmt.executeUpdate("CREATE TABLE MANIPULATION"
					+ "(	MID NUMBER," + "CREATORID NUMBER, RID NUMBER,"
					+ "MODIFIERID NUMBER, TIMESTAMP VARCHAR2(200),"
					+ "TYPE VARCHAR2(200), CONTENT VARCHAR2(200)"
					+ ") NOLOGGING");

			stmt.executeUpdate("CREATE TABLE RESOURCES"
					+ "(	RID NUMBER,CREATORID NUMBER,"
					+ "WALLUSERID NUMBER, TYPE VARCHAR2(200),"
					+ "BODY VARCHAR2(200), DOC VARCHAR2(200)"
					+ ") NOLOGGING");

			if (Boolean.parseBoolean(props.getProperty(Client.INSERT_IMAGE_PROPERTY,
					Client.INSERT_IMAGE_PROPERTY_DEFAULT)) && props.getProperty(FS_PATH, "").equals("")) {
				stmt.executeUpdate("CREATE TABLE USERS"
						+ "(USERID NUMBER, USERNAME VARCHAR2(200), "
						+ "PW VARCHAR2(200), FNAME VARCHAR2(200), "
						+ "LNAME VARCHAR2(200), GENDER VARCHAR2(200),"
						+ "DOB VARCHAR2(200),JDATE VARCHAR2(200), "
						+ "LDATE VARCHAR2(200), ADDRESS VARCHAR2(200),"
						+ "EMAIL VARCHAR2(200), TEL VARCHAR2(200), PIC BLOB, TPIC BLOB,"
						+ "PENDCOUNT NUMBER, CONFCOUNT NUMBER, RESCOUNT NUMBER"
						+ ") NOLOGGING");
			} else {
				stmt.executeUpdate("CREATE TABLE USERS"
						+ "(USERID NUMBER, USERNAME VARCHAR2(200), "
						+ "PW VARCHAR2(200), FNAME VARCHAR2(200), "
						+ "LNAME VARCHAR2(200), GENDER VARCHAR2(200),"
						+ "DOB VARCHAR2(200),JDATE VARCHAR2(200), "
						+ "LDATE VARCHAR2(200), ADDRESS VARCHAR2(200),"
						+ "EMAIL VARCHAR2(200), TEL VARCHAR2(200),"
						+ "PENDCOUNT NUMBER, CONFCOUNT NUMBER, RESCOUNT NUMBER"
						+ ") NOLOGGING");

			}

			stmt.executeUpdate("ALTER TABLE USERS MODIFY (USERID NOT NULL ENABLE)");
			stmt.executeUpdate("ALTER TABLE USERS ADD CONSTRAINT USERS_PK PRIMARY KEY (USERID) ENABLE");
			stmt.executeUpdate("ALTER TABLE MANIPULATION ADD CONSTRAINT MANIPULATION_PK PRIMARY KEY (MID) ENABLE");
			stmt.executeUpdate("ALTER TABLE MANIPULATION MODIFY (MID NOT NULL ENABLE)");
			stmt.executeUpdate("ALTER TABLE MANIPULATION MODIFY (CREATORID NOT NULL ENABLE)");
			stmt.executeUpdate("ALTER TABLE MANIPULATION MODIFY (RID NOT NULL ENABLE)");
			stmt.executeUpdate("ALTER TABLE MANIPULATION MODIFY (MODIFIERID NOT NULL ENABLE)");
			stmt.executeUpdate("ALTER TABLE FRIENDSHIP ADD CONSTRAINT FRIENDSHIP_PK PRIMARY KEY (INVITERID, INVITEEID) ENABLE");
			stmt.executeUpdate("ALTER TABLE FRIENDSHIP MODIFY (INVITERID NOT NULL ENABLE)");
			stmt.executeUpdate("ALTER TABLE FRIENDSHIP MODIFY (INVITEEID NOT NULL ENABLE)");
			stmt.executeUpdate("ALTER TABLE RESOURCES ADD CONSTRAINT RESOURCES_PK PRIMARY KEY (RID) ENABLE");
			stmt.executeUpdate("ALTER TABLE RESOURCES MODIFY (RID NOT NULL ENABLE)");
			stmt.executeUpdate("ALTER TABLE RESOURCES MODIFY (CREATORID NOT NULL ENABLE)");
			stmt.executeUpdate("ALTER TABLE RESOURCES MODIFY (WALLUSERID NOT NULL ENABLE)");
			stmt.executeUpdate("ALTER TABLE FRIENDSHIP ADD CONSTRAINT FRIENDSHIP_USERS_FK1 FOREIGN KEY (INVITERID)"
					+ "REFERENCES USERS (USERID) ON DELETE CASCADE ENABLE");
			stmt.executeUpdate("ALTER TABLE FRIENDSHIP ADD CONSTRAINT FRIENDSHIP_USERS_FK2 FOREIGN KEY (INVITEEID)"
					+ "REFERENCES USERS (USERID) ON DELETE CASCADE ENABLE");
			stmt.executeUpdate("ALTER TABLE MANIPULATION ADD CONSTRAINT MANIPULATION_RESOURCES_FK1 FOREIGN KEY (RID)"
					+ "REFERENCES RESOURCES (RID) ON DELETE CASCADE ENABLE");
			stmt.executeUpdate("ALTER TABLE MANIPULATION ADD CONSTRAINT MANIPULATION_USERS_FK1 FOREIGN KEY (CREATORID)"
					+ "REFERENCES USERS (USERID) ON DELETE CASCADE ENABLE");
			stmt.executeUpdate("ALTER TABLE MANIPULATION ADD CONSTRAINT MANIPULATION_USERS_FK2 FOREIGN KEY (MODIFIERID)"
					+ "REFERENCES USERS (USERID) ON DELETE CASCADE ENABLE");
			stmt.executeUpdate("ALTER TABLE RESOURCES ADD CONSTRAINT RESOURCES_USERS_FK1 FOREIGN KEY (CREATORID)"
					+ "REFERENCES USERS (USERID) ON DELETE CASCADE ENABLE");
			stmt.executeUpdate("ALTER TABLE RESOURCES ADD CONSTRAINT RESOURCES_USERS_FK2 FOREIGN KEY (WALLUSERID)"
					+ "REFERENCES USERS (USERID) ON DELETE CASCADE ENABLE");
			stmt.executeUpdate("CREATE OR REPLACE TRIGGER MINC before insert on manipulation "
					+ "for each row "
					+ "WHEN (new.mid is null) begin "
					+ "select midInc.nextval into :new.mid from dual;"
					+ "end;");
			stmt.executeUpdate("ALTER TRIGGER MINC ENABLE");

			stmt.executeUpdate("CREATE OR REPLACE TRIGGER RINC before insert on resources "
					+ "for each row "
					+ "WHEN (new.rid is null) begin "
					+ "select ridInc.nextval into :new.rid from dual;"
					+ "end;");
			stmt.executeUpdate("ALTER TRIGGER RINC ENABLE");

			stmt.executeUpdate("CREATE OR REPLACE TRIGGER UINC before insert on users "
					+ "for each row "
					+ "WHEN (new.userid is null) begin "
					+ "select useridInc.nextval into :new.userid from dual;"
					+ "end;");
			stmt.executeUpdate("ALTER TRIGGER UINC ENABLE");
			
			
			//drop stored procedure
			dropStoredProcedure(stmt, "INVITEFRIEND");
			stmt.execute("CREATE OR REPLACE PROCEDURE INVITEFRIEND " +
							"("+ 
						         " P_INVITERID   IN FRIENDSHIP.INVITERID%TYPE    , "+
						         " p_INVITEEID     IN FRIENDSHIP.INVITEEID%TYPE  "+
						     ")" +
						"AS "+ 
						"BEGIN "+ 
						      "INSERT INTO FRIENDSHIP "+
						             "("+ 
						               "INVITERID     , "+
						               "INVITEEID     , "+
						               "STATUS"+
						              ")"+ 
						     " VALUES "+
						             "("+ 
						               "P_INVITERID     , "+
						               "P_INVITEEID     ,"+
						               "1"+
						             ");"+ 
						      "UPDATE USERS SET "+
								  		"PENDCOUNT=PENDCOUNT+1 WHERE USERID = P_INVITEEID;"+
						      "COMMIT;"+
						"END INVITEFRIEND;"
					);
		
			//drop stored procedure
			dropStoredProcedure(stmt, "ACCEPTFRIEND");
			stmt.execute("CREATE OR REPLACE PROCEDURE ACCEPTFRIEND " +
							"("+ 
						         " P_INVITERID   IN FRIENDSHIP.INVITERID%TYPE    , "+
						         " p_INVITEEID     IN FRIENDSHIP.INVITEEID%TYPE  "+
						     ")" +
						"AS "+ 
						"BEGIN "+ 
							  "UPDATE FRIENDSHIP SET STATUS=2 "+
							  		"WHERE INVITERID = P_INVITERID AND INVITEEID = P_INVITEEID ;"+
							  "UPDATE USERS SET "+
							  		"CONFCOUNT=CONFCOUNT+1 WHERE USERID = P_INVITEEID OR USERID=P_INVITERID;"+
							  "UPDATE USERS SET "+
								  		"PENDCOUNT=PENDCOUNT-1 WHERE USERID = P_INVITEEID;"+
						      "COMMIT;"+
						"END ACCEPTFRIEND;"
					);
			
			//drop stored procedure
			dropStoredProcedure(stmt, "REJECTFRIEND");
			stmt.execute("CREATE OR REPLACE PROCEDURE REJECTFRIEND " +
							"("+ 
						         " P_INVITERID   IN FRIENDSHIP.INVITERID%TYPE    , "+
						         " p_INVITEEID     IN FRIENDSHIP.INVITEEID%TYPE  "+
						     ")" +
						"AS "+ 
						"BEGIN "+ 
							  "DELETE FROM FRIENDSHIP "+
							  		"WHERE INVITERID = P_INVITERID AND INVITEEID = P_INVITEEID AND STATUS=1 ;"+
							  "UPDATE USERS SET "+
							  		"PENDCOUNT=PENDCOUNT-1 WHERE USERID = P_INVITEEID;"+
						      "COMMIT;"+
						"END REJECTFRIEND;"
					);
			
			//drop stored procedure
			dropStoredProcedure(stmt, "THAWFRIEND");
			stmt.execute("CREATE OR REPLACE PROCEDURE THAWFRIEND " +
							"("+ 
						         " P_INVITERID   IN FRIENDSHIP.INVITERID%TYPE    , "+
						         " p_INVITEEID     IN FRIENDSHIP.INVITEEID%TYPE  "+
						     ")" +
						"AS "+ 
						"BEGIN "+ 
							  "DELETE FROM FRIENDSHIP "+
							  		"WHERE (INVITERID = P_INVITERID AND INVITEEID = P_INVITEEID) OR (INVITERID = P_INVITEEID AND INVITEEID = P_INVITERID) AND STATUS=2;"+
							  "UPDATE USERS SET "+
							  		"CONFCOUNT=CONFCOUNT-1 WHERE USERID = P_INVITEEID OR USERID=P_INVITERID;"+ 
						      "COMMIT;"+
						"END THAWFRIEND;"
					);
			
			dropStoredProcedure(stmt, "INSERTFRIEND");
			stmt.execute("CREATE OR REPLACE PROCEDURE INSERTFRIEND " +
							"("+ 
						         " P_INVITERID   IN FRIENDSHIP.INVITERID%TYPE    , "+
						         " p_INVITEEID     IN FRIENDSHIP.INVITEEID%TYPE  "+
						     ")" +
						"AS "+ 
						"BEGIN "+ 
						 "INSERT INTO FRIENDSHIP "+
			             "("+ 
			               "INVITERID     , "+
			               "INVITEEID     , "+
			               "STATUS"+
			              ")"+ 
			     " VALUES "+
			             "("+ 
			               "P_INVITERID     , "+
			               "P_INVITEEID     ,"+
			               "2"+
			             ");"+ 
			      "UPDATE USERS SET "+
					  		"CONFCOUNT=CONFCOUNT+1 WHERE USERID = P_INVITEEID OR USERID=P_INVITERID;"+
						      "COMMIT;"+
						"END INSERTFRIEND;"
					);


			dropIndex(stmt, "RESOURCE_CREATORID");
			dropIndex(stmt, "RESOURCES_WALLUSERID");
			dropIndex(stmt, "FRIENDSHIP_INVITEEID");
			dropIndex(stmt, "FRIENDSHIP_INVITERID");
			dropIndex(stmt, "MANIPULATION_RID");
			dropIndex(stmt, "MANIPULATION_CREATORID");
			
			/*
			//build index structures
			stmt.executeUpdate("CREATE INDEX RESOURCE_CREATORID ON RESOURCES (CREATORID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			stmt.executeUpdate("CREATE INDEX FRIENDSHIP_INVITEEID ON FRIENDSHIP (INVITEEID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			stmt.executeUpdate("CREATE INDEX MANIPULATION_RID ON MANIPULATION (RID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			stmt.executeUpdate("CREATE INDEX RESOURCES_WALLUSERID ON RESOURCES (WALLUSERID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			stmt.executeUpdate("CREATE INDEX FRIENDSHIP_INVITERID ON FRIENDSHIP (INVITERID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			stmt.executeUpdate("CREATE INDEX MANIPULATION_CREATORID ON MANIPULATION (CREATORID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			*/
		} catch (SQLException e) {
			e.printStackTrace(System.out);
		} finally {
			if (stmt != null)
				try {
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace(System.out);
				}
		}

	}
     @Override
	public void buildIndexes(Properties props){
		Statement stmt  = null;
		try {
			stmt = conn.createStatement();
			long startIdx = System.currentTimeMillis();

			dropIndex(stmt, "RESOURCE_CREATORID");
			dropIndex(stmt, "RESOURCES_WALLUSERID");
			dropIndex(stmt, "FRIENDSHIP_INVITEEID");
			dropIndex(stmt, "FRIENDSHIP_INVITERID");
			dropIndex(stmt, "MANIPULATION_RID");
			dropIndex(stmt, "MANIPULATION_CREATORID");
			stmt.executeUpdate("CREATE INDEX RESOURCE_CREATORID ON RESOURCES (CREATORID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			stmt.executeUpdate("CREATE INDEX FRIENDSHIP_INVITEEID ON FRIENDSHIP (INVITEEID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			stmt.executeUpdate("CREATE INDEX MANIPULATION_RID ON MANIPULATION (RID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			stmt.executeUpdate("CREATE INDEX RESOURCES_WALLUSERID ON RESOURCES (WALLUSERID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			stmt.executeUpdate("CREATE INDEX FRIENDSHIP_INVITERID ON FRIENDSHIP (INVITERID)"
					+ "COMPUTE STATISTICS NOLOGGING");

			stmt.executeUpdate("CREATE INDEX MANIPULATION_CREATORID ON MANIPULATION (CREATORID)"
					+ "COMPUTE STATISTICS NOLOGGING");
			
			stmt.executeUpdate("analyze table users compute statistics");
			stmt.executeUpdate("analyze table resources compute statistics");
			stmt.executeUpdate("analyze table friendship compute statistics");
			stmt.executeUpdate("analyze table manipulation compute statistics");
			long endIdx = System.currentTimeMillis();
			System.out
			.println("Time to build database index structures(ms):"
					+ (endIdx - startIdx));
		} catch (Exception e) {
			//e.printStackTrace(System.out);
			System.out.println("Error building index. (Possibly due to concurrent calls to buildIndex)");
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				e.printStackTrace(System.out);
			}
		}
	}

	public static void dropSequence(Statement st, String seqName) {
		try {
			st.executeUpdate("drop sequence " + seqName);
		} catch (SQLException e) {
		}
	}

	public static void dropIndex(Statement st, String idxName) {
		try {
			st.executeUpdate("drop index " + idxName);
		} catch (SQLException e) {
		}
	}

	public static void dropTable(Statement st, String tableName) {
		try {
			st.executeUpdate("drop table " + tableName);
		} catch (SQLException e) {
		}
	}
	
	public static void dropStoredProcedure(Statement st, String procName) {
		try {
			st.executeUpdate("drop procedure " + procName);
		} catch (SQLException e) {
		}
	}

	@Override
	public int delCommentOnResource(int resourceCreatorID, int resourceID,
			int manipulationID) {
		// TODO Auto-generated method stub
		return 0;
	}



}
