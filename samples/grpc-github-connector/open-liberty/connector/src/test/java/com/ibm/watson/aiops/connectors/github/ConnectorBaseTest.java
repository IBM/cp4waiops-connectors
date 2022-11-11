package com.ibm.watson.aiops.connectors.github;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConnectorBaseTest {
	
	protected static String rootDir;
    ObjectMapper mapper = new ObjectMapper();

	
	protected JsonNode getJsonData(String resourceDir) throws Exception {
        // get Json data and the target output
        File input = new File("src/test/" + resourceDir);
        FileInputStream inStream = new FileInputStream(input);
        BufferedInputStream bis = new BufferedInputStream(inStream);
        return mapper.readTree(bis); 
    }
	
}
