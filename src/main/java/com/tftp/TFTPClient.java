package com.tftp;

import java.io.File;

public class TFTPClient {
    private static String host = "127.0.0.1";
    private static String dbFolder = "client_DB/";
    private static Integer port = 5000;

    public static void main(String[] args) {

        //check for the folder
        File file = new File("client_DB");
        if (file.exists()) {
            //System.out.println("client_DB  exist");
        }else {
            ///System.out.println(" creating new folder client_DB ...");
            (new File(dbFolder)).mkdir();
        }

        Client client = new Client(host, port,dbFolder);

    }
}
