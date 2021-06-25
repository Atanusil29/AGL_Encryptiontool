package au.com.astral.exstream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Robin De Pooter 
 * 		   OTCSConnect V1 /06/2021 Class interface to connect
 *         with Opentext Content Server and perform actions such as retrieving
 *         web reports or uploading and downloading documents
 *
 */
public class OTCSConnect {

	private final static Logger LOG = LoggerFactory.getLogger(OTCSConnect.class);

	private String contentServerURL = null;
	private String authTicket = null;

	private String csUsername = null;
	private String csPassword = null;

	private String webReportResult = null;

	private String documentID = null;

	private String categoryWM = null;
	private String categoryWMkey = null;

	private String categoryID = null;
	private ArrayList<String> categoryKeys = new ArrayList<String>();

	private String categoryDoc = null;
	private ArrayList<String> categoryDocKeys = new ArrayList<String>();
	private ArrayList<String> categoryDocValues = new ArrayList<String>();

	private SecretKeySpec secretKey;
	private byte[] key;

	public static void main(String[] args) {

	}

	/**
	 * Read configuration file from server
	 * 
	 * @param ConfigFilePath Path to the config file
	 * @return "0" on success, error string on failure
	 */
	private String readConfigFile(String ConfigFolderPath) {

		JSONParser parser = new JSONParser();

		try (Reader reader = new FileReader(ConfigFolderPath + "\\config.json")) {

			JSONObject jsonObject = (JSONObject) parser.parse(reader);

			// Set credentials in class variable
			contentServerURL = (String) jsonObject.get("Content Server URL");
			csUsername = (String) jsonObject.get("Username");
			csPassword = (String) jsonObject.get("Password");

			categoryID = (String) jsonObject.get("Exstream Category");
			categoryKeys.add(categoryID + (String) jsonObject.get("Exstream Category Dispatch Tracker ID"));
			categoryKeys.add(categoryID + (String) jsonObject.get("Exstream Category Dispatch Date"));

			categoryWM = (String) jsonObject.get("Wholesale Markets Category");
			categoryWMkey = categoryWM + (String) jsonObject.get("Wholesale Markets Category Document type");

			categoryDoc = (String) jsonObject.get("Document Category");

			categoryDocKeys.add(categoryDoc + (String) jsonObject.get("Document Category Site"));
			categoryDocKeys.add(categoryDoc + (String) jsonObject.get("Document Category Function"));
			categoryDocKeys.add(categoryDoc + (String) jsonObject.get("Document Category Sub Function"));
			categoryDocKeys.add(categoryDoc + (String) jsonObject.get("Document Category Class"));

			categoryDocValues.add((String) jsonObject.get("Document Category Site Value"));
			categoryDocValues.add((String) jsonObject.get("Document Category Function Value"));
			categoryDocValues.add((String) jsonObject.get("Document Category Sub Function Value"));
			categoryDocValues.add((String) jsonObject.get("Document Category Class Value"));

		} catch (IOException | ParseException e) {
			String msg = "readConfigFile : Error : Error while fetching credentials from configuration file";
			LOG.error(msg, e);
			return msg;
		}

		LOG.info("readConfigFile : Configuration file succesfully read");
		return "0";
	}

