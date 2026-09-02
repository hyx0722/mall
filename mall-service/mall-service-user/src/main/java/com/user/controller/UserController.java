package com.user.controller;



import com.model.bean.Result;
import com.model.bean.User;
import com.model.util.ThreadLocalUtil;
import com.user.bean.UserAddress;
import com.user.bean.UserUpdateDTO;
import com.user.service.UserService;
import com.user.util.JwtUtil;
import jakarta.validation.constraints.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Slf4j
@RestController
@Validated
public class UserController {

    @Autowired
    UserService userService;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JwtUtil jwtUtil;

    //注册
    @PostMapping("/register")
    public Result register(@Pattern(regexp = "^\\S{5,16}$") @RequestParam("username") String username,
                           @RequestParam("password") @Pattern(regexp = "^\\S{5,16}$") String password){
        //以数据库为准判断用户是否已注册（唯一键 uk_username 兜底并发重复注册）
        if (userService.findPasswordByUsername(username) != null) {
            return Result.error("注册失败用户已经存在");
        }
        userService.registerInsert(username, password);
        return Result.success();
    }

    //登录
    @PostMapping("/login")
    public Result<String> login(@Pattern(regexp = "^\\S{5,16}$") @RequestParam("username") String username,
                                @Pattern(regexp = "^\\S{5,16}$") @RequestParam("password") String password) {
        //以数据库为准查询用户（布隆过滤器未预热/误报时可能拒绝已存在用户，不能作为登录门槛）
        User loginUser = userService.findIdAndPasswordByUsername(username);
        if (loginUser == null) {
            return Result.error("该用户不存在");
        }
        //禁用账号拒绝登录
        if (loginUser.getStatus() != null && loginUser.getStatus() == 0) {
            return Result.error("该账号已被禁用，请联系管理员");
        }
        //判断密码是否正确  loginUser对象中的password是密文
        if (passwordEncoder.matches(password, loginUser.getPassword())) {
            log.info("存在该用户并且密码正确");
            //登录成功
            Map<String, Object> claims = new HashMap<>();
            claims.put("id", loginUser.getId());
            claims.put("username", loginUser.getUsername());
            // 角色：注册默认普通用户 1；管理员 2（用于网关注入 X-User-Role / 后台鉴权）
            int role = loginUser.getRole() == null ? 1 : loginUser.getRole();
            claims.put("role", role);
            String token = jwtUtil.genToken(claims);
            // 存入 Redis（键为 login:token:{id}，用于主动失效或单设备登录）
            stringRedisTemplate.opsForValue().set(
                    "login:token:" + loginUser.getId(),
                    token,
                    1,
                    TimeUnit.HOURS
            );
            return Result.success(token);
        }
        return Result.error("密码错误");
    }

    //查看登录的用户的详细资料
    //只看username,email,phone,avatar,status
    @GetMapping("/userInfo")
    public Result<User> userInfo() {
        Map<String, Object> map = ThreadLocalUtil.get();
        String username = (String) map.get("username");
        User user = userService.findUserByUsername(username);
        return Result.success(user);
    }

    //查看其他的用户的详细资料
    //只看名字，头像和状态
    @GetMapping("/ortherUser")
    public Result<User> otherUser(@Pattern(regexp = "^\\S{5,16}$") @RequestParam("username") String username) {
        User user = userService.findOtherUserByUsername(username);
        return Result.success(user);
    }


    //删除当前用户
    @DeleteMapping("/delete")
    public Result<String> delete() {
        Map<String, Object> map = ThreadLocalUtil.get();
        String username = (String) map.get("username");
        Long id = (Long) map.get("id");
        userService.delete(username);
        //清理该用户的登录token，使其立即失效
        stringRedisTemplate.delete("login:token:" + id);
        return Result.success("该用户删除.");
    }



    //更新手机号和邮箱（只允许更新当前登录用户自己的信息，id 取自登录态防止越权）
    @PutMapping("/update")
    public Result update(@RequestBody @Validated UserUpdateDTO dto) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Long id = (Long) map.get("id");
        userService.update(id, dto.getPhone(), dto.getEmail());
        return Result.success();
    }

    @PatchMapping("updateAvatar")
    public Result updateAvatar(@RequestParam("avatar") @URL String avatar) {
        userService.updateAvatar(avatar);
        return Result.success();
    }

    //更换密码
    @PatchMapping("/updatePwd")
    public Result updatePwd(@RequestBody Map<String, String> params) {
        //1.校验参数
        String oldPwd = params.get("old_pwd");
        String newPwd = params.get("new_pwd");
        String rePwd = params.get("re_pwd");
        if (!StringUtils.hasLength(oldPwd) || !StringUtils.hasLength(newPwd) || !StringUtils.hasLength(rePwd)) {
            return Result.error("缺少必要的参数");
        }
        //新密码格式校验
        if (!newPwd.matches("^\\S{5,16}$")) {
            return Result.error("新密码长度必须在5-16位且不能包含空格");
        }
        if (newPwd.equals(oldPwd)) {
            return Result.error("新密码不能与旧密码相同");
        }
        //原密码是否正确（matches(明文, 密文)）
        Map<String, Object> map = ThreadLocalUtil.get();
        String username = (String) map.get("username");
        Long id = (Long) map.get("id");
        String userPassword = userService.findPasswordByUsername(username);
        if (!passwordEncoder.matches(oldPwd, userPassword)) {
            return Result.error("原密码填写不正确");
        }
        //newPwd和rePwd是否一样
        if (!rePwd.equals(newPwd)) {
            return Result.error("两次填写的新密码不一样");
        }
        //2.调用service完成密码更新
        userService.updatePwd(newPwd);
        //3.删除该用户当前登录token，强制重新登录
        stringRedisTemplate.delete("login:token:" + id);
        return Result.success();
    }


    //添加收货人的信息
    @PostMapping("/addReceiverDetail")
    public Result addReceiverDetail(@RequestBody @Validated UserAddress userAddress){
        Map<String, Object> map = ThreadLocalUtil.get();
        Long userId= (Long) map.get("id");
        userAddress.setUserId(userId);
        userService.addReceiverDetail(userAddress);
        return Result.success();
    }



}
