/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.casehub;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.util.*;
import java.time.Instant;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import static cn.zhuatech.casehub.Model.*;
@RestController @RequestMapping("/api")
public class Api {
 final Engine e;final Auth auth;
 public Api(Engine e,Auth auth){this.e=e;this.auth=auth;}
 public record Login(String username,String password){}
 public record Command(String code,Integer version,Map<String,Object> data,String remark){}
 User user(String h){return auth.current(h);}
 Map<String,Object> data(Command c){if(c.data()==null)throw new Failure(400,"缺少 data");return c.data();}
 int version(Command c){if(c.version()==null||c.version()<1)throw new Failure(400,"缺少有效版本");return c.version();}
 @GetMapping("/health") Map<String,Object> health(){e.jdbc().queryForObject("SELECT 1",Integer.class);return Map.of("status","UP","project","casehub");}
 @PostMapping("/auth/login") Map<String,Object> login(@RequestBody Login i){return auth.login(i.username(),i.password());}
 @PostMapping("/auth/logout") Map<String,Object> logout(@RequestHeader(value="Authorization",required=false) String h){auth.logout(h);return Map.of("ok",true);}
 @GetMapping("/me") User me(@RequestHeader(value="Authorization",required=false) String h){return user(h);}
 @GetMapping("/catalog") Object catalog(@RequestHeader(value="Authorization",required=false) String h){user(h);return e.spec();}
 @GetMapping("/dashboard") Object dashboard(@RequestHeader(value="Authorization",required=false) String h){return e.dashboard(user(h));}
 @GetMapping("/records") Page records(@RequestHeader(value="Authorization",required=false) String h,@RequestParam String module,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return e.page(user(h),module,q,state,page,size);}
 @GetMapping("/records/{id}") Row record(@RequestHeader(value="Authorization",required=false) String h,@PathVariable String id){return e.get(user(h),id);}
 @PostMapping("/records/{module}") Object create(@RequestHeader(value="Authorization",required=false) String h,@RequestHeader(value="Idempotency-Key",required=false) String key,@PathVariable String module,@RequestBody Command c){
  if(c.code()==null)throw new Failure(400,"缺少编号");return e.create(user(h),module,c.code(),data(c),key);
 }
 @PutMapping("/records/{id}") Object edit(@RequestHeader(value="Authorization",required=false) String h,@RequestHeader(value="Idempotency-Key",required=false) String key,@PathVariable String id,@RequestBody Command c){return e.edit(user(h),id,version(c),data(c),key);}
 @PostMapping("/records/{id}/actions/{action}") Object action(@RequestHeader(value="Authorization",required=false) String h,@RequestHeader(value="Idempotency-Key",required=false) String key,@PathVariable String id,@PathVariable String action,@RequestBody Command c){return e.action(user(h),id,action,version(c),data(c),c.remark(),key);}
 @GetMapping("/records/{id}/history") Object history(@RequestHeader(value="Authorization",required=false) String h,@PathVariable String id){return e.history(user(h),id);}
 @GetMapping(value="/export/{module}",produces="text/csv;charset=UTF-8") ResponseEntity<String> csv(@RequestHeader(value="Authorization",required=false) String h,@PathVariable String module){return ResponseEntity.ok().header("Content-Disposition","attachment; filename=\""+module+".csv\"").body(e.csv(user(h),module));}
 @GetMapping("/admin/users") Object users(@RequestHeader(value="Authorization",required=false) String h){return auth.users(user(h));}
 @PostMapping("/admin/users") @Transactional Object addUser(@RequestHeader(value="Authorization",required=false) String h,@RequestBody Map<String,String> i){User u=user(h);e.lock(u);auth.createUser(u,i);e.audit(u,"USERS","USER_CREATE",Map.of(),Map.of("username",i.get("username"),"role",i.get("role")),"新建账号");return Map.of("ok",true);}
 @PatchMapping("/admin/users/{id}") @Transactional Object changeUser(@RequestHeader(value="Authorization",required=false) String h,@PathVariable String id,@RequestBody Map<String,String> i){User u=user(h);e.lock(u);auth.changeUser(u,id,i);e.audit(u,"USERS","USER_CHANGE",Map.of(),Map.of("id",id,"role",i.getOrDefault("role",""),"active",i.getOrDefault("active","")),"调整权限或密码；旧会话已撤销");return Map.of("ok",true);}
 @GetMapping("/admin/audit") Object audit(@RequestHeader(value="Authorization",required=false) String h){User u=user(h);Auth.role(u,"ADMIN");return e.jdbc().queryForList("SELECT record_id,actor,action,remark,created_at FROM audit_event WHERE tenant=? ORDER BY sequence_no DESC LIMIT 200",u.tenant());}
 @GetMapping("/attachments/{record}") Object attachments(@RequestHeader(value="Authorization",required=false) String h,@PathVariable String record){User u=user(h);e.get(u,record);return e.jdbc().queryForList("SELECT id,filename,digest,size_bytes,created_at FROM attachment WHERE tenant=? AND record_id=? ORDER BY created_at DESC",u.tenant(),record);}
 @PostMapping("/attachments/{record}") @Transactional Object upload(@RequestHeader(value="Authorization",required=false) String h,@PathVariable String record,@RequestParam MultipartFile file)throws Exception{
  User u=user(h);Auth.role(u,"OPERATOR");e.lock(u);Row target=e.get(u,record);
  if(target.module().equals("evidence"))require(target.state().equals("REGISTERED")&&e.ref(u,target.data(),"case","cases").state().equals("INVESTIGATING"),"证据已封存或案件已进入结案流程，不可追加附件");
  if(target.module().equals("cases"))require(!target.state().equals("CLOSED"),"案件已结案，不可追加附件");
  if(file.isEmpty()||file.getSize()>2*1024*1024)throw new Failure(400,"附件须为 1 字节至 2 MB");
  String name=Objects.toString(file.getOriginalFilename(),"attachment").replaceAll("[\\\\/\\r\\n\\x00-]","_");if(name.length()>180)throw new Failure(400,"文件名过长");
  String lower=name.toLowerCase(Locale.ROOT);if(!lower.matches(".*\\.(pdf|png|jpg|jpeg|txt|csv|xlsx|docx)$"))throw new Failure(400,"仅支持 PDF、图片、文本与 Office 文档");
  byte[] bytes=file.getBytes();String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));String id=UUID.randomUUID().toString();
  e.jdbc().update("INSERT INTO attachment VALUES(?,?,?,?,?,?,?,?,?)",id,u.tenant(),record,name,digest,bytes,bytes.length,u.id(),Timestamp.from(Instant.now()));
  e.audit(u,record,"ATTACHMENT",Map.of(),Map.of("filename",name,"sha256",digest,"size",bytes.length),"上传附件");return Map.of("id",id,"sha256",digest);
 }
 @GetMapping("/attachments/download/{id}") ResponseEntity<byte[]> download(@RequestHeader(value="Authorization",required=false) String h,@PathVariable String id){
  User u=user(h);var rows=e.jdbc().queryForList("SELECT * FROM attachment WHERE tenant=? AND id=?",u.tenant(),id);if(rows.isEmpty())throw new Failure(404,"附件不存在");var r=rows.getFirst();
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename(r.get("filename").toString(),StandardCharsets.UTF_8).build().toString()).body((byte[])r.get("bytes"));
 }
}
@RestControllerAdvice class ApiErrors {
 @ExceptionHandler(Failure.class) ResponseEntity<?> domain(Failure f){return ResponseEntity.status(f.status).body(Map.of("message",f.getMessage()));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(Exception ex){return ResponseEntity.status(409).body(Map.of("message","编号或账号已存在，或关联数据冲突"));}
 @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.multipart.MaxUploadSizeExceededException.class,IllegalArgumentException.class})
 ResponseEntity<?> bad(Exception ex){return ResponseEntity.badRequest().body(Map.of("message","请求格式或文件大小不正确"));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> server(Exception ex){org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("服务请求失败",ex);return ResponseEntity.status(500).body(Map.of("message","服务处理失败，请联系管理员并查看服务端日志"));}
}
