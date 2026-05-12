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
    private static final UserInfoManager userInfoManager = new UserInfoManager();
    private static final Set<String> loggedInUsers = ConcurrentHashMap.newKeySet();


    public static void main(String[] args) {
        System.out.println("서버 시작됨. 포트 " + PORT + " 대기 중...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[서버] 클라이언트 접속: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private String loggedInId = null;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println("[서버] 수신: " + msg);
                    if (msg.startsWith("LOGIN:")) {
                        // msg format: LOGIN:id,password,role
                        String[] parts = msg.substring(6).split(",");
                        if (parts.length >= 3) {
                            String id = parts[0];
                            String pwd = parts[1];
                            String role = parts[2];

                            UserInfoManager.UserRole userRole = switch (role) {
                                case "STUDENT" -> UserInfoManager.UserRole.STUDENT;
                                case "PROFESSOR" -> UserInfoManager.UserRole.PROFESSOR;
                                case "ASSISTANT" -> UserInfoManager.UserRole.ASSISTANT;
                                default -> UserInfoManager.UserRole.UNKNOWN;
                            };

                            if (loggedInUsers.contains(id)) {
                                System.out.println("[서버] 로그인 실패 (이미 접속 중): " + id);
                                out.write("ALREADY_LOGGED_IN\n");
                            } else if (userInfoManager.login(id, pwd, userRole)) {
                                loggedInUsers.add(id);
                                this.loggedInId = id;
                                System.out.println("[서버] 로그인 승인: " + id + " (" + role + ")");
                                out.write("LOGIN_SUCCESS\n");
                            } else {
                                System.out.println("[서버] 로그인 실패 (정보 불일치): " + id + " (" + role + ")");
                                out.write("LOGIN_FAIL\n");
                            }
                            out.flush();
                        } else {
                            out.write("LOGIN_FAIL\n");
                            out.flush();
                        }
                    } else if (msg.startsWith("REGISTER:")) {
                        // msg format: REGISTER:id,password,name,number,role
                        String[] parts = msg.substring(9).split(",");
                        if (parts.length >= 5) {
                            String id = parts[0];
                            String pwd = parts[1];
                            String name = parts[2];
                            String number = parts[3];
                            String roleStr = parts[4];

                            UserInfoManager.UserRole role = switch (roleStr) {
                                case "STUDENT" -> UserInfoManager.UserRole.STUDENT;
                                case "PROFESSOR" -> UserInfoManager.UserRole.PROFESSOR;
                                case "ASSISTANT" -> UserInfoManager.UserRole.ASSISTANT;
                                default -> UserInfoManager.UserRole.UNKNOWN;
                            };

                            UserInfoManager.DuplicateStatus dupStatus = userInfoManager.checkDuplicate(id, number);
                            if (dupStatus == UserInfoManager.DuplicateStatus.ID_DUPLICATE) {
                                out.write("ALREADY_EXISTS_ID\n");
                            } else if (dupStatus == UserInfoManager.DuplicateStatus.NUMBER_DUPLICATE) {
                                out.write("ALREADY_EXISTS_NUMBER\n");
                            } else if (userInfoManager.register(id, pwd, name, number, role)) {
                                out.write("REGISTER_SUCCESS\n");
                            } else {
                                out.write("REGISTER_FAIL\n");
                            }
                        } else {
                            out.write("REGISTER_FAIL\n");
                        }
                        out.flush();
                    }
                }
            } catch (IOException e) {
                System.out.println("[서버] 클라이언트 연결 종료 또는 에러");
            } finally {
                if (loggedInId != null) {
                    loggedInUsers.remove(loggedInId);
                    System.out.println("[서버] 클라이언트 연결 종료로 인한 로그아웃: " + loggedInId);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
