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
    // 사용자 예약 내역 통합 조회
    // ----------------------------------------------------------------
    public String getReservationList(String userId) {
        List<Map<String, Object>> allUserReservations = new ArrayList<>();
        
        String[] paths = { REQUESTS_PATH, CLASSROOM_PATH, LAB_PATH };
        for (String path : paths) {
            Map<String, Object> data = loadYaml(path);
            List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("requests");
            if (list == null) list = (List<Map<String, Object>>) data.get("reservations");
            
            if (list != null) {
                for (Map<String, Object> req : list) {
                    if (userId.equals(req.get("userId"))) {
                        allUserReservations.add(req);
                    }
                }
            }
        }
        
        if (allUserReservations.isEmpty()) {
            return "NO_RESERVATIONS";
        }

        StringBuilder sb = new StringBuilder("RESERVATION_LIST:");
        for (int i = 0; i < allUserReservations.size(); i++) {
            Map<String, Object> res = allUserReservations.get(i);

            // YAML에는 time 필드 없이 startTime/endTime이 별도 저장됨
            String time;
            if (res.containsKey("time")) {
                time = String.valueOf(res.get("time"));
            } else {
                String start = String.valueOf(res.getOrDefault("startTime", ""));
                String end   = String.valueOf(res.getOrDefault("endTime", ""));
                time = start + " - " + end;
            }

            // 영문 status → 한국어 변환 (클라이언트 색상 분기용)
            String statusRaw = String.valueOf(res.getOrDefault("status", ""));
            String status = switch (statusRaw) {
                case "PENDING"  -> "승인 대기";
                case "APPROVED" -> "승인 완료";
                case "REJECTED" -> "승인 거부";
                default         -> statusRaw;
            };

            sb.append(res.get("room")).append("|")
              .append(res.get("date")).append("|")
              .append(time).append("|")
              .append(res.get("purpose")).append("|")
              .append(status);

            if (i < allUserReservations.size() - 1) {
                sb.append(";");
            }
        }
        return sb.toString();
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

    public String getAllRequestsList(UserInfoManager userInfoManager) {
        Map<String, Object> data = loadYaml(REQUESTS_PATH);
        List<Map<String, Object>> requests = (List<Map<String, Object>>) data.get("requests");
        if (requests == null || requests.isEmpty()) {
            return "NO_REQUESTS";
        }
        
        StringBuilder sb = new StringBuilder("ALL_RESERVATIONS:");
        for (int i = 0; i < requests.size(); i++) {
            Map<String, Object> req = requests.get(i);
            String userId = String.valueOf(req.getOrDefault("userId", ""));
            
            // UserInfo에서 이름과 역할(한글) 가져오기
            String[] userDetails = userInfoManager.getUserDetails(userId);
            String name = userDetails[0];
            String role = userDetails[1];
            
            String room = String.valueOf(req.getOrDefault("room", ""));
            String date = String.valueOf(req.getOrDefault("date", ""));
            String startTime = String.valueOf(req.getOrDefault("startTime", ""));
            String endTime = String.valueOf(req.getOrDefault("endTime", ""));
            String time = startTime + " - " + endTime;
            
            String statusRaw = String.valueOf(req.getOrDefault("status", "PENDING"));
            String status = switch (statusRaw) {
                case "PENDING"  -> "승인 대기";
                case "APPROVED" -> "승인 완료";
                case "REJECTED" -> "승인 거부";
                default         -> statusRaw;
            };
            
            sb.append(role).append("|")
              .append(userId).append("|")
              .append(name).append("|")
              .append(room).append("|")
              .append(date).append("|")
              .append(time).append("|")
              .append(status);
              
            if (i < requests.size() - 1) {
                sb.append(";");
            }
        }
        return sb.toString();
    }

    public synchronized boolean updateRequestStatus(String userId, String room, String date, String startTime, String endTime, String newStatus) {
        try {
            Map<String, Object> data = loadYaml(REQUESTS_PATH);
            List<Map<String, Object>> requests = (List<Map<String, Object>>) data.get("requests");
            if (requests == null) return false;
            
            boolean updated = false;
            for (Map<String, Object> req : requests) {
                String reqUser = String.valueOf(req.get("userId"));
                String reqRoom = String.valueOf(req.get("room"));
                String reqDate = String.valueOf(req.get("date"));
                String reqStart = String.valueOf(req.get("startTime"));
                String reqEnd = String.valueOf(req.get("endTime"));
                
                if (userId.equals(reqUser) && room.equals(reqRoom) && date.equals(reqDate) && startTime.equals(reqStart) && endTime.equals(reqEnd)) {
                    req.put("status", newStatus);
                    updated = true;
                    break;
                }
            }
            
            if (updated) {
                return saveYaml(REQUESTS_PATH, data);
            }
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "예약 상태 업데이트 실패: " + e.getMessage(), e);
            return false;
        }
    }
}


