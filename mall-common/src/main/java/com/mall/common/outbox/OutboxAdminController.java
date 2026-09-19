package com.mall.common.outbox;

import com.mall.common.web.Auths;
import com.model.bean.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 已放弃（status=3）outbox 事件的查看与重投。管理员专用。
 *
 * ── 为什么这个控制器在 mall-common 里却能被扫到 ──────────────────────────
 * 各服务的 {@code @SpringBootApplication} 只扫自己的包，com.mall.common **不在**扫描路径内，
 * 所以本类**不是**被组件扫描注册的，而是在 {@link OutboxConfig} 里以 {@code @Bean} 显式注册。
 * 而 {@code RequestMappingHandlerMapping} 判断一个 Bean 能否处理请求时只看它的类型上
 * **有没有** {@code @Controller}/{@code @RequestMapping}，不管它是怎么进容器的——
 * 所以注释在类上，注册在配置里，两者缺一不可：
 * <ul>
 *   <li>漏掉类上的 {@code @RestController} → **静默**没有映射（编译不报错，启动不报错，只有 404）；</li>
 *   <li>漏掉 OutboxConfig 里的 {@code @Bean} → 类根本不在容器里。</li>
 * </ul>
 *
 * 放到 OutboxConfig 里注册还有个额外好处：**恰好**只有带 outbox 表的三个服务
 * （order / payment / inventory）导入了那个配置，于是「有 outbox 表 ⇒ 就有本管理端」自动成立，
 * 无需在三个启动类上各加一行 @Import（也就不会漏掉某一个，比如 payment 本没有任何管理员控制器）。
 *
 * ── 路径 ───────────────────────────────────────────────────────────────
 * 不加前缀，与 {@code OrderAdminController}（/admin/refunds）等既有管理员控制器一致；
 * 经网关的 StripPrefix=1 后对外分别是
 * {@code /order/admin/outbox/**}、{@code /pay/admin/outbox/**}、{@code /inventory/admin/outbox/**}。
 */
@RestController
public class OutboxAdminController {

    /** 单次操作的上限。有界是刻意的：一次调用不该把整个积压翻过来冲垮 broker */
    private static final int MAX_LIMIT = 200;

    /** 缺省与 relay 的批大小一致（OutboxServiceImpl.BATCH），重投后一轮 relay 即可消化 */
    private static final int DEFAULT_LIMIT = 50;

    private final OutboxService outboxService;

    public OutboxAdminController(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    /**
     * 查看已放弃的事件。routingKey 先与队列绑定核对——那才是它们被放弃的根因，
     * 不修好绑定就重投，只会让它们再攒满上限被放弃一次。
     */
    @GetMapping("/admin/outbox/abandoned")
    public Result<List<AbandonedOutbox>> abandoned(@RequestParam(required = false) Integer limit) {
        // 网关只校验 JWT 与登录态，**不做角色校验**；下游据此判断身份是唯一的角色闸门，
        // 所以每个 handler 都必须自己调，不能省。
        Auths.requireAdmin();
        return Result.success(outboxService.listAbandoned(clamp(limit)));
    }

    /** 重投已放弃的事件，返回本次实际重投条数（重复调用返回 0，天然幂等） */
    @PostMapping("/admin/outbox/requeue")
    public Result<Integer> requeue(@RequestParam(required = false) Integer limit) {
        Auths.requireAdmin();
        return Result.success(outboxService.requeueAbandoned(clamp(limit)));
    }

    private int clamp(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
