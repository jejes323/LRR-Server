package deu.cse.lrr.server;

import org.yaml.snakeyaml.Yaml;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReservationManager {
    private static final String YAML_PATH = "src/main/data/ReservationList.yaml";
    private Map<String, List<Map<String, String>>> reservationData = new java.util.HashMap<>();

    public ReservationManager() {
        loadReservations();
    }

    @SuppressWarnings("unchecked")
    public void loadReservations() {
        try (InputStream inputStream = new FileInputStream(YAML_PATH)) {
            Yaml yaml = new Yaml();
            Map<String, List<Map<String, String>>> loadedData = yaml.load(inputStream);
            if (loadedData != null) {
                reservationData = loadedData;
            }
        } catch (Exception e) {
            System.err.println("YAML 로드 실패: " + e.getMessage());
        }
    }

    public String getReservationList(String userId) {
        if (reservationData == null || !reservationData.containsKey(userId)) {
            return "NO_RESERVATIONS";
        }

        List<Map<String, String>> userReservations = reservationData.get(userId);
        StringBuilder sb = new StringBuilder("RESERVATION_LIST:");
        
        for (int i = 0; i < userReservations.size(); i++) {
            Map<String, String> res = userReservations.get(i);
            sb.append(res.get("room")).append("|")
              .append(res.get("date")).append("|")
              .append(res.get("time")).append("|")
              .append(res.get("purpose")).append("|")
              .append(res.get("status"));
            
            if (i < userReservations.size() - 1) {
                sb.append(";");
            }
        }
        return sb.toString();
    }
}
