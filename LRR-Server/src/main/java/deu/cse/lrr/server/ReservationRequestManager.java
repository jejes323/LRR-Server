package deu.cse.lrr.server;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 예약 신청 데이터를 YAML 파일로 관리하는 매니저
 * - reservation-requests.yaml : 대기 중인 신청 (PENDING)
 * - classroom-reservations.yaml : 승인된 강의실 예약
 * - lab-reservations.yaml : 승인된 실습실 예약
 */
public class ReservationRequestManager {

    private static final Logger logger = Logger.getLogger(ReservationRequestManager.class.getName());

    private static final String BASE_DIR = System.getProperty("data.dir", "src/main/data");
    private static final String REQUESTS_PATH  = BASE_DIR + "/reservation-requests.yaml";
    private static final String CLASSROOM_PATH = BASE_DIR + "/classroom-reservations.yaml";
    private static final String LAB_PATH       = BASE_DIR + "/lab-reservations.yaml";

    // ----------------------------------------------------------------
    // 예약 신청 저장 (PENDING)
    // ----------------------------------------------------------------

    /**
     * 예약 신청 데이터를 reservation-requests.yaml 에 PENDING 상태로 저장
     *
     * @param userId    신청자 ID
     * @param type      "CLASSROOM" 또는 "LAB"
     * @param room      강의실/실습실 호수
     * @param date      예약 날짜 (yyyy-MM-dd)
     * @param startTime 시작 시간
     * @param endTime   종료 시간
     * @param purpose   사용 목적
     * @param people    사용 인원
     * @return 저장 성공 여부
     */
    public synchronized String saveRequest(String userId, String type, String room,
                                            String date, String startTime, String endTime,
                                            String purpose, String people) {
        try {
            Map<String, Object> data = loadYaml(REQUESTS_PATH);

            List<Map<String, Object>> requests = (List<Map<String, Object>>) data.get("requests");
            if (requests == null) {
                requests = new ArrayList<>();
                data.put("requests", requests);
            }

            // 분 단위 정수로 변환 (중복 체크용)
            int newStart = toMinutes(startTime);
            int newEnd   = toMinutes(endTime);

            // 중복 체크: 같은 방, 같은 날짜, 시간대 겹침 여부
            for (Map<String, Object> existing : requests) {
                String exDate = String.valueOf(existing.get("date"));
                if (!date.equals(exDate)) continue;
                if (!room.equals(String.valueOf(existing.get("room")))) continue;
                if (!type.equals(String.valueOf(existing.get("type")))) continue;

                int exStart = toMinutes(existing.get("startTime"));
                int exEnd   = toMinutes(existing.get("endTime"));

                if (newStart < exEnd && newEnd > exStart) {
                    logger.warning("중복 예약 신청 감지: " + room + " / " + date + " / " + startTime + "~" + endTime);
                    return "DUPLICATE";
                }
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId",    userId);
            entry.put("type",      type);
            entry.put("room",      room);
            entry.put("date",      date);      // yyyy-MM-dd 문자열 그대로 저장
            entry.put("startTime", startTime); // HH:mm 문자열 그대로 저장
            entry.put("endTime",   endTime);   // HH:mm 문자열 그대로 저장
            entry.put("purpose",   purpose);
            entry.put("people",    people);
            entry.put("status",    "PENDING");

            requests.add(entry);
            return saveYaml(REQUESTS_PATH, data) ? "SUCCESS" : "FAIL";

        } catch (Exception e) {
            logger.log(Level.SEVERE, "예약 신청 저장 실패: " + e.getMessage(), e);
            return "FAIL";
        }
    }

    // "09:00" 혹은 정수(540)를 분 단위 정수로 변환
    private int toMinutes(Object rawValue) {
        if (rawValue == null) return -1;
        if (rawValue instanceof Integer) return (Integer) rawValue;
        String time = rawValue.toString();
        try {
            if (time.contains(":")) {
                String[] parts = time.split(":");
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
            return Integer.parseInt(time);
        } catch (Exception e) {
            return -1;
        }
    }


    // ----------------------------------------------------------------
    // YAML 공통 유틸
    // ----------------------------------------------------------------

    private Map<String, Object> loadYaml(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return new LinkedHashMap<>();
        }
        try (InputStream is = new FileInputStream(file)) {
            Yaml yaml = new Yaml();
            Map<String, Object> loaded = yaml.load(is);
            return (loaded != null) ? loaded : new LinkedHashMap<>();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "YAML 로드 실패 (" + path + "): " + e.getMessage(), e);
            return new LinkedHashMap<>();
        }
    }

    private boolean saveYaml(String path, Map<String, Object> data) {
        try {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);

            try (FileWriter writer = new FileWriter(path)) {
                yaml.dump(data, writer);
                return true;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "YAML 저장 실패 (" + path + "): " + e.getMessage(), e);
            return false;
        }
    }
}
