/**
 * Media player application. This application acts as a server to receive commands to play videos
 */
package com.xtec.daz.mediaplayer;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.VideoView;
import android.media.AudioManager;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;


/**
 * Main class of the application
 */
public class MainActivity extends AppCompatActivity {
    public static final int FAILURE = -1;
    public static final int SUCCESS = 1;
    public static final int NO_FILE_NUMBER = 2;
    public static final int FILE_NOT_FOUND = 3;
    public static final int FILE_ALREADY_LOADED = 4;
    public static final int FILE_ALREADY_PLAYING = 5;

    public static int FRAME_RATE = 30;

    private ServerSocket serverSocket; // Server socket object
    Thread serverThread = null;
    public static final int SERVERPORT = 6000;
    Socket clientSocket = null; // Server side client socket reference
    PrintWriter out = null; // Output stream to the client to send messages
    Handler messageReceivedHandler; // Handler to handle client communication

    // 2 video views. Whilst one is playing the other will be hidden loading
    VideoView shownView;
    VideoView hiddenView;

    MediaPlayer mediaPlayer;

    int loadedVideo; // Which video file has been loaded (on hiddenView)
    int playingVideo; // Which video file is playing (on shownView)

    //TODO: path may change?
    //private static final String STORAGE_PATH = Environment.getExternalStorageDirectory().getPath() + "/Download";
    private static final String STORAGE_PATH = "/mnt/udisk";

