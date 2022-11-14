package com.ibm.watson.aiops.connectors.github;

public class Utils {

    public static String getDataMissingPropertyErrorMessage(String configurationName, String property) {
        return String.format("%s configuration is missing %s property", configurationName, property);
    }

}