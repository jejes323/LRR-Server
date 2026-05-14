/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package deu.cse.lrr.server;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author seo
 */
public class LRRServer {

    public static final int PORT = 9999;

    public static void main(String[] args) {
        ServerModel model = new ServerModel();
        ServerView view = new ServerView();
        ServerController controller = new ServerController(model, view, PORT);
        
        controller.startServer();
    }
}