	/**
	 * Gets Content Server authentication ticket using credentials set in
	 * setCredentials
	 * 
	 * @return "0" on success, error string on failure
	 */
	private String getOTCSTicket() {

		String msg;

		// Create an http instance and set the url for authentication
		HttpClient httpclient = HttpClients.createDefault();
		HttpPost httppost = new HttpPost(contentServerURL + "/api/v1/auth");

		LOG.info("getOTCSTicket : Post setup : added url : " + contentServerURL + "/api/v1/auth");

		// Set username and password in body of POST call
		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		params.add(new BasicNameValuePair("username", csUsername));
		params.add(new BasicNameValuePair("password", csPassword));

		try {
			httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

			// Check if username is initialized
			if (csUsername.isEmpty()) {
				msg = "getOTCSTicket : Post setup : Content Server username not set";
				LOG.error(msg);
				return msg;

			} else if (csPassword.isEmpty()) {
				msg = "getOTCSTicket : Post setup : Content Server username not set";
				LOG.error(msg);
				return msg;

			} else {
				msg = "getOTCSTicket : Post setup : added username : ";
				LOG.info(msg + csUsername);
				LOG.info(msg + toAsterisk(csPassword));
			}

			// Execute the POST call and get the response
			HttpResponse response;
			response = httpclient.execute(httppost);
			HttpEntity entity = (HttpEntity) response.getEntity();

			if (entity != null) {
				// Get response and convert it to string
				InputStream instream = ((org.apache.http.HttpEntity) entity).getContent();
				String theString = IOUtils.toString(instream, "UTF-8");

				// Make a JSON parser class object
				JSONParser jsonParser = new JSONParser();
				JSONObject jsonObject;

				// Store web report value in JSON parser object
				jsonObject = (JSONObject) jsonParser.parse(theString);

				// Check for error value in Content Cerver response
				if (jsonObject.containsKey("error")) {
					msg = "getOTCSTicket : Error : Content Server authentication : ";
					LOG.error(msg + jsonObject.get("error"));
					return msg + jsonObject.get("error");
				}

				// Return ticket from Content Server response
				if (jsonObject.containsKey("ticket")) {
					authTicket = jsonObject.get("ticket").toString();
					LOG.info("getOTCSTicket : OTCS Ticket received: " + toAsterisk((String) jsonObject.get("ticket")));
					return "0";

				} else {
					msg = "getOTCSTicket : OTCS Ticket was not found";
					LOG.error(msg);
					return msg;
				}
			}
		} catch (IOException | ParseException e) {
			msg = "getOTCSTicket : Error :  Content Server connection failed during authentication";
			LOG.error(msg, e);
			return msg;
		}

		return "getOTCSTicket : Error : Could not connect, invalid credentials or response";
	}

	/**
	 * Gets Content Server authentication ticket using credentials set in
	 * setCredentials. The password should be encrypted and will be decrypted during
	 * the function
	 * 
	 * @return "0" on success, error string on failure
	 */
	private String getOTCSTicketEncrypted() {

		String msg;

		// Create an http instance and set the url for authentication
		HttpClient httpclient = HttpClients.createDefault();
		HttpPost httppost = new HttpPost(contentServerURL + "/api/v1/auth");

		LOG.info("getOTCSTicket : Post setup : added url : " + contentServerURL + "/api/v1/auth");

		// Set username and decryptedpassword in body of POST call
		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		params.add(new BasicNameValuePair("username", csUsername));
		params.add(new BasicNameValuePair("password", decrypt(csPassword)));

		try {
			httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

			// Check if username is initialized
			if (csUsername.isEmpty()) {
				msg = "getOTCSTicket : Post setup : Content Server username not set";
				LOG.error(msg);
				return msg;

			} else if (csPassword.isEmpty()) {
				msg = "getOTCSTicket : Post setup : Content Server username not set";
				LOG.error(msg);
				return msg;

			} else {
				msg = "getOTCSTicket : Post setup : added username : ";
				LOG.info(msg + csUsername);
				LOG.info(msg + toAsterisk(csPassword));
			}

			// Execute the POST call and get the response
			HttpResponse response;
			response = httpclient.execute(httppost);
			HttpEntity entity = (HttpEntity) response.getEntity();

			if (entity != null) {
				// Get response and convert it to string
				InputStream instream = ((org.apache.http.HttpEntity) entity).getContent();
				String theString = IOUtils.toString(instream, "UTF-8");

				// Make a JSON parser class object
				JSONParser jsonParser = new JSONParser();
				JSONObject jsonObject;

				// Store web report value in JSON parser object
				jsonObject = (JSONObject) jsonParser.parse(theString);

				// Check for error value in Content Cerver response
				if (jsonObject.containsKey("error")) {
					msg = "getOTCSTicket : Error : Content Server authentication : ";
					LOG.error(msg + jsonObject.get("error"));
					return msg + jsonObject.get("error");
				}

				// Return ticket from Content Server response
				if (jsonObject.containsKey("ticket")) {
					authTicket = jsonObject.get("ticket").toString();
					LOG.info("getOTCSTicket : OTCS Ticket received: " + toAsterisk((String) jsonObject.get("ticket")));
					return "0";

				} else {
					msg = "getOTCSTicket : OTCS Ticket was not found";
					LOG.error(msg);
					return msg;
				}
			}

		} catch (IOException | ParseException e) {
			msg = "getOTCSTicket : Error :  Content Server connection failed during authentication";
			LOG.error(msg, e);
			return msg;
		}

		return "getOTCSTicket : Error : Could not connect, invalid credentials or response";
	}

