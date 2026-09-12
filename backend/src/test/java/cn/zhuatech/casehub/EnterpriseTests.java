/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.casehub;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.*;
import java.time.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.zhuatech.casehub.Model.*;
@SpringBootTest(properties={"app.demo=false","app.admin-password=Test-Admin-2026!","app.reviewer-password=Test-Review-2026!","app.operator-password=Test-Operator-2026!","app.viewer-password=Test-Viewer-2026!"},webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EnterpriseTests {
 @LocalServerPort int port;@Autowired ObjectMapper json;@Autowired Engine e;@Autowired Auth auth;@Autowired JdbcTemplate db;
 final HttpClient client=HttpClient.newHttpClient();final Map<String,String> tokens=new HashMap<>();final Map<String,String> ids=new LinkedHashMap<>();List<Map<String,Object>> steps;
 record Result(int status,Map<String,Object> body){}
 @SuppressWarnings("unchecked") Result call(String method,String path,Object body,String role,String key)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json");
  if(role!=null)b.header("Authorization","Bearer "+tokens.getOrDefault(role,role));
  if(key!=null)b.header("Idempotency-Key",key);
  b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());String text=r.body();Object parsed=text.isBlank()?Map.of():json.readValue(text,Object.class);Map<String,Object> result=parsed instanceof Map?(Map<String,Object>)parsed:Map.of("items",parsed);
  return new Result(r.statusCode(),result);
 }
 Result call(String method,String path,Object body,String role)throws Exception{return call(method,path,body,role,UUID.randomUUID().toString());}
 @SuppressWarnings("unchecked") @BeforeAll void initialize()throws Exception{
  assertEquals(200,call("GET","/health",null,null).status());
  for(String name:List.of("admin","reviewer","operator","viewer")){
   String pass=switch(name){case "admin"->"Test-Admin-2026!";case "reviewer"->"Test-Review-2026!";case "operator"->"Test-Operator-2026!";default->"Test-Viewer-2026!";};
   Result r=call("POST","/auth/login",Map.of("username",name,"password",pass),null);assertEquals(200,r.status(),r.body().toString());tokens.put(name,r.body().get("token").toString());
  }
  for(String name:List.of("reviewer2","reviewer3")){
   assertEquals(200,call("POST","/admin/users",Map.of("username",name,"password","Test-Review-2026!","role","REVIEWER"),"admin").status());
   tokens.put(name,call("POST","/auth/login",Map.of("username",name,"password","Test-Review-2026!"),null).body().get("token").toString());
  }
  try(var in=getClass().getResourceAsStream("/acceptance.json")){steps=json.readValue(in,List.class);}
 }
 @SuppressWarnings("unchecked") Object resolve(Object value){
  if(value instanceof String s){if(s.startsWith("$today"))return LocalDate.now().plusDays(s.length()==6?0:Integer.parseInt(s.substring(6))).toString();if(s.startsWith("$now")){String delta=s.substring(4);if(delta.isEmpty())return Instant.now().toString();long n=Long.parseLong(delta.substring(0,delta.length()-1));return Instant.now().plus(Duration.ofMinutes(delta.endsWith("h")?n*60:n)).toString();}if(s.startsWith("$"))return ids.get(s.substring(1,s.indexOf('.')));return s;}
  if(value instanceof Map<?,?> m){var out=new LinkedHashMap<String,Object>();m.forEach((k,v)->out.put(k.toString(),resolve(v)));return out;}if(value instanceof List<?> l)return l.stream().map(this::resolve).toList();return value;
 }
 Object at(Map<String,Object> m,String path){Object v=m;for(String key:path.split("\\.")){if(!(v instanceof Map<?,?> map))return null;v=map.get(key);}return v;}
 Map<String,Object> fresh(Map<String,Object> sample,String suffix){var data=new LinkedHashMap<>(sample);for(String field:List.of("name","planCode","employeeNo","serial","sku"))if(data.containsKey(field))data.put(field,data.get(field)+"-"+suffix);return data;}
 void compare(Object expected,Object actual,String label){
  if(expected instanceof Number){assertNotNull(actual,label);assertEquals(0,new java.math.BigDecimal(expected.toString()).compareTo(new java.math.BigDecimal(actual.toString())),label);}
  else assertEquals(expected,actual,label);
 }
 @SuppressWarnings("unchecked") boolean matches(Map<String,Object> row,Map<String,Object> where){return where.entrySet().stream().allMatch(x->Objects.toString(at(row,x.getKey()),"").equals(x.getValue().toString()));}
 @SuppressWarnings("unchecked") @TestFactory @Order(1) Stream<DynamicTest> businessScenarios(){
  return IntStream.range(0,steps.size()).mapToObj(index->DynamicTest.dynamicTest(String.format("%02d %s %s %s",index+1,steps.get(index).get("op"),steps.get(index).getOrDefault("target",steps.get(index).getOrDefault("module","")),steps.get(index).getOrDefault("action","")),()->run(steps.get(index))));
 }
 @SuppressWarnings("unchecked") void run(Map<String,Object> step)throws Exception{
  String op=step.get("op").toString(),role=step.getOrDefault("role","operator").toString();Map<String,Object> data=(Map<String,Object>)resolve(step.getOrDefault("data",Map.of()));
  if(op.equals("check")){
   var result=call("GET","/records/"+ids.get(step.get("target").toString()),null,role);assertEquals(200,result.status());
   ((Map<String,Object>)resolve(step.get("expect"))).forEach((k,v)->compare(v,at(result.body(),k),k));return;
  }
  if(op.equals("count")||op.equals("find")){
   var result=call("GET","/records?module="+step.get("module")+"&size=100",null,role);assertEquals(200,result.status());
   var where=(Map<String,Object>)resolve(step.getOrDefault("where",Map.of()));var list=((List<Map<String,Object>>)result.body().get("items")).stream().filter(x->matches(x,where)).toList();
   if(op.equals("count"))assertEquals(((Number)step.get("count")).intValue(),list.size(),step.toString());else{assertEquals(1,list.size(),step.toString());ids.put(step.get("as").toString(),list.getFirst().get("id").toString());}return;
  }
  if(op.equals("metric")){
   var metrics=(Map<String,Object>)call("GET","/dashboard",null,role).body().get("metrics");((Map<String,Object>)step.get("expect")).forEach((k,v)->compare(v,metrics.get(k),k));return;
  }
  int expected=((Number)step.getOrDefault("error",200)).intValue();Result result;long records=db.queryForObject("SELECT COUNT(*) FROM business_record",Long.class),audit=db.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class);
  if(op.equals("create")){
   String alias=step.get("as").toString();result=call("POST","/records/"+step.get("module"),Map.of("code","TEST-"+alias.toUpperCase(Locale.ROOT),"data",data),role);
   if(result.status()==200)ids.put(alias,((Map<String,Object>)result.body().get("record")).get("id").toString());
  }else if(op.equals("action")){
   String id=ids.get(step.get("target").toString());Result row=call("GET","/records/"+id,null,role);
   result=call("POST","/records/"+id+"/actions/"+step.get("action"),Map.of("version",row.body().get("version"),"data",data,"remark","企业业务验收"),role);
  }else if(op.equals("attachment")){
   String boundary="ZhihuaTestBoundary";String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"evidence.txt\"\r\nContent-Type: text/plain\r\n\r\n企业业务验收附件\r\n--"+boundary+"--\r\n";
   var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/attachments/"+ids.get(step.get("target").toString()))).header("Authorization","Bearer "+tokens.get(role)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(body)).build();
   var response=client.send(request,HttpResponse.BodyHandlers.ofString());result=new Result(response.statusCode(),json.readValue(response.body(),Map.class));
  }else throw new AssertionError("unknown step "+op);
  assertEquals(expected,result.status(),step+" -> "+result.body());
  if(expected>=400){assertEquals(records,db.queryForObject("SELECT COUNT(*) FROM business_record",Long.class),"拒绝操作不能产生业务流水");assertEquals(audit,db.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class),"拒绝操作不能产生成功审计");}
 }
 @Test @Order(2) void authenticationAndRoles()throws Exception{
  assertEquals(401,call("GET","/catalog",null,null).status());assertEquals(401,call("GET","/catalog",null,"invalid-session").status());
  assertEquals(403,call("GET","/admin/users",null,"operator").status());
  var sample=steps.stream().filter(s->s.get("op").equals("create")).findFirst().orElseThrow();
  assertEquals(403,call("POST","/records/"+sample.get("module"),Map.of("code","NO-VIEWER","data",resolve(sample.get("data"))),"viewer").status());
 }
 @Test @Order(3) void idempotencyAndUniqueCode()throws Exception{
  var sample=steps.stream().filter(s->s.get("op").equals("create")).findFirst().orElseThrow();String path="/records/"+sample.get("module");Map<String,Object> data=fresh((Map<String,Object>)resolve(sample.get("data")),"IDEMPOTENT");
  if(data.containsKey("serial"))data.put("serial","IDEMPOTENT-SERIAL");if(data.containsKey("sku"))data.put("sku","IDEMPOTENT-SKU");
  var body=Map.of("code","IDEMPOTENT-001","data",data);String key=UUID.randomUUID().toString();
  Result first=call("POST",path,body,"operator",key),again=call("POST",path,body,"operator",key);assertEquals(200,first.status(),first.body().toString());assertEquals(first.body(),again.body());
  assertEquals(409,call("POST",path,Map.of("code","IDEMPOTENT-002","data",data),"operator",key).status());
  assertEquals(409,call("POST",path,body,"operator").status());
  assertEquals(400,call("POST",path,body,"operator",null).status());
 }
 @Test @Order(4) void tenantIsolationAndAudit()throws Exception{
  db.update("INSERT INTO tenant_guard VALUES('isolation-test')");
  User other=new User("foreign-user","isolation-test","isolation","ADMIN");
  String id=ids.values().iterator().next();assertThrows(Failure.class,()->e.get(other,id));
  assertEquals(0,e.page(other,e.spec().modules().getFirst().key(),"","",1,20).total());
  assertFalse(e.history(auth.current("Bearer "+tokens.get("admin")),id).isEmpty());
  assertEquals(404,call("GET","/records/not-found",null,"operator").status());
 }
 @Test @Order(5) void staleVersionAndInputValidation()throws Exception{
  String id=ids.values().iterator().next();var row=call("GET","/records/"+id,null,"operator").body();
  Map<String,Object> values=new LinkedHashMap<>((Map<String,Object>)row.get("data"));var module=e.spec().module(row.get("module").toString());
  values.keySet().retainAll(module.fields().stream().map(Field::key).toList());
  assertEquals(409,call("PUT","/records/"+id,Map.of("version",999999,"data",values),"operator").status());
  assertEquals(400,call("GET","/records?module="+module.key()+"&size=1000",null,"operator").status());
  assertEquals(400,call("POST","/records/"+module.key(),Map.of("code","BAD-INPUT","data",Map.of("unknown","bad")),"operator").status());
 }
 @Test @Order(6) void concurrentIdempotency()throws Exception{
  var sample=steps.stream().filter(s->s.get("op").equals("create")).findFirst().orElseThrow();Map<String,Object> data=fresh((Map<String,Object>)resolve(sample.get("data")),"CONCURRENT");
  if(data.containsKey("serial"))data.put("serial","CONCURRENT-SERIAL");if(data.containsKey("sku"))data.put("sku","CONCURRENT-SKU");
  String key=UUID.randomUUID().toString(),path="/records/"+sample.get("module");var body=Map.of("code","CONCURRENT-001","data",data);
  try(var pool=Executors.newFixedThreadPool(4)){
   var futures=new ArrayList<Future<Result>>();for(int x=0;x<4;x++)futures.add(pool.submit(()->call("POST",path,body,"operator",key)));
   Map<String,Object> expected=null;for(var f:futures){var r=f.get(20,TimeUnit.SECONDS);assertEquals(200,r.status(),r.body().toString());if(expected==null)expected=r.body();else assertEquals(expected,r.body());}
  }
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM business_record WHERE code='CONCURRENT-001'",Integer.class));
 }
 @Test @Order(7) void selfReviewAndLogout()throws Exception{
  var module=e.spec().modules().stream().filter(m->m.actions().stream().anyMatch(Action::separate)).findFirst().orElseThrow();
  var approval=module.actions().stream().filter(Action::separate).findFirst().orElseThrow();
  String actorRole=approval.role().equals("ADMIN")?"admin":"reviewer";User reviewer=auth.current("Bearer "+tokens.get(actorRole));Row source=e.all(reviewer,module.key()).getFirst();
  Row fixture=e.system(reviewer,module.key(),"SECURITY-SELF-REVIEW",approval.from().getFirst(),source.data());
  var rejected=call("POST","/records/"+fixture.id()+"/actions/"+approval.key(),Map.of("version",1,"data",Map.of(),"remark","自审权限验证"),actorRole);
  assertEquals(409,rejected.status());assertTrue(rejected.body().get("message").toString().contains("提交人与审批人"));
  String logoutToken=call("POST","/auth/login",Map.of("username","viewer","password","Test-Viewer-2026!"),null).body().get("token").toString();
  assertEquals(200,call("POST","/auth/logout",Map.of(),logoutToken).status());assertEquals(401,call("GET","/me",null,logoutToken).status());
 }
 @Test @Order(8) void administrativeRevocationAndPasswordReset()throws Exception{
  assertEquals(200,call("POST","/admin/users",Map.of("username","temporary","password","Test-Temporary-2026!","role","VIEWER"),"admin").status());
  String token=call("POST","/auth/login",Map.of("username","temporary","password","Test-Temporary-2026!"),null).body().get("token").toString();
  String id=db.queryForObject("SELECT id FROM app_user WHERE username='temporary'",String.class);
  assertEquals(200,call("PATCH","/admin/users/"+id,Map.of("active","false"),"admin").status());assertEquals(401,call("GET","/me",null,token).status());
  assertEquals(401,call("POST","/auth/login",Map.of("username","temporary","password","Test-Temporary-2026!"),null).status());
 }
 @Test @Order(9) void exportAndAttachmentReadAuthorization()throws Exception{
  String module=e.spec().modules().getFirst().key();
  var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/export/"+module)).header("Authorization","Bearer "+tokens.get("viewer")).GET().build();
  var response=client.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());assertTrue(response.body().contains("编号"));assertTrue(response.body().contains("状态"));
  assertEquals(401,call("GET","/attachments/download/no-file",null,null).status());
 }
 @Test @Order(10) void differentRequestsCannotOverwriteSameVersion()throws Exception{
  var sample=steps.stream().filter(x->x.get("op").equals("create")&&e.spec().module(x.get("module").toString()).editable()).findFirst().orElseThrow();
  String module=sample.get("module").toString();Map<String,Object> data=fresh((Map<String,Object>)resolve(sample.get("data")),"VERSION-RACE");
  if(data.containsKey("serial"))data.put("serial","VERSION-RACE-SERIAL");if(data.containsKey("sku"))data.put("sku","VERSION-RACE-SKU");
  Result created=call("POST","/records/"+module,Map.of("code","VERSION-RACE","data",data),"operator");assertEquals(200,created.status(),created.body().toString());
  String id=((Map<String,Object>)created.body().get("record")).get("id").toString();
  String editable=e.spec().module(module).fields().stream().filter(f->f.type().equals("text")&&!Set.of("sku","serial","barcode").contains(f.key())).findFirst().orElseThrow().key();
  try(var pool=Executors.newFixedThreadPool(4)){
   var futures=new ArrayList<Future<Result>>();for(int x=0;x<4;x++){var changed=new LinkedHashMap<>(data);changed.put(editable,"并发修改 "+x);futures.add(pool.submit(()->call("PUT","/records/"+id,Map.of("version",1,"data",changed),"operator")));}
   int success=0,conflict=0;for(var f:futures){Result r=f.get(20,TimeUnit.SECONDS);if(r.status()==200)success++;else if(r.status()==409)conflict++;else fail(r.body().toString());}
   assertEquals(1,success);assertEquals(3,conflict);assertEquals(2,call("GET","/records/"+id,null,"operator").body().get("version"));
  }
 }
 @Test @Order(11) void failedLoginLocksAndDoesNotIssueToken()throws Exception{
  assertEquals(200,call("POST","/admin/users",Map.of("username","lockoutuser","password","Test-Lockout-2026!","role","VIEWER"),"admin").status());
  for(int i=0;i<5;i++){Result r=call("POST","/auth/login",Map.of("username","lockoutuser","password","incorrect-password"),null);assertEquals(401,r.status());assertFalse(r.body().containsKey("token"));}
  assertEquals(429,call("POST","/auth/login",Map.of("username","lockoutuser","password","Test-Lockout-2026!"),null).status());
 }
 @Test @Order(12) void foreignTenantCannotReadHttpOrAttachments()throws Exception{
  String id=UUID.randomUUID().toString();db.update("INSERT INTO app_user(id,tenant,username,password_hash,role,active) VALUES(?,?,?,?,?,true)",id,"isolation-test","foreign-http-user",auth.encoder.encode("Test-Foreign-2026!"),"ADMIN");
  String token=call("POST","/auth/login",Map.of("username","foreign-http-user","password","Test-Foreign-2026!"),null).body().get("token").toString();
  String record=ids.values().iterator().next();assertEquals(404,call("GET","/records/"+record,null,token).status());assertEquals(404,call("GET","/records/"+record+"/history",null,token).status());assertEquals(404,call("GET","/attachments/"+record,null,token).status());
  var records=call("GET","/records?module="+e.spec().modules().getFirst().key(),null,token);assertEquals(200,records.status());assertEquals(0,((Number)records.body().get("total")).intValue());
 }

 @Test @Order(13) void databaseSequenceOrdersEventsWithSameTimestamp(){
  User u=auth.current("Bearer "+tokens.get("admin"));String module=e.spec().modules().getFirst().key();Row source=e.all(u,module).getFirst();
  Row first=e.system(u,module,"ORDER-FIRST",source.state(),source.data()),second=e.system(u,module,"ORDER-SECOND",source.state(),source.data());
  db.update("UPDATE business_record SET created_at='2026-09-05 00:00:00',updated_at='2026-09-05 00:00:00' WHERE id IN (?,?)",first.id(),second.id());
  var ordered=e.all(u,module).stream().filter(r->r.id().equals(first.id())||r.id().equals(second.id())).map(Row::code).toList();
  assertEquals(List.of("ORDER-FIRST","ORDER-SECOND"),ordered);
 }

}
