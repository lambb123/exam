package com.exam.backend.controller;

import com.exam.backend.common.DbSwitchContext;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@CrossOrigin(origins = "*")
public class SystemController {

    // 获取当前主库
    @GetMapping("/db-mode")
    public Map<String, Object> getDbMode() {
        Map<String, Object> map = new HashMap<>();
        map.put("code", 200);
        map.put("data", DbSwitchContext.getCurrentMasterDb());
        return map;
    }

    // 切换主库
    @PostMapping("/db-mode")
    public Map<String, Object> setDbMode(@RequestParam String mode) {
        DbSwitchContext.setCurrentMasterDb(mode);
        Map<String, Object> map = new HashMap<>();
        map.put("code", 200);
        map.put("msg", "主写入库已切换为: " + mode);
        return map;
    }
}