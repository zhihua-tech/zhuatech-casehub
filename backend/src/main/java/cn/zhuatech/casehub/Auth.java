/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.casehub;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import java.time.Instant;
import java.sql.Timestamp;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import static cn.zhuatech.casehub.Model.*;
@Service @org.springframework.core.annotation.Order(0) public class Auth implements ApplicationRunner {
 final JdbcTemplate db; final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(10);
 final Map<String,String> passwords;final boolean demo;
 public Auth(JdbcTemplate db,@Value("${app.admin-password}") String admin,@Value("${app.reviewer-password}") String reviewer,
 @Value("${app.operator-password}") String operator,@Value("${app.viewer-password}") String viewer,@Value("${app.demo:false}") boolean demo){
  this.db=db;this.demo=demo;passwords=Map.of("admin",admin,"reviewer",reviewer,"operator",operator,"viewer",viewer);
 }
 public static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public void run(org.springframework.boot.ApplicationArguments args){
  for(var p:passwords.values())if(p.length()<12 || p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || (!demo&&p.contains("Demo")))throw new IllegalStateException("配置独立的 12 位以上初始化密码，生产禁用演示密码");
  if(db.queryForObject("SELECT COUNT(*) FROM tenant_guard WHERE tenant='default'",Integer.class)==0)db.update("INSERT INTO tenant_guard VALUES('default')");
  for(var entry:passwords.entrySet()) if(db.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=?",Integer.class,entry.getKey())==0)
   db.update("INSERT INTO app_user(id,tenant,username,password_hash,role,active) VALUES(?,?,?,?,?,true)",UUID.randomUUID().toString(),"default",entry.getKey(),encoder.encode(entry.getValue()),entry.getKey().toUpperCase(Locale.ROOT));
 }
 public Map<String,Object> login(String name,String password){
  if(name==null||password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new Failure(401,"账号或密码错误");
  var rows=db.queryForList("SELECT * FROM app_user WHERE username=?",name);
  if(rows.isEmpty()){encoder.matches(password,"$2a$10$abcdefghijklmnopqrstuu8K.f.a7NEsLoHdLF0EmUeZTGV7r.LJgW");throw new Failure(401,"账号或密码错误");}
  var r=rows.getFirst();String id=r.get("id").toString();
  if(!Boolean.TRUE.equals(r.get("active")))throw new Failure(401,"账号或密码错误");
  Object lock=r.get("locked_until");
  if(lock!=null && (lock instanceof Timestamp ts?ts.toInstant():((java.time.LocalDateTime)lock).toInstant(java.time.ZoneOffset.UTC)).isAfter(Instant.now()))throw new Failure(429,"登录尝试过多，请稍后重试");
  if(!encoder.matches(password,r.get("password_hash").toString())){
   db.update("UPDATE app_user SET failures=failures+1 WHERE id=?",id);
   if(db.queryForObject("SELECT failures FROM app_user WHERE id=?",Integer.class,id)>=5)db.update("UPDATE app_user SET locked_until=? WHERE id=?",Timestamp.from(Instant.now().plusSeconds(900)),id);
   throw new Failure(401,"账号或密码错误");
  }
  db.update("UPDATE app_user SET failures=0,locked_until=NULL WHERE id=?",id);
  byte[] random=new byte[32];new SecureRandom().nextBytes(random);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
  db.update("DELETE FROM auth_session WHERE expires_at<?",Timestamp.from(Instant.now()));
  db.update("INSERT INTO auth_session VALUES(?,?,?)",hash(token),id,Timestamp.from(Instant.now().plusSeconds(28800)));
  return Map.of("token",token,"user",new User(id,r.get("tenant").toString(),name,r.get("role").toString()));
 }
 public User current(String authorization){
  if(authorization==null||!authorization.startsWith("Bearer "))throw new Failure(401,"请先登录");
  var users=db.query("SELECT u.id,u.tenant,u.username,u.role FROM auth_session s JOIN app_user u ON s.user_id=u.id WHERE s.token_hash=? AND s.expires_at>? AND u.active=true",
   (r,n)->new User(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),hash(authorization.substring(7)),Timestamp.from(Instant.now()));
  if(users.isEmpty())throw new Failure(401,"登录已失效");return users.getFirst();
 }
 public void logout(String authorization){if(authorization!=null&&authorization.startsWith("Bearer "))db.update("DELETE FROM auth_session WHERE token_hash=?",hash(authorization.substring(7)));}
 public List<Map<String,Object>> users(User u){role(u,"ADMIN");return db.queryForList("SELECT id,username,role,active FROM app_user WHERE tenant=? ORDER BY username",u.tenant());}
 public void createUser(User u,Map<String,String> input){
  role(u,"ADMIN");String name=input.getOrDefault("username",""),pass=input.getOrDefault("password",""),role=input.getOrDefault("role","");
  if(!name.matches("[a-zA-Z0-9_.-]{3,50}")||pass.length()<12||pass.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72||!Set.of("ADMIN","REVIEWER","OPERATOR","VIEWER").contains(role))throw new Failure(400,"账号、密码或角色格式错误");
  db.update("INSERT INTO app_user(id,tenant,username,password_hash,role,active) VALUES(?,?,?,?,?,true)",UUID.randomUUID().toString(),u.tenant(),name,encoder.encode(pass),role);
 }
 public void changeUser(User u,String id,Map<String,String> input){
  role(u,"ADMIN");var users=db.queryForList("SELECT role,active FROM app_user WHERE tenant=? AND id=?",u.tenant(),id);
  if(users.isEmpty())throw new Failure(404,"账号不存在");
  String nextRole=input.getOrDefault("role",users.getFirst().get("role").toString());
  if(!Set.of("ADMIN","REVIEWER","OPERATOR","VIEWER").contains(nextRole))throw new Failure(400,"角色错误");
  boolean active=input.containsKey("active")?Boolean.parseBoolean(input.get("active")):Boolean.TRUE.equals(users.getFirst().get("active"));
  require(!id.equals(u.id())||(active&&nextRole.equals("ADMIN")),"不可停用或降级自己的管理账号");
  db.update("UPDATE app_user SET role=?,active=? WHERE tenant=? AND id=?",nextRole,active,u.tenant(),id);
  String password=input.getOrDefault("password","");
  if(!password.isEmpty()){
   if(password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new Failure(400,"密码须至少 12 位且不超过 72 字节");
   db.update("UPDATE app_user SET password_hash=?,failures=0,locked_until=NULL WHERE id=?",encoder.encode(password),id);
  }
  db.update("DELETE FROM auth_session WHERE user_id=?",id);
 }
 public static void role(User user,String role){
  if(role.equals("OPERATOR")&&!user.role().equals("VIEWER"))return;
  if(user.role().equals("ADMIN")||user.role().equals(role))return;
  throw new Failure(403,"当前角色没有此操作权限");
 }
}