	/**
	 * POST Call to Content Server that Fetches a Webreport using parameters,
	 * parameter values and webreport name Stores the JSON result in class variable
	 * webReportResult.
	 * 
	 * @param Web Report Name
	 * @param Web Report Parameters [x]
	 * @param Web Report Parameter Values [x]
	 * @return "0" on success, error string on failure
	 */
	private String getWebReport(String WebReportName, String[] parameters, String[] parValues) {

		String msg;

		// Construct first part of the POST URL using webreportname
		String dl = contentServerURL + "/api/v1/webreports/" + WebReportName + "?format=webreport";

		// Check if amount of parameters is equal to the amount of parameter values
		if (parameters.length == parValues.length) {
			// Add parameters and values to the POST URL
			for (int i = 0; i < parameters.length; i++) {
				if (parameters[i].isBlank() == false && parValues[i].isBlank() == false) {
					try {
						parameters[i] = java.net.URLEncoder.encode(parameters[i], "UTF-8").replace("+", "%20");
						parValues[i] = java.net.URLEncoder.encode(parValues[i], "UTF-8").replace("+", "%20");

					} catch (UnsupportedEncodingException e) {
						msg = "getWebReport : Error during URL-encoding of parameters";
						LOG.error(msg);
						return msg;
					}

					dl = dl + "&" + parameters[i] + "=" + parValues[i];
				}
			}

		} else {
			msg = "getWebReport : Error : Web report amount of parameters and values is not equal";
			LOG.error(msg);
			return msg;
		}

		// Remove empty spaces from Url
		LOG.info("getWebReport : added url : " + dl);

		// Create a HTTP connection
		HttpClientBuilder builder = HttpClientBuilder.create();
		HttpClient httpClient = builder.build();

		// Set the GET call header parameters
		HttpGet httpGet = new HttpGet(dl);
		httpGet.setHeader("OTCSticket", authTicket);

		try {
			// Make the GET call and get the response
			HttpResponse response = httpClient.execute(httpGet);
			HttpEntity resEntity = response.getEntity();

			// Check if the response is not blank
			if (resEntity != null) {
				String retSrc = EntityUtils.toString(resEntity);
				int statusCode = response.getStatusLine().getStatusCode();

				// Check response status for succes
				if (statusCode == 200) {
					// Response successfully received, read it as an inputstream
					LOG.info("getWebReport : Content Server response received");

				} else {
					// Response status is unsuccessful, return error value
					msg = "getWebReport : Retrieving the web report from Content Server did not succeed: url response code: "
							+ statusCode + " || " + retSrc;
					LOG.error(msg);
					return msg;
				}

				// Store the content as a string in webReportResult
				if (retSrc.isEmpty()) {
					msg = "getWebReport : Web report could not be found";
					LOG.error(msg);
					return msg;

				} else {
					LOG.info("getWebReport : Web Report : " + retSrc);
				}

				webReportResult = retSrc;
				return "0";
			}

			msg = "categoryUpdate : Connection to Content Server failed : no response received";
			LOG.error(msg);
			return msg;

		} catch (Exception e) {
			msg = "getWebReport : error : Connection did not succeed ";
			LOG.error(msg, e);
			return msg;
		}
	}

	/**
	 * Returns JSON parameter value from web report obtained by getWebReport
	 * 
	 * @param parameterName
	 * @return parameter value, "-1" on failure
	 */
	private String parseWebReportParameter(String parameterName) {

		String msg;

		// Make a JSON parser class object
		JSONParser jsonParser = new JSONParser();
		JSONObject jsonObject;

		// Store web report value in JSON parser object
		try {
			jsonObject = (JSONObject) jsonParser.parse(webReportResult);

		} catch (ParseException e) {
			LOG.error("parseWebReportParameter : Error parsing web report parameter", e);
			return "-1";
		}

		// Check if parameter value exist, if so return it
		if (jsonObject.get(parameterName).toString().isEmpty()) {
			msg = "parseWebReportParameter : Error : Web report parameter could not be found";
			LOG.error(msg);
			return msg;

		} else {
			LOG.info("parseWebReportParameter : parameter returned : " + parameterName + " : "
					+ jsonObject.get(parameterName));
			return (String) jsonObject.get(parameterName);
		}
	}

