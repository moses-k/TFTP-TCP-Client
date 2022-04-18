package com.tftp;

import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Optional;


public class Client implements AutoCloseable {
    // initialize socket and input output streams
    private static Socket clientSocket = null;
    private static DataInputStream input = null;
    private static DataInputStream fromServer = null;
    private static DataOutputStream out = null;
    /**
     * The default buffer size
     */
    public static final int DEFAULT_BUFFER_SIZE = 81920;
    static boolean success = false;


    // constructor to put ip address and port
    public Client(String address, int port, String dbFolder) {
        // establish a connection
        try {
            clientSocket = new Socket(address, port);
            //System.out.println("Connected");
            //set a socket timeout of 5 seconds, after we will just resend
            clientSocket.setSoTimeout(5000);
            // takes input from terminal
            input = new DataInputStream(System.in);
            //server response
            fromServer = new DataInputStream(clientSocket.getInputStream());
            // sends output to the socket
            out = new DataOutputStream(clientSocket.getOutputStream());

            String res = fromServer.readUTF();
            System.out.println(res);

        } catch (UnknownHostException u) {
            System.out.println(u);
        } catch (IOException i) {
            System.out.println(i);
        }

        // string to read message from input
        String line = "";

        // keep reading until "Over" is input
        while (!line.equals("Over")) {
            try {
                //write command to the server
                line = input.readLine();
                out.writeUTF(line);

                Thread.sleep(0);
                //Response
                String res = fromServer.readUTF();
                System.out.println(res);

                String fileToStore = null;
                while (res.equalsIgnoreCase("Input file name")) {
                    line = input.readLine();
                    if (!line.isEmpty()) {
                        fileToStore = dbFolder + line;
                        File file = new File(fileToStore);
                        if (!file.exists()) {
                            System.out.println("File does not exist");
                        } else {
                            out.writeUTF(line);
                            break;
                        }
                    }
                }

                while (res.equalsIgnoreCase("Enter file name")) {
                    String fileName = input.readLine();
                    out.writeUTF(fileName);
                    //Response
                    //if(fromServer.readUTF() !=null){
                   res = fromServer.readUTF();
                   System.out.println(res);
                   String download = dbFolder+"downloads/";

                    if (!res.equalsIgnoreCase("File does not exit")) {
                        //read file and save it
                        File file = new File(download + fileName);
                        //Check if download folder exist
                        if(!file.exists()){
                            (new File(download)).mkdir();
                        }

                        boolean success = copyInputStreamToFile(fromServer, file);
                        if (success) {
                            System.out.println("File retrieved successful..");
                            out.write(("received" + "\r\n").getBytes());
                            return;
                        } else {
                            System.out.println("Error occurred");
                        }
                       out.flush();
                       break;

                    } else {
                        //start
                        out.writeUTF("2");
                        break;
                    }
                }


                //Response
                res = fromServer.readUTF();
                System.out.println(res);

                if (res.equalsIgnoreCase("sending...")) {
                    send(out, dbFolder + line);
                }

            /*    copyInputStreamToFile(in, file);
                if (success) {
                    System.out.println("File Retrieved successful");
                    response = "File save successfully";
                    //out.write((output + "\r\n").getBytes());
                } else {
                    response = "File not save";
                    //out.write((output + "\r\n").getBytes());
                }
                out.flush();*/

            } catch (IOException i) {
                System.out.println(i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // close the connection
        try {
            input.close();
            out.close();
            clientSocket.close();
        } catch (IOException i) {
            System.out.println(i);
        }
    }

    public int write(String content) {

        byte[] cbytes = content.getBytes();
        try {
            this.out.write(cbytes);
            this.out.flush();
            return cbytes.length;
        } catch (IOException ex) {
            System.out.println("Could not write to Socket" + ex);
            return 0;
        }
    }

    private int readNext(StringBuilder stringBuilder, Buffer buffer) throws IOException {
        // Attempt to read up to the buffers size
        int read = input.read(buffer.array());
        // If EOF is reached (-1 read)
        // we disconnect, because the
        // other end disconnected.
        if (read == -1) {
            //disconnect();
            return -1;
        }
        // Add the read byte[] as
        // a String to the stringBuilder.
        stringBuilder.append(new String(buffer.array()).trim());
        buffer.clear();
        return read;
    }

    private Optional<String> readBlocking() throws IOException {
        final Buffer buffer = new Buffer(512);
        final StringBuilder stringBuilder = new StringBuilder();
        // This call blocks. Therefore
        // if we continue past this point
        // we WILL have some sort of
        // result. This might be -1, which
        // means, EOF (disconnect.)
        if (readNext(stringBuilder, buffer) == -1) {
            return Optional.empty();
        }
        while (input.available() > 0) {
            buffer.reallocate(input.available());
            if (readNext(stringBuilder, buffer) == -1) {
                return Optional.empty();
            }
        }

        buffer.teardown();

        return Optional.of(stringBuilder.toString());
    }

    public String readAll() throws IOException {
        Optional<String> response = readBlocking();
        return response.get();
    }

    @Override
    public void close() throws Exception {
        try {
            this.out.close();
            this.input.close();
        } catch (Exception ex) {

        }
    }

    public static void send(DataOutputStream os, String fileName) {
        //track the time taken and the number of bytes sent to print at the end if all goes well
        long startTime = System.currentTimeMillis();
        int bytesSent = 0;

        //allocate a buffer for sending data - might as well make this 512 bytes, like the data packets in TFTP
        byte[] buffer = new byte[1026];

        //open an input stream to the file
        try (FileInputStream reader = new FileInputStream(fileName)) {
            int num;
            try {
                //keep on writing to the output stream until the end of the file is reached
                while ((num = reader.read(buffer)) != -1) {
                    os.write(buffer, 0, num);
                    bytesSent += num;
                }
            } catch (IOException e) {
                System.out.println("error sending file: " + e.getMessage());
                return;
            }
        } catch (IOException e) {
            System.out.println("error reading from file: " + e.getMessage());
            return;
        }

        //print information about the transfer, and finish
        long time = System.currentTimeMillis() - startTime;
        double seconds = (double) time / 1000.0;
        BigDecimal bigDecimal = new BigDecimal(seconds);
        bigDecimal = bigDecimal.setScale(1, BigDecimal.ROUND_UP);
        System.out.printf("sent %d bytes in %s seconds%n", bytesSent, bigDecimal.toPlainString());
    }

    private static boolean copyInputStreamToFile(DataInputStream inputStream, File file) throws IOException {
        try {
            success = false;

            // append = false
            try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
                int read;
                byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
                //byte[] bytes = new byte[510];
                // while ((read = inputStream.read(bytes)) != -1) {

                while ((read = inputStream.read(bytes)) != -1) {
                    outputStream.write(bytes, 0, read);
                    System.out.println("File downloaded! "+ file.getAbsolutePath());
                    success = true;
                    break;
                }
                outputStream.flush();
                return true;
            }
        } catch (Exception e) {
            System.out.println("Error occured while writing to file " + e.getMessage());
        }
        return  false;

    }
}
