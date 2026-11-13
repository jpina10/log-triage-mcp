package dev.logtriage.demo.common;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * In-memory fault flags, toggled by {@code demo/scripts/fault.sh} through
 * {@code /admin/faults}. Deliberately silent: turning a fault on writes no
 * log line, so the agent has to find the cause from symptoms alone.
 */
@Component
public class FaultSwitches {

    private final Set<String> enabled = ConcurrentHashMap.newKeySet();

    public boolean isOn(String fault) {
        return enabled.contains(fault);
    }

    Set<String> enabled() {
        return Set.copyOf(enabled);
    }

    void set(String fault, boolean on) {
        if (on) {
            enabled.add(fault);
        } else {
            enabled.remove(fault);
        }
    }

    @RestController
    @RequestMapping("/admin/faults")
    static class Admin {

        private final FaultSwitches switches;

        Admin(FaultSwitches switches) {
            this.switches = switches;
        }

        @GetMapping
        Map<String, Set<String>> list() {
            return Map.of("enabled", switches.enabled());
        }

        @PutMapping("/{fault}")
        ResponseEntity<Void> enable(@PathVariable String fault) {
            switches.set(fault, true);
            return ResponseEntity.noContent().build();
        }

        @DeleteMapping("/{fault}")
        ResponseEntity<Void> disable(@PathVariable String fault) {
            switches.set(fault, false);
            return ResponseEntity.noContent().build();
        }
    }
}
