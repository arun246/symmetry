package io.symmetry.api;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.symmetry.identity.Actor;
import io.symmetry.identity.CurrentUserService;
import io.symmetry.settings.ConfigScope;
import io.symmetry.settings.SettingService;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/me")
@PreAuthorize("isAuthenticated()")
public class MeController {

    private final CurrentUserService currentUser;
    private final SettingService settings;

    public MeController(CurrentUserService currentUser, SettingService settings) {
        this.currentUser = currentUser;
        this.settings = settings;
    }

    @GetMapping
    public MeView me() {
        Actor actor = currentUser.require();
        return new MeView(actor.id(), actor.email(), actor.name(), actor.role().name(),
                actor.orgId(), actor.orgSlug(), settings(actor));
    }

    @GetMapping("/settings")
    public Map<String, JsonNode> settings() {
        return settings(currentUser.require());
    }

    @PatchMapping("/settings")
    public Map<String, JsonNode> update(@RequestBody Map<String, JsonNode> values) {
        Actor actor = currentUser.require();
        values.forEach((key, value) -> settings.set(ConfigScope.USER, actor.id(), key, value, actor));
        return settings(actor);
    }

    private Map<String, JsonNode> settings(Actor actor) {
        return settings.effective(actor.id(), null, actor.orgId());
    }
}
