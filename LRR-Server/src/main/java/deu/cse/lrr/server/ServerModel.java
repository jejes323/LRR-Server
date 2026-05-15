package deu.cse.lrr.server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerModel {
    private final UserInfoManager userInfoManager;
    private final ReservationManager reservationManager;
    private final Set<String> loggedInUsers;

    public ServerModel() {
        userInfoManager = new UserInfoManager();
        reservationManager = new ReservationManager();
        loggedInUsers = ConcurrentHashMap.newKeySet();
    }

    public ReservationManager getReservationManager() {
        return reservationManager;
    }

    public boolean isUserLoggedIn(String id) {
        return loggedInUsers.contains(id);
    }

    public String loginUser(String id, String pwd, UserInfoManager.UserRole role) {
        String name = userInfoManager.login(id, pwd, role);
        if (name != null) {
            loggedInUsers.add(id);
            return name;
        }
        return null;
    }

    public void logoutUser(String id) {
        if (id != null) {
            loggedInUsers.remove(id);
        }
    }

    public UserInfoManager.DuplicateStatus checkDuplicate(String id, String number) {
        return userInfoManager.checkDuplicate(id, number);
    }

    public boolean registerUser(String id, String password, String name, String number, UserInfoManager.UserRole role) {
        return userInfoManager.register(id, password, name, number, role);
    }
}
