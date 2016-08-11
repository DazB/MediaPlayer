package com.xtec.daz.mediaplayer.Server;

/**
 * Created by Daz "The Razamataz" Baz on 10/08/2016.
 */

import android.content.Context;
import android.content.res.Resources;

import com.xtec.daz.mediaplayer.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class HTTPServer extends NanoHTTPD {
    private final static int PORT = 8080;
    public Context context;

    public HTTPServer(Context con) throws IOException {
        super(PORT);
        context = con;
        start();
    }

    @Override
    public Response serve(IHTTPSession session) {

        InputStream ins = context.getResources().openRawResource(
                context.getResources().getIdentifier("index",
                        "raw", context.getPackageName()));

        String msg = convertStreamToString(ins); // Convert input stream to a string

        // Go through html file looking for places to add templates or stored values
        int i;
        i = msg.indexOf('~');
        while (i != -1) {
            // Check if we are inserting a value or inserting template
            if (msg.charAt(i + 1) == '/') {
                // This is a template. Insert html code
                String file = msg.substring(i + 2, msg.indexOf('~', i + 1));
                InputStream fileIns = context.getResources().openRawResource(
                        context.getResources().getIdentifier(file,
                                "raw", context.getPackageName()));
                String fileHTML = convertStreamToString(fileIns);
                msg = new StringBuilder(msg).replace(i, msg.indexOf('~', i + 1) + 1, fileHTML).toString();

            }
            else {
                String value = msg.substring(i + 1, msg.indexOf('~', i + 1));
                String storedValue = (String) context.getResources().getText(
                        context.getResources().getIdentifier(value, "string", context.getPackageName()));
                msg = new StringBuilder(msg).replace(i, msg.indexOf('~', i + 1) + 1, storedValue).toString();

            }

            i = msg.indexOf('~');

        }

        return newFixedLengthResponse(msg);
    }

    /**
     * Scanner iterates over tokens in the stream, and in this case we separate tokens using
     * "beginning of the input boundary" (\A) thus giving us only one token for the entire
     * contents of the stream.
     * @param is
     * @return
     */
    static String convertStreamToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    /**
     * Class for IndexPage storing Index values
     */
    class IndexPage {
        public String FWNum;
        public String DevDesc;

        // constructor
        public IndexPage(String  FWNum, String DevDesc) {
            this.FWNum = FWNum;
            this.DevDesc = DevDesc;
        }
    }
}
