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
}
