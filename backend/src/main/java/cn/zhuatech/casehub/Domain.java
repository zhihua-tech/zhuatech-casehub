/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.casehub;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.*;
import java.security.MessageDigest;
import static cn.zhuatech.casehub.Model.*;
import static cn.zhuatech.casehub.Engine.*;
@Component public class Domain {
 static String text(Row r,String k){return txt(r.data(),k);}
 static List<Row> linked(Engine e,User u,String module,String field,String id){return e.all(u,module).stream().filter(x->text(x,field).equals(id)).toList();}
 static String holder(Engine e,User u,Row evidence,Map<String,Object>d){String current=txt(d,"currentHolder");if(!current.isEmpty())return current;return e.jdbc().queryForObject("SELECT username FROM app_user WHERE tenant=? AND id=?",String.class,u.tenant(),evidence.creator());}
 public Map<String,Object> integrity(Engine e,User u,Row evidence){
  require(evidence.module().equals("evidence"),"仅证据记录支持完整性校验");
  var files=e.jdbc().queryForList("SELECT filename,digest,size_bytes,bytes FROM attachment WHERE tenant=? AND record_id=? ORDER BY id",u.tenant(),evidence.id());
  List<Map<String,Object>> manifest=new ArrayList<>();int damaged=0;
  for(var file:files){byte[] bytes=(byte[])file.get("bytes");String actual=HexFormat.of().formatHex(digest(bytes));
   if(!actual.equalsIgnoreCase(file.get("digest").toString())||bytes.length!=((Number)file.get("size_bytes")).intValue())damaged++;
   Map<String,Object> item=new LinkedHashMap<>();item.put("filename",file.get("filename"));item.put("digest",file.get("digest"));item.put("size_bytes",file.get("size_bytes"));manifest.add(item);
  }
  String actual=Auth.hash(e.encode(manifest)),expected=text(evidence,"manifestDigest");
  return Map.of("verified",!files.isEmpty()&&damaged==0&&!expected.isEmpty()&&expected.equals(actual),"expected",expected,"actual",actual,"fileCount",files.size(),"damagedFiles",damaged);
 }
 static byte[] digest(byte[] bytes){try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(Exception ex){throw new IllegalStateException("SHA-256 不可用",ex);}}
 public void create(Engine e,User u,String module,Map<String,Object>d){
  if(module.equals("tasks")||module.equals("evidence")){Row incident=e.ref(u,d,"case","cases");require(incident.state().equals("INVESTIGATING"),"仅调查中的案件可新增任务或证据");}
  if(module.equals("tasks"))require(!date(d,"dueDate").isBefore(LocalDate.now()),"任务截止日不能早于今天");
  if(module.equals("evidence")){require(!date(d,"collectedAt").isAfter(LocalDate.now()),"证据取得日期不能是未来");d.put("currentHolder",u.username());d.put("transferCount",0);}
 }
 public void edit(Engine e,User u,Row r,Map<String,Object>d){
  if(r.module().equals("tasks")){require(e.ref(u,d,"case","cases").state().equals("INVESTIGATING")&&txt(d,"case").equals(text(r,"case")),"任务不能迁移案件或在结案复核后修改");}
 }
 public String action(Engine e,User u,Row r,String action,Map<String,Object>i,Map<String,Object>d){
  switch(r.module()+"."+action){
   case "cases.submit" -> {
    var tasks=linked(e,u,"tasks","case",r.id());var evidence=linked(e,u,"evidence","case",r.id());
    require(!tasks.isEmpty()&&tasks.stream().allMatch(x->x.state().equals("DONE")),"调查任务尚未全部完成");
    require(!evidence.isEmpty()&&evidence.stream().anyMatch(x->x.state().equals("SEALED")),"至少需要一份已核验证据");
    require(evidence.stream().filter(x->x.state().equals("SEALED")).allMatch(x->Boolean.TRUE.equals(integrity(e,u,x).get("verified"))),"证据附件或封存清单完整性校验失败");
    d.put("resolution",txt(i,"resolution"));d.put("submittedAt",Instant.now().toString());
   }
   case "cases.approve" -> {
    var evidence=linked(e,u,"evidence","case",r.id()).stream().filter(x->x.state().equals("SEALED")).toList();
    require(!evidence.isEmpty()&&linked(e,u,"tasks","case",r.id()).stream().allMatch(x->x.state().equals("DONE")),"结案条件已变化，请重新调查");
    require(evidence.stream().allMatch(x->Boolean.TRUE.equals(integrity(e,u,x).get("verified"))),"证据附件或封存清单完整性校验失败");
    e.ledger(u,"closures","ARCHIVED",Map.of("case",r.id(),"resolution",txt(d,"resolution"),"evidenceDigests",evidence.stream().map(x->text(x,"manifestDigest")).toList(),"approvedBy",u.username()));
    d.put("approvedBy",u.username());d.put("closedAt",Instant.now().toString());
   }
   case "cases.return" -> {d.put("returnReason",txt(i,"reason"));d.remove("resolution");}
   case "tasks.start" -> d.put("startedAt",Instant.now().toString());
   case "tasks.complete" -> {require(e.ref(u,d,"case","cases").state().equals("INVESTIGATING"),"案件不在调查阶段");d.put("result",txt(i,"result"));d.put("completedAt",Instant.now().toString());}
   case "tasks.reopen" -> {require(e.ref(u,d,"case","cases").state().equals("INVESTIGATING"),"案件不在调查阶段");d.put("reopenReason",txt(i,"reason"));d.remove("completedAt");}
   case "evidence.seal" -> {
    require(e.ref(u,d,"case","cases").state().equals("INVESTIGATING"),"案件不在调查阶段");
    require(u.username().equals(holder(e,u,r,d)),"仅当前证据保管人可核验封存，请先完成交接");
    var result=integrity(e,u,r);require(((Number)result.get("fileCount")).intValue()>0,"封存前必须上传实际证据附件");
    require(((Number)result.get("damagedFiles")).intValue()==0,"证据附件摘要或大小不一致，不得封存");
    d.put("manifestDigest",result.get("actual"));d.put("sealedAt",Instant.now().toString());d.put("sealedBy",u.username());
   }
   case "evidence.transfer" -> {
    String current=holder(e,u,r,d),next=txt(i,"toHolder"),reference=txt(i,"transferRef");
    require(u.username().equals(current)||u.role().equals("ADMIN"),"仅当前保管人或管理员可发起证据交接");
    require(!current.equals(next),"接收人与当前保管人不能相同");
    require(e.jdbc().queryForObject("SELECT COUNT(*) FROM app_user WHERE tenant=? AND username=? AND active=true AND role<>'VIEWER'",Integer.class,u.tenant(),next)==1,"接收人必须是当前企业的有效业务账号");
    require(e.all(u,"custody").stream().noneMatch(x->text(x,"transferRef").equalsIgnoreCase(reference)),"交接凭证号重复");
    e.ledger(u,"custody","POSTED",Map.of("evidence",r.id(),"transferRef",reference,"fromHolder",current,"toHolder",next,"purpose",txt(i,"purpose"),"transferredBy",u.username(),"transferredAt",Instant.now().toString()));
    d.put("currentHolder",next);d.put("transferCount",((Number)d.getOrDefault("transferCount",0)).intValue()+1);return r.state();
   }
  }
  return null;
 }
 public Map<String,Object> metrics(Engine e,User u){return Map.of("调查中案件",e.all(u,"cases").stream().filter(r->r.state().equals("INVESTIGATING")).count(),"待结案复核",e.all(u,"cases").stream().filter(r->r.state().equals("REVIEW")).count(),"已归档案件",e.all(u,"closures").size());}
}
