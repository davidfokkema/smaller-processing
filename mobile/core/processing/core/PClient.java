/** Part of the Mobile Processing project - http://mobile.processing.org
 *
 * Copyright (c) 2004-05 Francis Li
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General
 * Public License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307  USA
 */
package processing.core;

import java.io.*;
import javax.microedition.io.*;

/** The <b>PClient</b> object is used to initiate network requests to 
 * web servers on the Internet.  It supports the two most common methods
 * of the Hypertext Transfer Protocol (HTTP), <b>GET</b> and <b>POST</b>, allowing it
 * to communicate with servers as if it were a desktop web browser.  Both
 * methods initiate a new network request and return a <b>PRequest</b> object used
 * to track the request and read the returned data.
 *
 * @category Net
 * @related PRequest
 * @author  Francis Li
 */
public class PClient {    
    protected PMIDlet   midlet;
    
    protected String    server;
    protected int       port;
    
    public PClient(PMIDlet midlet, String server) {
        this(midlet, server, 80);
    }
    
    /**
     * @param midlet PMIDlet: typically use "this"
     * @param server String: name or IP address of server
     * @param port int: optional port number to read/write from on the server
     */
    public PClient(PMIDlet midlet, String server, int port) {
        this.midlet = midlet;
        this.server = server;
        this.port = port;
    }
    
    /** 
     * Minimal URL encoding implementation 
     * @hidden
     */
    public static String encode(String str) {
        StringBuffer encoded = new StringBuffer();
        char c;
        for (int i = 0, length = str.length(); i < length; i++) {
            c = str.charAt(i);
            if (Character.isDigit(c) || Character.isLowerCase(c) || Character.isUpperCase(c)) {
                encoded.append(c);
            } else if (c == ' ') {
                encoded.append('+');
            } else {
                encoded.append('%');
                if (c < 16) {
                    encoded.append('0');
                }
                encoded.append(Integer.toHexString(c));
            }
        }
        return encoded.toString();
    }
    
    /** The GET method is used to fetch data from a web server.  It is the method
     * used by web browsers most commonly used to fetch the contents of a URL.  It
     * can also be used to execute scripts that return data based on parameters
     * passed in the URL string.
     * 
     * @param file String: name of file to fetch or script to execute on the web server
     * @param params String[]: an array of String objects representing the names of parameters being passed to a script
     * @param values String[]: an array of String objects representing the values of parameters being passed to a script
     * @return PRequest
     */
    public PRequest GET(String file) {
        return request(file, null, null);
    }
        
    public PRequest GET(String file, String[] params, String[] values) {        
        StringBuffer query = new StringBuffer();
        query.append(file);
        query.append("?");
        for (int i = 0, length = params.length; i < length; i++) {
            query.append(params[i]);
            query.append("=");
            query.append(encode(values[i]));
            if (i < (length - 1)) {
                query.append("&");
            }
        }        
        return GET(query.toString());
    }
    
    /** The <b>POST</b> method is used to submit data to a web server to be processed.
     * This is the method most commonly used by web browsers to send data from
     * forms when the user clicks on a "Submit" button.  The <b>params</b> array
     * contains the names of the form fields.  The <b>values</b> array must be 
     * the same size as and contain the values corresponding, in order, to the 
     * names specified in the <b>params</b> array.  If you need to submit
     * binary data, create an array of Objects that can contain either String
     * values or byte array (byte[]) objects containing the binary data.
     *
     * @param file String: name of the web page/script that will process the data
     * @param params String[]: an array of String objects containing the names of form fields being submitted
     * @param values String[]: an array of String objects containing the values of form fields being submitted
     * @param data Object[]: an array of objects, each object either String or byte[], representing values or binary data being submitted 
     * @return PRequest
     */
    public PRequest POST(String file, String[] params, String[] values) {
        //// generate request payload
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        for (int i = 0, length = params.length; i < length; i++) {
            if (i > 0) {
                ps.print("&");
            }
            ps.print(encode(params[i]));
            ps.print("=");
            ps.print(encode(values[i]));             
        }
        return request(file, "application/x-www-form-urlencoded", baos.toByteArray());
    }
    
    public PRequest POST(String file, String[] params, Object[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            for (int i = 0, length = params.length; i < length; i++) {
                ps.print("--BOUNDARY_185629\r\n");
                ps.print("Content-Disposition: form-data; name=\"");
                ps.print(params[i]);
                if (data[i] instanceof String) {
                    ps.print("\"\r\n");
                    ps.print("\r\n");
                    ps.print((String) data[i]);
                    ps.print("\r\n");
                } else if (data[i] instanceof byte[]) {
                    byte[] buffer = (byte[]) data[i];
                    ps.print("\"; filename=\"");
                    ps.print(params[i]);
                    ps.print("\"\r\n");
                    ps.print("Content-Type: application/octet-stream\r\n");
                    ps.print("Content-Transfer-Encoding: binary\r\n\r\n");
                    ps.write(buffer);
                    ps.print("\r\n");
                }                
            }
            ps.print("--BOUNDARY_185629--\r\n\r\n");
            return request(file, "multipart/form-data; boundary=BOUNDARY_185629", baos.toByteArray());
        } catch (IOException ioe) {
            throw new PException(ioe);
        }
    }
    
    protected PRequest request(String file, String contentType, byte[] bytes) {
        //// create url
        StringBuffer url = new StringBuffer();
        url.append("http://");
        url.append(server);
        if (port != 80) {
            url.append(":");
            url.append(port);
        }
        url.append(file);
        //// initiate request
        PRequest request = new PRequest(midlet, url.toString(), contentType, bytes);
        Thread t = new Thread(request);
        t.start();
        
        return request;
    }
}
