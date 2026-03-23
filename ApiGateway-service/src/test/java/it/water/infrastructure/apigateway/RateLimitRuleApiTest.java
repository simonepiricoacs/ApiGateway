package it.water.infrastructure.apigateway;

import it.water.infrastructure.apigateway.api.RateLimitRuleApi;
import it.water.infrastructure.apigateway.api.RateLimitRuleRepository;
import it.water.infrastructure.apigateway.api.RateLimitRuleSystemApi;
import it.water.infrastructure.apigateway.model.*;
import it.water.core.api.bundle.Runtime;
import it.water.core.api.model.PaginableResult;
import it.water.core.api.model.Role;
import it.water.core.api.permission.PermissionManager;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.role.RoleManager;
import it.water.core.api.service.Service;
import it.water.core.api.user.UserManager;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.permission.exceptions.UnauthorizedException;
import it.water.core.testing.utils.bundle.TestRuntimeInitializer;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import it.water.repository.entity.model.exceptions.DuplicateEntityException;
import it.water.repository.entity.model.exceptions.NoResultException;
import it.water.core.api.repository.query.Query;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Unit tests for RateLimitRule entity CRUD operations.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimitRuleApiTest implements Service {

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private RateLimitRuleApi rateLimitRuleApi;

    @Inject
    @Setter
    private Runtime runtime;

    @Inject
    @Setter
    private RateLimitRuleRepository rateLimitRuleRepository;

    @Inject
    @Setter
    private RateLimitRuleSystemApi rateLimitRuleSystemApi;

    @Inject
    @Setter
    private PermissionManager permissionManager;

    @Inject
    @Setter
    private UserManager userManager;

    @Inject
    @Setter
    private RoleManager roleManager;

    private it.water.core.api.model.User managerUser;
    private it.water.core.api.model.User viewerUser;
    private Role managerRole;
    private Role viewerRole;

    @BeforeAll
    void beforeAll() {
        managerRole = roleManager.getRole(Route.DEFAULT_MANAGER_ROLE);
        viewerRole = roleManager.getRole(Route.DEFAULT_VIEWER_ROLE);
        Assertions.assertNotNull(managerRole);
        Assertions.assertNotNull(viewerRole);

        managerUser = userManager.addUser("rlManager", "RL", "Manager", "rlmanager@test.com", "TempPassword1_", "salt", false);
        viewerUser = userManager.addUser("rlViewer", "RL", "Viewer", "rlviewer@test.com", "TempPassword1_", "salt", false);

        roleManager.addRole(managerUser.getId(), managerRole);
        roleManager.addRole(viewerUser.getId(), viewerRole);

        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    @Test
    @Order(1)
    void componentsInstantiatedCorrectly() {
        Assertions.assertNotNull(rateLimitRuleApi);
        Assertions.assertNotNull(componentRegistry.findComponent(RateLimitRuleSystemApi.class, null));
        Assertions.assertNotNull(rateLimitRuleRepository);
    }

    @Test
    @Order(2)
    void saveOk() {
        RateLimitRule rule = createRule("rule-1");
        rule = rateLimitRuleApi.save(rule);
        Assertions.assertEquals(1, rule.getEntityVersion());
        Assertions.assertTrue(rule.getId() > 0);
        Assertions.assertEquals("rule-1", rule.getRuleId());
    }

    @Test
    @Order(3)
    void updateShouldWork() {
        Query q = rateLimitRuleRepository.getQueryBuilderInstance().field("ruleId").equalTo("rule-1");
        RateLimitRule rule = rateLimitRuleApi.find(q);
        Assertions.assertNotNull(rule);
        rule.setMaxRequests(200);
        rule = rateLimitRuleApi.update(rule);
        Assertions.assertEquals(200, rule.getMaxRequests());
        Assertions.assertEquals(2, rule.getEntityVersion());
    }

    @Test
    @Order(4)
    void findAllShouldWork() {
        PaginableResult<RateLimitRule> all = rateLimitRuleApi.findAll(null, -1, -1, null);
        Assertions.assertFalse(all.getResults().isEmpty());
    }

    @Test
    @Order(5)
    void removeAllShouldWork() {
        PaginableResult<RateLimitRule> all = rateLimitRuleApi.findAll(null, -1, -1, null);
        all.getResults().forEach(r -> rateLimitRuleApi.remove(r.getId()));
        Assertions.assertEquals(0, rateLimitRuleApi.countAll(null));
    }

    @Test
    @Order(6)
    void saveShouldFailOnDuplicate() {
        rateLimitRuleApi.save(createRule("dup-rule"));
        RateLimitRule dup = createRule("dup-rule");
        Assertions.assertThrows(DuplicateEntityException.class, () -> rateLimitRuleApi.save(dup));
    }

    @Test
    @Order(7)
    void managerCanDoEverything() {
        TestRuntimeInitializer.getInstance().impersonate(managerUser, runtime);
        RateLimitRule rule = createRule("mgr-rule-100");
        RateLimitRule saved = Assertions.assertDoesNotThrow(() -> rateLimitRuleApi.save(rule));
        saved.setMaxRequests(500);
        Assertions.assertDoesNotThrow(() -> rateLimitRuleApi.update(saved));
        Assertions.assertDoesNotThrow(() -> rateLimitRuleApi.find(saved.getId()));
        Assertions.assertDoesNotThrow(() -> rateLimitRuleApi.remove(saved.getId()));
    }

    @Test
    @Order(8)
    void viewerCannotSaveOrUpdateOrRemove() {
        TestRuntimeInitializer.getInstance().impersonate(viewerUser, runtime);
        RateLimitRule rule = createRule("viewer-rule-200");
        Assertions.assertThrows(UnauthorizedException.class, () -> rateLimitRuleApi.save(rule));

        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        RateLimitRule systemRule = rateLimitRuleSystemApi.save(createRule("viewer-rule-201"));

        TestRuntimeInitializer.getInstance().impersonate(viewerUser, runtime);
        systemRule.setMaxRequests(1000);
        long id = systemRule.getId();
        Assertions.assertThrows(UnauthorizedException.class, () -> rateLimitRuleApi.update(systemRule));
        Assertions.assertThrows(NoResultException.class, () -> rateLimitRuleApi.remove(id));
    }

    @Test
    @Order(9)
    void repositoryFindByRuleIdShouldWork() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        rateLimitRuleApi.save(createRule("findme-rule"));
        RateLimitRule found = rateLimitRuleRepository.findByRuleId("findme-rule");
        Assertions.assertNotNull(found);
        Assertions.assertEquals("findme-rule", found.getRuleId());
    }

    @Test
    @Order(10)
    void repositoryFindByEnabledShouldWork() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        RateLimitRule disabledRule = createRule("disabled-rule");
        disabledRule.setEnabled(false);
        rateLimitRuleApi.save(disabledRule);

        var enabled = rateLimitRuleRepository.findByEnabled(true);
        var disabled = rateLimitRuleRepository.findByEnabled(false);
        Assertions.assertNotNull(enabled);
        Assertions.assertTrue(disabled.size() >= 1);
    }

    @Test
    @Order(11)
    void rateLimitRuleWithNullAlgorithmAndKeyTypeGetsDefaults() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        // Pass null for algorithm and keyType; @PrePersist must supply defaults
        RateLimitRule rule = new RateLimitRule("default-fields-rule", null, 10, 60, null);
        rule.setBurstCapacity(0); // force burstCapacity<=0 branch in @PrePersist
        RateLimitRule saved = rateLimitRuleSystemApi.save(rule);
        Assertions.assertNotNull(saved.getAlgorithm());
        Assertions.assertEquals(RateLimitAlgorithm.TOKEN_BUCKET, saved.getAlgorithm());
        Assertions.assertNotNull(saved.getKeyType());
        Assertions.assertEquals(RateLimitKeyType.CLIENT_IP, saved.getKeyType());
        Assertions.assertTrue(saved.getBurstCapacity() > 0, "burstCapacity must be set to maxRequests when <=0");
        Assertions.assertEquals(10, saved.getBurstCapacity());
        rateLimitRuleSystemApi.remove(saved.getId());
    }

    private RateLimitRule createRule(String ruleId) {
        return new RateLimitRule(ruleId, RateLimitKeyType.CLIENT_IP, 100, 60, RateLimitAlgorithm.TOKEN_BUCKET);
    }
}
