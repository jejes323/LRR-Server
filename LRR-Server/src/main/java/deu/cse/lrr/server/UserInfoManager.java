package deu.cse.lrr.server;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * UserInfo.yaml 파일에서 사용자 정보를 읽어 로그인 검증을 수행하는 매니저
 */
public class UserInfoManager {

    private static final Logger logger = Logger.getLogger(UserInfoManager.class.getName());
    private static final String YAML_PATH = "src/main/data/UserInfo.yaml";

    public enum UserRole {
        STUDENT, PROFESSOR, ASSISTANT, UNKNOWN
    }

    private Map<String, Object> data;

    public UserInfoManager() {
        loadYaml();
    }

    private void loadYaml() {
        try {
            File yamlFile = new File(YAML_PATH);
            try (InputStream is = new FileInputStream(yamlFile)) {
                Yaml yaml = new Yaml();
                data = yaml.load(is);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "UserInfo.yaml 파일을 읽는 중 오류 발생: " + e.getMessage(), e);
            data = null;
        }
    }

    public boolean login(String id, String password, UserRole role) {
        if (data == null) {
            System.out.println("[오류] 사용자 데이터를 불러오지 못했습니다.");
            return false;
        }

        String yamlKey = switch (role) {
            case STUDENT   -> "students";
            case PROFESSOR -> "professors";
            case ASSISTANT -> "assistants";
            default        -> null;
        };

        if (yamlKey == null) {
            System.out.println("[오류] 역할이 선택되지 않았습니다.");
            return false;
        }

        List<Map<String, String>> users = (List<Map<String, String>>) data.get(yamlKey);
        if (users == null) return false;

        for (Map<String, String> user : users) {
            if (id.equals(user.get("id")) && password.equals(user.get("password"))) {
                String roleName = switch (role) {
                    case STUDENT   -> "학생";
                    case PROFESSOR -> "교수";
                    case ASSISTANT -> "조교";
                    default        -> "알 수 없음";
                };
                System.out.println("[로그인 검증 성공] " + roleName + " 로그인 정보 일치. (이름: " + user.get("name") + ", ID: " + id + ")");
                return true;
            }
        }

        System.out.println("[로그인 실패] 아이디 또는 비밀번호가 일치하지 않습니다.");
        return false;
    }

    public enum DuplicateStatus {
        NONE, ID_DUPLICATE, NUMBER_DUPLICATE
    }

    /**
     * 전체 역할군에서 동일한 ID나 학번/교수번호가 있는지 확인하고 원인을 반환
     *
     * @param id 확인하려는 아이디
     * @param number 확인하려는 번호
     * @return 중복 상태 (NONE, ID_DUPLICATE, NUMBER_DUPLICATE)
     */
    public DuplicateStatus checkDuplicate(String id, String number) {
        if (data == null) return DuplicateStatus.NONE;

        String[] keys = {"students", "professors", "assistants"};
        for (String key : keys) {
            List<Map<String, String>> users = (List<Map<String, String>>) data.get(key);
            if (users != null) {
                for (Map<String, String> user : users) {
                    if (id.equals(user.get("id"))) {
                        return DuplicateStatus.ID_DUPLICATE;
                    }
                    if (number.equals(user.get("number"))) {
                        return DuplicateStatus.NUMBER_DUPLICATE;
                    }
                }
            }
        }
        return DuplicateStatus.NONE;
    }

    /**
     * 새로운 사용자를 등록하고 YAML 파일에 저장
     */
    public synchronized boolean register(String id, String password, String name, String number, UserRole role) {
        if (data == null) return false;

        String yamlKey = getYamlKey(role);
        if (yamlKey == null) return false;

        List<Map<String, Object>> users = (List<Map<String, Object>>) data.get(yamlKey);
        if (users == null) {
            users = new java.util.ArrayList<>();
            data.put(yamlKey, users);
        }

        // 새로운 사용자 데이터 생성
        Map<String, Object> newUser = new java.util.HashMap<>();
        newUser.put("id", id);
        newUser.put("password", password);
        newUser.put("name", name);
        newUser.put("number", number); // 학번/교수번호 저장

        users.add(newUser);

        return saveYaml();
    }

    private boolean saveYaml() {
        try {
            File yamlFile = new File(YAML_PATH);
            
            org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
            options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            
            try (java.io.FileWriter writer = new java.io.FileWriter(yamlFile)) {
                yaml.dump(data, writer);
                return true;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "UserInfo.yaml 파일을 저장하는 중 오류 발생: " + e.getMessage(), e);
            return false;
        }
    }

    private String getYamlKey(UserRole role) {
        return switch (role) {
            case STUDENT -> "students";
            case PROFESSOR -> "professors";
            case ASSISTANT -> "assistants";
            default -> null;
        };
    }
}