    /**
     * Activity launched. Initialise activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            Runtime.getRuntime().exec("ifconfig eth0 up");
            Runtime.getRuntime().exec("ifconfig eth0 dhcp start");
        } catch (IOException e) {
            Log.e("OLE","Runtime Error: "+e.getMessage());
            e.printStackTrace();
        }

        String ipAddress = getIPAddress(true);

        // Get a reference to the VideoView instances.
        shownView = (VideoView) findViewById(R.id.videoView1);
        hiddenView = (VideoView) findViewById(R.id.videoView2);

        // Way to access mediaplayer of videoview if needed
//        MediaPlayer mMediaPlayer;
//        shownView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//            @Override
//            public void onPrepared(MediaPlayer mp) {
//                mMediaPlayer = mp;
//
//            }
//        });

        // Create handler to deal with incoming messages from clients
        messageReceivedHandler = new Handler();

        // Start new thread to listen for client
        this.serverThread = new Thread(new ServerThread());
        this.serverThread.start();

    }

    /**
     * Activity is finished or being destroyed by the system TODO: more clean up stuff?
     * TODO: handle disconnect
     */
    @Override
    protected void onStop() {
        super.onStop();
        try {
            serverSocket.close(); // Clean up
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main TCP/IP server thread. Listens for client, and blocks on serverSocket.accept
     * until a client accepts, then it makes a new communication thread for that client.
     * Only accepts one client
     */
    class ServerThread implements Runnable {
        public void run() {
            try {
                // Bind server socket to a port
                serverSocket = new ServerSocket(SERVERPORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                // Accept the client connection and hand over communication to server side client socket
                clientSocket = serverSocket.accept(); // Blocks until client connects
                // Get the output stream to the client to write data back to client
                try {
                    out = new PrintWriter(clientSocket.getOutputStream(), true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Create communication thread to communicate with client
                CommunicationThread commThread = new CommunicationThread();
                new Thread(commThread).start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Communication thread. Listens for communication from the client, then passes that to
     * the control thread. One for each client, always runs, and is blocked input.readLine
     * until the client sends a command
     */
    class CommunicationThread implements Runnable {
        private BufferedReader input;

        public CommunicationThread() {
            try {
                this.input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Blocks waiting for input
                    String read = input.readLine();
                    // Send command to control thread
                    messageReceivedHandler.post(new ControlThread(read));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * Media Player control thread. Receives commands from communication thread, and acts
     * accordingly. Only runs when a message is received
     */
    class ControlThread implements Runnable {
        private String msg;

        public ControlThread(String str) {
            this.msg = str;
        }

        @Override
        public void run() {
            String command = msg.substring(0, 2); // Get command
            int rc; // Return code

            switch (command) {
                // Load command
                case "LD":
                    if (loadVideo(msg) == SUCCESS) {
                        out.println("OK");
                    }
                    if (loadVideo(msg) == FILE_ALREADY_PLAYING) {
                        out.println("File already playing");
                    }
                    break;

                // Play command
                case "PL":
                    rc = loadVideo(msg);

                    if (rc == FILE_ALREADY_PLAYING) {

                        // Video is already on shown view. Play.
                        shownView.setVisibility(View.VISIBLE);
                        shownView.start();

                        // Remove possible loop that may have been set on view
                        shownView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                //Do nothing. Resets loop handler if there is one
                            }
                        });

                        out.println("OK");
                        break;
                    }

                    if (rc != SUCCESS) {
                        break;
                    }

                    // Switch the views
                    switchViewsAndStart();

                    // Remove possible loop that may have been set on view
                    shownView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            //Do nothing. Resets loop handler if there is one
                        }
                    });

                    playingVideo = Integer.parseInt(msg.substring(2, msg.length())); // Save which video is playing
                    out.println("OK");
                    break;

                // Loop command
                case "LP":
                    rc = loadVideo(msg);

                    if (rc == FILE_ALREADY_PLAYING) {

                        shownView.start(); // Play the loaded video

                        // Video is already on shown view. Loop.
                        shownView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                mp.seekTo(1);
                                mp.start();
                            }
                        });
                        out.println("OK");
                        break;
                    }

                    if (rc != SUCCESS) {
                        break;
                    }

                    // Switch the views and play video 
                    switchViewsAndStart();

                    playingVideo = Integer.parseInt(msg.substring(2, msg.length())); // Save which video is playing
                    // On completion of video, go back to the start
                    shownView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            mp.seekTo(1);
                            mp.start();
                        }
                    });
                    out.println("OK");
                    break;

                // Stop command
                case "ST":
                    shownView.pause();
                    shownView.seekTo(1);
                    shownView.setVisibility(View.INVISIBLE);
                    out.println("OK");
                    break;

                // Pause command
                case "PA": // TODO: pausing mid video, then playing from beginning, there is some audio lag
                    shownView.pause();
                    out.println("OK");
                    break;

                // Video mute command
                case "VM":
                    int n = -1;
                    try {
                        n = Integer.parseInt(msg.substring(2, msg.length())); // Get command number
                    } catch (NumberFormatException e) {
                        out.println("ERROR: specify a command number (0 for off, 1 for on)");
                        break;
                    }
                    // Show video
                    if (n == 1) {
                        shownView.setAlpha(1f);
                        hiddenView.setAlpha(1f);
                    }
                    // Hide video
                    else if (n == 0) {
                        shownView.setAlpha(0f);
                        hiddenView.setAlpha(0f);

                    }
                    else {
                        out.println("ERROR: incorrect number. 0 for off, 1 for on");
                        break;
                    }
                    out.println("OK");
                    break;

                // Audio mute command
                case "AM":
                    n = -1;
                    try {
                        n = Integer.parseInt(msg.substring(2, msg.length())); // Get command number
                    } catch (NumberFormatException e) {
                        out.println("ERROR: specify a command number (0 for off, 1 for on)");
                        break;
                    }

                    AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    // Unmute
                    if (n == 1) {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                    }
                    // Mute
                    else if (n == 0) {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                    }
                    else {
                        out.println("ERROR: incorrect number. 0 for off, 1 for on");
                        break;
                    }
                    out.println("OK");
                    break;

                // Seek command
                case "SE":
                    int seekTimeMs = 0;
                    seekTimeMs = seekVideo(msg.substring(2, msg.length()));

                    if (seekTimeMs != FAILURE) {
                        shownView.seekTo(seekTimeMs); // Seek to calculated position
                        out.println("OK");

                    }
                    break;

                default:
                    out.println("ERROR: Command not recognised");
            }
        }
    }

    /**
     * Parses seek command and returns number of ms to seek to in video
     * @param seekTime seek command string
     * @return number of milliseconds to seek to
     */
    int seekVideo(String seekTime){
        if (!seekTime.matches("\\d{2}:\\d{2}:\\d{2}:\\d{2}")) {
            // Seek time format incorrect
            out.println("ERROR: Seek time format incorrect. Ensure in form SEhh:mm:ss:ff");
            return FAILURE;
        }
        int seekTimeMs = 0;
        // Split string into time parts
        String parts[] = seekTime.split(":");
        int hours = Integer.parseInt(parts[0]); // Get number of hours
        if (hours < 0 || hours > 23) {
            out.println("ERROR: hh must be between 00 and 23");
            return FAILURE;
        }
        seekTimeMs = seekTimeMs + (hours * 3600000); // Add hour seek time in milliseconds

        int mins = Integer.parseInt(parts[1]); // Get number of mins
        if (mins < 0 || hours > 59) {
            out.println("ERROR: mm must be between 00 and 59");
            return FAILURE;
        }
        seekTimeMs = seekTimeMs + (mins * 60000); // Add min seek time in ms

        int secs = Integer.parseInt(parts[2]); // Get number of secs
        if (secs < 0 || secs > 59) {
            out.println("ERROR: ss must be between 00 and 59");
            return FAILURE;
        }
        seekTimeMs = seekTimeMs + (secs * 1000); // Add secs seek time in ms

        int frames = Integer.parseInt(parts[3]); // Get number of frames
        if (frames < 0 || frames > FRAME_RATE) {
            out.println("ERROR: ff must be between 00 and frame rate (" + FRAME_RATE + ")");
            return FAILURE;
        }

        if (frames != 0) { // Avoid dividing by zero which will blow up the universe
            seekTimeMs = seekTimeMs + (1000 / frames); // Add number of frames in ms
        }

        return seekTimeMs;
    }

    /**
     * Switches between the VideoViews, making one visible and the other invisible, and play
     */
    void switchViewsAndStart() {
        VideoView temp = null; // Temp VideoView used in switch of shown and hidden views

        // Set the view behind to be visable. This removes flash of black frame
        hiddenView.setVisibility(View.VISIBLE);
        hiddenView.start(); // Play the loaded video

        shownView.stopPlayback();

        temp = shownView;
        shownView = hiddenView;
        hiddenView = temp;

        // Hide the previous video
        hiddenView.setVisibility(View.INVISIBLE);


    }

    /**
     * Finds and loads a video to the hidden video view.
     * Overwrites any buffered video in the hidden view.
     *
     * @param msg message sent by the client
     * @return integer status code of result
     */
    public int loadVideo(String msg) {
        int fileNumber = -1;
        int seekPos = 0;
        // Check if load command also includes a seek command
        if (msg.matches("(.*)SE\\d{2}:\\d{2}:\\d{2}:\\d{2}")) {
            try {
                seekPos = msg.indexOf(":"); // Finds index of first ":" in msg
                seekPos = seekPos - 2; // Get to beginning of seek time (2 characters before first ":")
                fileNumber = Integer.parseInt(msg.substring(2, seekPos - 2)); // Get file number
            } catch (NumberFormatException e) {
                out.println("ERROR: specify file number");
                return NO_FILE_NUMBER;
            }
        }
        else {
            try {
                fileNumber = Integer.parseInt(msg.substring(2, msg.length())); // Get file number
            } catch (NumberFormatException e) {
                out.println("ERROR: specify file number");
                return NO_FILE_NUMBER;
            }
        }

        if (fileNumber == playingVideo) return FILE_ALREADY_PLAYING; // File is already on shown view
        if (fileNumber == loadedVideo) return SUCCESS; // Already loaded video

        String path = getFilePath(fileNumber); // Get file path
        if (path.equals("")) {
            out.println("ERROR: File can't be found");
            return FILE_NOT_FOUND;
        }

        // Load the video to the hidden view
        hiddenView.setVideoPath(path);

        // If we are seeking to a specific point in the loaded video
        if (msg.matches("(.*)SE\\d{2}:\\d{2}:\\d{2}:\\d{2}")) {
            int seekTime = seekVideo(msg.substring(seekPos, msg.length()));
            if (seekTime != FAILURE) {
                hiddenView.seekTo(seekTime); // Seek to specified point in loaded video
            }
            else hiddenView.seekTo(1); // Incorrect seek command. Load video anyway.
        }

        else hiddenView.seekTo(1); // Buffer video by moving ahead 1ms

        loadedVideo = fileNumber;

        return SUCCESS;
    }

    /**
     * Returns the absolute file path that relates to the sent file number
     * @param fileNumber The file number sent in the clients message
     * @return absolute file path to the video file
     */
    public String getFilePath(int fileNumber) {
        String path = "";
        //TODO: it probably won't be this path. Change to sd card or whatever
        File storage = new File(STORAGE_PATH);
        File[] listOfFiles = storage.listFiles(); // Get list of every file in directory

        if (listOfFiles == null) {
            out.println("ERROR: Storage not found");
            return path;
        }

        // Loops through every file trying to find one that matches filenumber
        for (int i = 0; i < listOfFiles.length; i++) {
            // Remove file extenstion
            String file;
            try {
                file = listOfFiles[i].toString().substring(0, listOfFiles[i].toString().lastIndexOf('.'));
            }
            catch (Exception e) {
                // File doesn't have extension. Move on
                continue;
            }

            // Get file number
            int j = file.length();
            while (j > 0 && Character.isDigit(file.charAt(j - 1))) {
                j--;
            }
            if (j == file.length()) {
                // File does not end with file number, so move on to next file
                continue;
            }
            int number = Integer.parseInt(file.substring(j, file.length()));
            // Compare file numbers
            if (fileNumber == number) {
                // Get path and break out of for loop
                path = listOfFiles[i].toString();
                break;
            }
        }
        return path;
    }

    /**
     * Get IP address from first non-localhost interface
     * @param useIPv4  true=return ipv4, false=return ipv6
     * @return  address or empty string
     */
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr.indexOf(':')<0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) { } // for now eat exceptions
        return "";
    }

    /**
     * Finds every external storage device attached
     */
    public static HashSet<String> getExternalMounts() {
        final HashSet<String> out = new HashSet<String>();
        String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4).*rw.*";
        String s = "";
        try {
            final Process process = new ProcessBuilder().command("mount")
                    .redirectErrorStream(true).start();
            process.waitFor();
            final InputStream is = process.getInputStream();
            final byte[] buffer = new byte[1024];
            while (is.read(buffer) != -1) {
                s = s + new String(buffer);
            }
            is.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        // parse output
        final String[] lines = s.split("\n");
        for (String line : lines) {
            if (!line.toLowerCase(Locale.US).contains("asec")) {
                if (line.matches(reg)) {
                    String[] parts = line.split(" ");
                    for (String part : parts) {
                        if (part.startsWith("/"))
                            if (!part.toLowerCase(Locale.US).contains("vold"))
                                out.add(part);
                    }
                }
            }
        }
        return out;
    }
}
