package deu.cse.lrr.server;

import java.io.*;
import java.net.*;

public class ServerController {
    private final ServerModel model;
    private final ServerView view;
    private final int port;

    public ServerController(ServerModel model, ServerView view, int port) {
        this.model = model;
        this.view = view;
        this.port = port;
    }

    public void startServer() {
        view.printStartMessage(port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                view.printLog("클라이언트 접속: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler extends Thread {
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
                    view.printLog("수신: " + msg);
                    if (msg.startsWith("LOGIN:")) {
                        handleLogin(msg, out);
                    } else if (msg.startsWith("REGISTER:")) {
                        handleRegister(msg, out);
                    } else if (msg.startsWith("GET_RESERVATION_LIST:")) {
                        handleGetReservationList(msg, out);
                    } else if (msg.startsWith("RESERVATION_REQUEST:")) {
                        handleReservationRequest(msg, out);
                    }
                }
            } catch (IOException e) {
                view.printLog("클라이언트 연결 종료 또는 에러");
            } finally {
                if (loggedInId != null) {
                    model.logoutUser(loggedInId);
                    view.printLog("클라이언트 연결 종료로 인한 로그아웃: " + loggedInId);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleLogin(String msg, BufferedWriter out) throws IOException {
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

                if (model.isUserLoggedIn(id)) {
                    view.printLog("로그인 실패 (이미 접속 중): " + id);
                    out.write("ALREADY_LOGGED_IN\n");
                } else {
                    String name = model.loginUser(id, pwd, userRole);
                    if (name != null) {
                        this.loggedInId = id;
                        view.printLog("로그인 승인: " + id + " (" + role + ")");
                        out.write("LOGIN_SUCCESS," + name + "\n");
                    } else {
                        view.printLog("로그인 실패 (정보 불일치): " + id + " (" + role + ")");
                        out.write("LOGIN_FAIL\n");
                    }
                }
            } else {
                out.write("LOGIN_FAIL\n");
            }
            out.flush();
        }

        private void handleRegister(String msg, BufferedWriter out) throws IOException {
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

                UserInfoManager.DuplicateStatus dupStatus = model.checkDuplicate(id, number);
                if (dupStatus == UserInfoManager.DuplicateStatus.ID_DUPLICATE) {
                    out.write("ALREADY_EXISTS_ID\n");
                } else if (dupStatus == UserInfoManager.DuplicateStatus.NUMBER_DUPLICATE) {
                    out.write("ALREADY_EXISTS_NUMBER\n");
                } else if (model.registerUser(id, pwd, name, number, role)) {
                    out.write("REGISTER_SUCCESS\n");
                } else {
                    out.write("REGISTER_FAIL\n");
                }
            } else {
                out.write("REGISTER_FAIL\n");
            }
            out.flush();
        }

        private void handleGetReservationList(String msg, BufferedWriter out) throws IOException {
            String userId = msg.substring(21);
            view.printLog("내 예약 조회 요청: " + userId);
            
            // 승인, 대기 예약 모두 포함하는 새로운 매니저 호출
            String response = model.getReservationRequestManager().getReservationList(userId);
            
            out.write(response + "\n");
            out.flush();
        }

        private void handleReservationRequest(String msg, BufferedWriter out) throws IOException {
            // 포맷: RESERVATION_REQUEST:userId|type|room|date|startTime|endTime|purpose|people
            String payload = msg.substring(20);
            String[] parts = payload.split("\\|");

            if (parts.length < 8) {
                view.printLog("예약 신청 파싱 실패: " + msg);
                out.write("RESERVATION_REQUEST_FAIL\n");
                out.flush();
                return;
            }

            String userId    = parts[0];
            String type      = parts[1];
            String room      = parts[2];
            String date      = parts[3];
            String startTime = parts[4];
            String endTime   = parts[5];
            String purpose   = parts[6];
            String people    = parts[7];

            view.printLog("예약 신청 수신: " + userId + " / " + type + " / " + room + " / " + date);

            String result = model.saveReservationRequest(userId, type, room, date, startTime, endTime, purpose, people);

            switch (result) {
                case "SUCCESS" -> {
                    view.printLog("예약 신청 저장 완료: " + userId);
                    out.write("RESERVATION_REQUEST_SUCCESS\n");
                }
                case "DUPLICATE" -> {
                    view.printLog("예약 신청 중복: " + userId);
                    out.write("RESERVATION_REQUEST_DUPLICATE\n");
                }
                default -> {
                    view.printLog("예약 신청 저장 실패: " + userId);
                    out.write("RESERVATION_REQUEST_FAIL\n");
                }
            }
            out.flush();
        }
    }
}
