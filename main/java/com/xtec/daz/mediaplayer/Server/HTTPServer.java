package com.xtec.daz.mediaplayer.Server;

/**
 * Created by Daz "The Razamataz" Baz on 10/08/2016.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.webkit.WebView;

import com.xtec.daz.mediaplayer.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public class HTTPServer extends NanoHTTPD {
    private final static int PORT = 8080;
    public Context context;

    /**
     * Common mime types for dynamic content
     */
    public static final String
            MIME_PLAINTEXT = "text/plain",
            MIME_HTML = "text/html",
            MIME_JS = "application/javascript",
            MIME_CSS = "text/css",
            MIME_PNG = "image/png",
            MIME_GIF = "image/gif",
            MIME_DEFAULT_BINARY = "application/octet-stream",
            MIME_XML = "text/xml";

    // Stores all media player settings
    public static final String PREFS_NAME = "MediaPlayerSettings";

    SharedPreferences settings;
    SharedPreferences.Editor editor;

    public HTTPServer(Context con) throws IOException {
        super(PORT);
        context = con;
        start();

        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        editor = settings.edit();

        // Add default unchangeable values TODO: change what these are depending on device
        editor.putString("FWNum", "123456");
        editor.commit();
    }

    //TODO: add variables to string file. fix post http methods to save shit. test again
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> parms = session.getParms();

        if (uri.contains("x_sml.gif")) {
            // Add logo
            InputStream buffer = context.getResources().openRawResource(
                    context.getResources().getIdentifier("x_sml",
                            "raw", context.getPackageName()));

            return new NanoHTTPD.Response(Response.Status.OK, MIME_GIF, buffer, 3958); // 3958 is the number of bytes of logo

        } else if (uri.contains("index.html")) {

            if (parms.get("DevDesc") != null) {
                // save device description
                editor.putString("DevDesc", parms.get("DevDesc"));
                editor.commit();
            }

            InputStream ins = context.getResources().openRawResource(
                    context.getResources().getIdentifier("index",
                            "raw", context.getPackageName()));

            String msg = convertStreamToString(ins); // Convert input stream to a string
            msg = replaceTemplate(msg);
            return newFixedLengthResponse(msg);

        } else if (uri.contains("net.html")) {
            InputStream ins = context.getResources().openRawResource(
                    context.getResources().getIdentifier("net",
                            "raw", context.getPackageName()));

            String msg = convertStreamToString(ins); // Convert input stream to a string
            msg = replaceTemplate(msg);
            return newFixedLengthResponse(msg);

        } else if (uri.contains("din.html")) {
            InputStream ins = context.getResources().openRawResource(
                    context.getResources().getIdentifier("din",
                            "raw", context.getPackageName()));

            String msg = convertStreamToString(ins); // Convert input stream to a string
            msg = replaceTemplate(msg);
            return newFixedLengthResponse(msg);

        } else if (uri.contains("dout.html")) {
            InputStream ins = context.getResources().openRawResource(
                    context.getResources().getIdentifier("dout",
                            "raw", context.getPackageName()));

            String msg = convertStreamToString(ins); // Convert input stream to a string
            msg = replaceTemplate(msg);
            return newFixedLengthResponse(msg);

        } else if (uri.contains("oth.html")) {
            InputStream ins = context.getResources().openRawResource(
                    context.getResources().getIdentifier("oth",
                            "raw", context.getPackageName()));

            String msg = convertStreamToString(ins); // Convert input stream to a string
            msg = replaceTemplate(msg);
            return newFixedLengthResponse(msg);

        } else if (uri.contains("gen_error.html")) {
            InputStream ins = context.getResources().openRawResource(
                    context.getResources().getIdentifier("gen_error",
                            "raw", context.getPackageName()));

            String msg = convertStreamToString(ins); // Convert input stream to a string
            msg = replaceTemplate(msg);

            return newFixedLengthResponse(msg);

        } else if (uri.contains("netdata_error.html")) {
            InputStream ins = context.getResources().openRawResource(
                    context.getResources().getIdentifier("netdata_error",
                            "raw", context.getPackageName()));

            String msg = convertStreamToString(ins); // Convert input stream to a string
            msg = replaceTemplate(msg);

            return newFixedLengthResponse(msg);
        } else if (uri.contains("save_error.html")) {
            InputStream ins = context.getResources().openRawResource(
                    context.getResources().getIdentifier("save_error",
                            "raw", context.getPackageName()));

            String msg = convertStreamToString(ins); // Convert input stream to a string
            msg = replaceTemplate(msg);

            return newFixedLengthResponse(msg);

        } else if (uri.contains("submit_ok.html")) {
            InputStream ins = context.getResources().openRawResource(
                    context.getResources().getIdentifier("submit_ok",
                            "raw", context.getPackageName()));

            String msg = convertStreamToString(ins); // Convert input stream to a string
            msg = replaceTemplate(msg);

            return newFixedLengthResponse(msg);

        } else {
            InputStream ins = context.getResources().openRawResource(
                    context.getResources().getIdentifier("index",
                            "raw", context.getPackageName()));

            String msg = convertStreamToString(ins); // Convert input stream to a string
            msg = replaceTemplate(msg);

            return newFixedLengthResponse(msg);
        }
    }

    /**
     * In the HTML code, there are variables between ~ which represents template HTML code or
     * stored values. This replaces the variable names with their values.
     *
     * @param msg html code with ~
     * @return complete html code
     */
    String replaceTemplate(String msg) {
        // Go through html file looking for places to add templates or stored values
        int i;
        i = msg.indexOf('~');
        while (i != -1) {
            // Check if we are inserting a value or inserting template
            if (msg.charAt(i + 1) == '/') {
                // Insert html code
                String file = msg.substring(i + 2, msg.indexOf('~', i + 1)); // Get file name
                try {
                    InputStream fileIns = context.getResources().openRawResource(
                            context.getResources().getIdentifier(file,
                                    "raw", context.getPackageName())); // Open file name
                    String fileHTML = convertStreamToString(fileIns);
                    msg = new StringBuilder(msg).replace(i, msg.indexOf('~', i + 1) + 1, fileHTML).toString(); // Insert html in place of variable
                } catch (Exception e) {
                    System.out.print("Error in replacing html code: " + e);
                    msg = new StringBuilder(msg).replace(i, msg.indexOf('~', i + 1) + 1, "").toString(); // Error. Put blank
                }

            } else {
                String value = msg.substring(i + 1, msg.indexOf('~', i + 1)); // Get variable name
                try {
                    String storedValue = settings.getString(value, ""); // Get value from shared preferences
                    msg = new StringBuilder(msg).replace(i, msg.indexOf('~', i + 1) + 1, storedValue).toString();
                } catch (Exception e) {
                    System.out.print("Error in finding variable: " + e);
                    msg = new StringBuilder(msg).replace(i, msg.indexOf('~', i + 1) + 1, "").toString(); // Error. Put blank

                }
            }

            i = msg.indexOf('~');

        }

        return msg;
    }

    /**
     * Scanner iterates over tokens in the stream, and in this case we separate tokens using
     * "beginning of the input boundary" (\A) thus giving us only one token for the entire
     * contents of the stream.
     *
     * @param is
     * @return
     */
    static String convertStreamToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

}
