/**
 * Media player application. This application acts as a server to receive commands to play videos
 */
package com.xtec.daz.mediaplayer;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.VideoView;
import android.os.Environment;
import android.media.AudioManager;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashSet;
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
    public static final int SUCCESS = 1;
    public static final int NO_FILE_NUMBER = 2;
    public static final int FILE_NOT_FOUND = 3;
    public static final int FILE_ALREADY_LOADED = 4;
    public static final int FILE_ALREADY_PLAYING = 5;

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
    String loadedVideoFPS;
    String playingVideoFPS;

    //TODO: path may change?
    private static final String STORAGE_PATH = Environment.getExternalStorageDirectory().getPath() + "/Download";

    /**
     * Activity launched. Initialise activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                    loadVideo(msg);
                    break;

                // Play command
                case "PL":
                    rc = loadVideo(msg);

                    if (rc == FILE_ALREADY_PLAYING) {

                        // Remove possible loop that may have been set on view
                        shownView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                //Do nothing. Resets loop handler if there is one
                            }
                        });

                        // Video is already on shown view. Play.
                        shownView.setVisibility(View.VISIBLE);
                        shownView.start();
                        break;
                    }

                    if (rc != SUCCESS) {
                        break;
                    }

                    // Switch the views
                    switchViews();

                    // Remove possible loop that may have been set on view
                    shownView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            //Do nothing. Resets loop handler if there is one
                        }
                    });

                    shownView.start(); // Play the loaded video
                    playingVideo = Integer.parseInt(msg.substring(2, msg.length())); // Save which video is playing
                    break;

                // Loop command
                case "LP":
                    rc = loadVideo(msg);

                    if (rc == FILE_ALREADY_PLAYING) {
                        // Video is already on shown view. Loop.
                        shownView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                mp.seekTo(1);
                                mp.start();
                            }
                        });
                        break;
                    }

                    if (rc != SUCCESS) {
                        break;
                    }

                    // Switch the views
                    switchViews();

                    shownView.start(); // Play the loaded video
                    playingVideo = Integer.parseInt(msg.substring(2, msg.length())); // Save which video is playing

                    shownView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            mp.seekTo(1);
                            mp.start();
                        }
                    });

                    break;

                // Stop command
                case "ST":
                    shownView.pause();
                    shownView.seekTo(1);
                    shownView.setVisibility(View.INVISIBLE);
                    break;

                // Pause command
                case "PA":
                    shownView.pause();
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
                    }
                    break;

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
                    }
                    break;

                case "SE":

                    break;
                default:
                    out.println("ERROR: Command not recognised");
            }
        }
    }

    /**
     * Switches between the VideoViews, making one visible and the other invisible
     */
    void switchViews() {
        VideoView temp = null; // Temp VideoView used in switch of shown and hidden views
        shownView.stopPlayback();
        temp = shownView;
        shownView = hiddenView;
        hiddenView = temp;

        shownView.setVisibility(View.VISIBLE);
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
        try {
            fileNumber = Integer.parseInt(msg.substring(2, msg.length())); // Get file number
        } catch (NumberFormatException e) {
            out.println("ERROR: specify file number");
            return NO_FILE_NUMBER;
        }

        if (fileNumber == playingVideo) return FILE_ALREADY_PLAYING; // File is already on shown view
        if (fileNumber == loadedVideo) return SUCCESS; // Already loaded video

        String path = getFilePath(fileNumber); // Get file path
        if (path.equals("")) {
            out.println("ERROR: File can't be found");
            return FILE_NOT_FOUND;
        }

//        hiddenView.setOnPreparedListener( new MediaPlayer.OnPreparedListener() {
//            @Override
//            public void onPrepared(MediaPlayer mp) {
//                //use a global variable to get the object
//                mediaPlayer = mp;
//                mediaPlayer.get
//            }
//        });

        // Load the video to the hidden view
        hiddenView.setVideoPath(path);
        hiddenView.seekTo(1); // Buffer video by moving ahead 1ms
        loadedVideo = fileNumber;

//        // Get frame rate of loaded video
//        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
//        retriever.setDataSource(path);
//        loadedVideoFPS = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);

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
        // Loops through every file trying to find one that matches filenumber
        for (int i = 0; i < listOfFiles.length; i++) {
            // Remove file extenstion
            String file = listOfFiles[i].toString().substring(0, listOfFiles[i].toString().lastIndexOf('.'));
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