	/**
	 * Downloads document from Content Server by nodeID and stores it on the given
	 * filepath location. Requires authentication ticket to be set.
	 * 
	 * @param filepath: filepath where document will be stored
	 * @param filename: name of file to be stored
	 * @param ext:      file extension (pdf/docx/...)
	 * @param nodeid:   Content Server document nodeid
	 * @return filepath of stored document, error string on failure
	 */
	private String getDocument(String filepath, String filename, String ext, String nodeid) {

		String msg;

		// Create the download URL
		String dl = contentServerURL + "/api/v1/nodes/" + nodeid + "/content";

		// Remove empty spaces from Url
		dl = dl.replace(" ", "+");
		LOG.info("getDocument : added url : " + dl);

		// Check if filepath directory is valid
		File file = new File(filepath);
		if (!file.isDirectory()) {
			msg = "getDocument: Error : filepath to store document is invalid {" + filepath + "}";
			LOG.error(msg);
			return msg;
		}

		// Construct the filepath string
		filepath = filepath + "/" + filename + "." + ext;

		try {
			// Create an http instance and set the url for authentication
			HttpClient httpclient = HttpClients.createDefault();
			HttpGet httpget = new HttpGet(dl);
			httpget.setHeader("OTCSticket", authTicket);

			// Execute the GET call and get the response
			HttpResponse response = httpclient.execute(httpget);

			HttpEntity entity = (HttpEntity) response.getEntity();
			LOG.info("getDocument : Setting up connection with Content Server");

			// Check if the response is not blank
			if (entity != null) {
				LOG.info("getDocument : Content Server response received");

				// Store the document in an inputstream write it to the filepath using
				// FileOutputStream
				BufferedInputStream in = new BufferedInputStream(entity.getContent());
				FileOutputStream fileOutputStream = new FileOutputStream(filepath);

				byte dataBuffer[] = new byte[1024];
				int bytesRead;

				while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
					fileOutputStream.write(dataBuffer, 0, bytesRead);
				}

				fileOutputStream.close();

			} else {
				msg = "getDocument : Retrieving the document from Content Server did not succeed by using NodeID ";
				LOG.error(msg + nodeid);
				return msg + nodeid;
			}

			LOG.info("getDocument : Document stored at " + filepath);
			return filepath;

		} catch (Exception e) {
			msg = "getDocument : Connection to Content Server did not succeed";
			LOG.error(msg, e);
			return msg;
		}
	}

	/**
	 * Writes a document on the local system to OTCS
	 * 
	 * @param filepath: pointing to folder where document is
	 * @param filename: name of file to be stored
	 * @param ext:      File extension (pdf/docx/...)
	 * @param parentid: Content Server parentID
	 * @return Returns nodeID of document in CS
	 */
	private String writeDocument(String filepath, String filename, String ext, String parentid) {

		String[] categoryValues = new String[] { "#trackerID#", "#Timestamp#" };

		String msg;
		// Make a JSON parser class object
		JSONParser jsonParser = new JSONParser();
		JSONObject jsonObj;

		// Create the download URL
		String dl = contentServerURL + "/api/v1/nodes";

		// Remove empty spaces from Url
		dl = dl.replace(" ", "+");
		LOG.info("writeDocument : Content Server POST URI: " + dl);

		String jsonBody = "{\"name\": \"" + filename + "\", \"parent_id\": \"" + parentid
				+ "\", \"type\":\"144\",\"roles\":{\"categories\":{ \"" + categoryID + "\": { \"" + categoryKeys.get(0)
				+ "\":\"" + categoryValues[0] + "\", \"" + categoryKeys.get(1) + "\":\"" + categoryValues[1]
				+ "\"} } } }";

		LOG.info("writeDocument : JSON BODY : " + jsonBody);

		filepath = filepath + "/" + filename + "." + ext;
		LOG.info("writeDocument : Loading file at filepath : " + filepath);

		try {
			// Read the document stored at the given filepath
			byte[] bytes = Files.readAllBytes(Paths.get(filepath));
			ByteArrayBody file = new ByteArrayBody(bytes, ContentType.DEFAULT_BINARY, "." + ext);
			StringBody body = new StringBody(jsonBody, ContentType.APPLICATION_JSON);

			// Create a HTTP connection
			HttpClientBuilder builder = HttpClientBuilder.create();
			HttpClient httpClient = builder.build();

			// Set the POST call header parameters
			HttpPost httpPost = new HttpPost(dl);
			httpPost.setHeader("OTCSticket", authTicket);
			LOG.info("writeDocument : POST URL body Parameters" + jsonBody);

			// Set the POST call body parameters
			MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
			entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
			entityBuilder.addPart("file", file);
			entityBuilder.addPart("body", body);

			// Create a HttpEntity object out of the entitybuilder object constructed above
			HttpEntity entity = entityBuilder.build();
			httpPost.setEntity(entity);

			// Make the POST call and get the response
			HttpResponse response = httpClient.execute(httpPost);
			HttpEntity resEntity = response.getEntity();

			// Check if the response is not blank
			if (resEntity != null) {
				String retSrc = EntityUtils.toString(resEntity);
				LOG.info("writeDocument : Content Server response received");

				// Response returns nodeID in JSON, parse that JSON to get the nodeID value
				jsonObj = (JSONObject) jsonParser.parse(retSrc);

				// Check if available, then return the document ID
				if (String.valueOf(jsonObj.get("id")) == "null") {
					msg = "writeDocument : Error : Document could not be stored, nodeID was not returned";
					LOG.error(msg);
					return msg;

				} else {
					LOG.info("writeDocument : nodeID returned : " + jsonObj.get("id"));
					documentID = String.valueOf(jsonObj.get("id"));
				}

				return documentID;

			} else {
				msg = "writeDocument : Connection to Content Server failed : no response received";
				LOG.error(msg);
				return msg;
			}

		} catch (IOException | ParseException e) {
			msg = "writeDocument : Finding or reading document failed : IOException";
			LOG.error(msg, e);
			return msg;
		}
	}

	/**
	 * Writes a document on the local system to OTCS
	 * 
	 * @param filepath:   pointing to folder where document is
	 * @param filename:   name of file to be stored
	 * @param ext:        File extension (pdf/docx/...)
	 * @param nodeID:     Content Server nodeID
	 * @param addVersion: set to true if a new version needs to be added, false if
	 *                    not
	 * @return Returns new version number
	 */
	private String writeDocument(String filepath, String filename, String ext, String nodeID, boolean addVersion) {

		String msg;

		// Function overloading, if no new version is needed execute regular function
		if (!addVersion) {
			String ret = writeDocument(filepath, filename, ext, nodeID);
			return ret;
		}

		// Make a JSON parser class object
		JSONParser jsonParser = new JSONParser();
		JSONObject jsonObj;

		// Create filepath
		filepath = filepath + "/" + filename + "." + ext;
		LOG.info("writeDocument : Loading file at filepath : " + filepath);

		try {
			// Create the download URL
			String dl = contentServerURL + "/api/v1/nodes/" + nodeID + "/versions";
			LOG.info("writeDocument : Content Server POST URI: " + dl);

			// Read the document stored at the given filepath
			byte[] bytes = Files.readAllBytes(Paths.get(filepath));
			ByteArrayBody file = new ByteArrayBody(bytes, ContentType.DEFAULT_BINARY, "." + ext);

			// Create a HTTP connection
			HttpClientBuilder builder = HttpClientBuilder.create();
			HttpClient httpClient = builder.build();

			// Set the POST call header parameters
			HttpPost httpPost = new HttpPost(dl);
			httpPost.setHeader("OTCSticket", authTicket);

			// Set the POST call body parameters
			MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
			entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
			entityBuilder.addPart("file", file);

			// Create a HttpEntity object out of the entitybuilder object constructed above
			HttpEntity entity = entityBuilder.build();
			httpPost.setEntity(entity);

			// Make the POST call and get the response
			HttpResponse response = httpClient.execute(httpPost);
			HttpEntity resEntity = response.getEntity();

			// Check if the response is not blank
			if (resEntity != null) {
				String retSrc = EntityUtils.toString(resEntity);
				LOG.info("writeDocument : Content Server response received");

				// Response returns nodeID in JSON, parse that JSON to get the nodeID value
				jsonObj = (JSONObject) jsonParser.parse(retSrc);

				// Check if available, then return the document ID
				if (String.valueOf(jsonObj.get("version_number")) == "null") {
					msg = "writeDocument : Error : Document could not be stored, version number was not returned";
					LOG.error(msg);
					return msg;

				} else {
					LOG.info("writeDocument : version number returned : " + jsonObj.get("version_number"));
					return String.valueOf(jsonObj.get("version_number"));
				}

			} else {
				msg = "writeDocument : Connection to Content Server failed : no response received";
				LOG.error(msg);
				return msg;
			}

		} catch (IOException | ParseException e) {
			msg = "writeDocument : Finding or reading failed: IOException";
			LOG.error(msg, e);
			return msg;
		}
	}

	/**
	 * Updates the categories provided with their values
	 * 
	 * @param categoryValues Array of values of categories to be updated
	 * @param nodeID         Node ID value
	 * @return "0" on succes, error on failure
	 */
	private String categoryUpdate(String categoryValues[], String nodeID) {

		String msg;

		// Construct first part of the PUT URL using webreportname
		String dl = contentServerURL + "/api/v1/nodes/" + nodeID + "/categories/" + categoryID;
		LOG.info("categoryUpdate : PUT url : " + dl);

		// Check if amount of parameters is equal to the amount of parameter values
		if (categoryKeys.size() != categoryValues.length) {
			msg = "categoryUpdate : Web report amount of parameters and values is not equal";
			LOG.error(msg);
			return msg;
		}

		// Create a HTTP connection
		HttpClientBuilder builder = HttpClientBuilder.create();
		HttpClient httpClient = builder.build();

		// Set the PUT call header parameters
		HttpPut httpPut = new HttpPut(dl);
		httpPut.setHeader("OTCSticket", authTicket);

		// Set the PUT call body parameters
		MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
		entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

		// Add parameters and values to the POST URL
		for (int i = 0; i < categoryKeys.size(); i++) {
			StringBody categoryValue = new StringBody(categoryValues[i], ContentType.DEFAULT_TEXT);
			entityBuilder.addPart(categoryKeys.get(i), categoryValue);
			LOG.info("categoryUpdate : Body added : [" + categoryKeys.get(i) + ", " + categoryValues[i] + "]");
		}

		// Create a HttpEntity object out of the entitybuilder object constructed above
		HttpEntity entity = entityBuilder.build();
		httpPut.setEntity(entity);

		try {
			// Make the POST call and get the response
			HttpResponse response = httpClient.execute(httpPut);
			HttpEntity resEntity = response.getEntity();

			// Check if the response is not blank
			if (resEntity != null) {
				String retSrc = EntityUtils.toString(resEntity);
				int statusCode = response.getStatusLine().getStatusCode();

				// Returns 200 if success
				if (statusCode == 200) {
					LOG.info("categoryUpdate : Attributes successfully updated");
					return "0";
				} else if (retSrc.contains("not a valid category")) {
					LOG.info("categoryUpdate : no valid categories found, creating new categories...");

					String status = createNewCategories(categoryValues, nodeID);
					return status;

				} else {
					msg = "categoryUpdate : Content Server response received : " + retSrc;
					LOG.info(msg);

					LOG.info("categoryUpdate : Trying to create new categories...");

					String status = createNewCategories(categoryValues, nodeID);
					return status;
				}

			} else {
				msg = "categoryUpdate : Connection to Content Server failed : no response received";
				LOG.error(msg);
				return msg;
			}

		} catch (IOException e) {
			msg = "categoryUpdate : HTTP PUT connection failed : ClientProtocolException";
			LOG.error(msg, e);
			return msg;
		}
	}

	/**
	 * Creates new categories provided with their values
	 * 
	 * @param categoryValues Array of values of categories to be updated
	 * @param nodeID         Node ID value
	 * @return "0" on succes, error on failure
	 */
	private String createNewCategories(String categoryValues[], String nodeID) {

		String msg;

		// Construct first part of the PUT URL using webreportname
		String dl = contentServerURL + "/api/v1/nodes/" + nodeID + "/categories";
		LOG.info("createNewCategories : PUT url : " + dl);

		// Create a HTTP connection
		HttpClientBuilder builder = HttpClientBuilder.create();
		HttpClient httpClient = builder.build();

		// Set the PUT call header parameters
		HttpPost httpPost = new HttpPost(dl);
		httpPost.setHeader("OTCSticket", authTicket);

		// Set the PUT call body parameters
		MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
		entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

		// Add parameters and values to the Body of the POST URL
		for (int i = 0; i < categoryKeys.size(); i++) {
			StringBody categoryValue = new StringBody(categoryValues[i], ContentType.DEFAULT_TEXT);
			entityBuilder.addPart(categoryKeys.get(i), categoryValue);
			LOG.info("createNewCategories : Body added : [" + categoryKeys.get(i) + ", " + categoryValues[i] + "]");
		}

		// Add category ID
		StringBody categoryIDBody = new StringBody(categoryID, ContentType.DEFAULT_TEXT);
		entityBuilder.addPart("category_id", categoryIDBody);
		LOG.info("createNewCategories : Body added : [" + "category_id" + ", " + categoryID + "]");

		// Create a HttpEntity object out of the entitybuilder object constructed above
		HttpEntity entity = entityBuilder.build();
		httpPost.setEntity(entity);

		try {
			// Make the POST call and get the response
			HttpResponse response = httpClient.execute(httpPost);
			HttpEntity resEntity = response.getEntity();

			// Check if the response is not blank
			if (resEntity != null) {
				String retSrc = EntityUtils.toString(resEntity);
				int statusCode = response.getStatusLine().getStatusCode();

				// Returns 200 if success
				if (statusCode == 200) {
					LOG.info("createNewCategories : Attributes successfully created");
					return "0";
				} else {
					msg = "createNewCategories : Content Server response received : " + retSrc;
					LOG.info(msg);
					return msg;
				}

			} else {
				msg = "createNewCategories : Connection to Content Server failed : no response received";
				LOG.error(msg);
				return msg;
			}

		} catch (IOException e) {
			msg = "createNewCategories : HTTP PUT connection failed : ClientProtocolException";
			LOG.error(msg, e);
			return msg;
		}
	}

	/**
	 * Sets key object, used in password decryption
	 * 
	 * @param myKey: Password generation key
	 */
	private void setKey(String myKey) {
		MessageDigest sha = null;
		try {
			key = myKey.getBytes("UTF-8");

			sha = MessageDigest.getInstance("SHA-1");

			key = sha.digest(key);
			key = Arrays.copyOf(key, 16);

			secretKey = new SecretKeySpec(key, "AES");

		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			LOG.error("Error setting decryption key ", e);
		}
	}

	/**
	 * Decrypts password string
	 * 
	 * @param strToDecrypt: Encrypted password
	 * @return decrypted password as string, null on failure
	 */
	private String decrypt(String strToDecrypt) {

		String secret = "Wnu9VGTh";

		try {
			setKey(secret);

			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
			cipher.init(Cipher.DECRYPT_MODE, secretKey);

			return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));

		} catch (Exception e) {
			LOG.debug("Error while decrypting: ", e);
		}
		return null;
	}

	/**
	 * Returns strings of equal length with every character being an asterisk
	 * 
	 * @param inputString Any string to convert
	 * @return Asterisk converted string
	 */
	private String toAsterisk(String inputString) {

		String asteriskedString = "";
		int length = inputString.length();

		for (int i = 0; i < length; i++) {
			asteriskedString = asteriskedString + "*";
		}

		return asteriskedString;
	}

	/**
	 * Creates a JSON string that is used for initializing all categories in AEL
	 * 
	 * @param filename       Name of the file
	 * @param docID          Document ID
	 * @param categoryValues Category values of Exstream categories
	 * @param docType        Confirmation or invoice
	 * @return
	 */
	private String buildJSONBody(String filename, String docID, String[] categoryValues, String docType) {

		HashMap<String, Object> JSONWM = new HashMap<String, Object>();
		JSONWM.put(categoryWMkey, docType);

		HashMap<String, Object> JSONExs = new HashMap<String, Object>();
		JSONExs.put(categoryKeys.get(0), categoryValues[0]);
		JSONExs.put(categoryKeys.get(1), categoryValues[1]);

		HashMap<String, Object> JSONDoc = new HashMap<String, Object>();
		for (int i = 0; i < 4; i++) {
			JSONDoc.put(categoryDocKeys.get(i), categoryDocValues.get(i));
		}

		HashMap<String, Object> JSONCategories = new HashMap<String, Object>();
		JSONCategories.put(categoryWMkey, JSONWM);
		JSONCategories.put(categoryID, JSONExs);
		JSONCategories.put(categoryDoc, JSONDoc);
		HashMap<String, Object> JSONRoles = new HashMap<String, Object>();
		JSONRoles.put("categories", JSONCategories);

		HashMap<String, Object> JSONMain = new HashMap<String, Object>();
		JSONMain.put("name", filename);
		JSONMain.put("parent_id", docID);
		JSONMain.put("type", 144);
		JSONMain.put("roles", JSONRoles);

		JSONObject JSON = new JSONObject(JSONMain);

		return JSON.toString();
	}
}
