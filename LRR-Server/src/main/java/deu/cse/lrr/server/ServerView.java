package deu.cse.lrr.server;

public class ServerView {
    public void printLog(String message) {
        System.out.println("[서버] " + message);
    }
    
    public void printStartMessage(int port) {
        System.out.println("서버 시작됨. 포트 " + port + " 대기 중...");
    }
}
