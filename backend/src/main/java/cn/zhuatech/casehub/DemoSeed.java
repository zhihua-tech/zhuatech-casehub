/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.casehub;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import static cn.zhuatech.casehub.Model.*;
/**
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@Component @org.springframework.core.annotation.Order(1)
public class DemoSeed implements ApplicationRunner {
 final Engine e;final Auth auth;final boolean demo;final Map<String,Row> aliases=new HashMap<>();
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public DemoSeed(Engine e,Auth auth,@Value("${app.demo:false}")boolean demo){this.e=e;this.auth=auth;this.demo=demo;}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 @SuppressWarnings("unchecked") Object resolve(Object value){
  if(value instanceof String s){if(s.startsWith("$today"))return LocalDate.now().plusDays(s.length()==6?0:Integer.parseInt(s.substring(6))).toString();if(s.startsWith("$now")){String delta=s.substring(4);if(delta.isEmpty())return Instant.now().toString();long n=Long.parseLong(delta.substring(0,delta.length()-1));return Instant.now().plus(Duration.ofMinutes(delta.endsWith("h")?n*60:n)).toString();}if(s.startsWith("$"))return aliases.get(s.substring(1,s.indexOf('.'))).id();return s;}
  if(value instanceof Map<?,?> m){var out=new LinkedHashMap<String,Object>();m.forEach((k,v)->out.put(k.toString(),resolve(v)));return out;}if(value instanceof List<?> l)return l.stream().map(this::resolve).toList();return value;
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 User user(String name){return e.jdbc().query("SELECT id,tenant,username,role FROM app_user WHERE username=?",(r,n)->new User(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),name).getFirst();}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 @SuppressWarnings("unchecked") public void run(ApplicationArguments args)throws Exception{
  if(!demo||e.jdbc().queryForObject("SELECT COUNT(*) FROM business_record",Integer.class)>0)return;
  
  try(var in=getClass().getResourceAsStream("/demo.json")){
   List<Map<String,Object>> steps=e.mapper().readValue(in,List.class);
   for(var step:steps){
    User u=user(step.getOrDefault("role","operator").toString());String op=step.get("op").toString();
    Map<String,Object> data=(Map<String,Object>)resolve(step.getOrDefault("data",Map.of()));
    if(op.equals("create")){
     String name=step.get("as").toString();Row r=(Row)e.create(u,step.get("module").toString(),"DEMO-"+name.toUpperCase(Locale.ROOT),data,UUID.randomUUID().toString()).get("record");aliases.put(name,r);
    }else if(op.equals("action")){
     Row r=e.get(u,aliases.get(step.get("target").toString()).id());e.action(u,r.id(),step.get("action").toString(),r.version(),data,"演示数据：业务操作",UUID.randomUUID().toString());
    }else if(op.equals("find")){
     var where=(Map<String,Object>)resolve(step.get("where"));Row r=e.all(u,step.get("module").toString()).stream().filter(x->where.entrySet().stream().allMatch(w->Objects.toString(w.getKey().startsWith("data.")?x.data().get(w.getKey().substring(5)):x.state(),"").equals(w.getValue().toString()))).findFirst().orElseThrow();aliases.put(step.get("as").toString(),r);
    }else if(op.equals("attachment")){
     Row r=e.get(u,aliases.get(step.get("target").toString()).id());byte[] bytes="企业业务演示附件，非正式生产数据。".getBytes(java.nio.charset.StandardCharsets.UTF_8);
     e.jdbc().update("INSERT INTO attachment VALUES(?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),u.tenant(),r.id(),"demo-evidence.txt",Auth.hash(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)),bytes,bytes.length,u.id(),Timestamp.from(Instant.now()));e.audit(u,r.id(),"ATTACHMENT",Map.of(),Map.of("filename","demo-artwork.txt"),"演示附件上传");
    }
   }
  }
 }
}
