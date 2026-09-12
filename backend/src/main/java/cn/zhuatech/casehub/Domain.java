/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.casehub;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.*;
import static cn.zhuatech.casehub.Model.*;
import static cn.zhuatech.casehub.Engine.*;
@Component public class Domain {
 static String text(Row r,String k){return txt(r.data(),k);}
 static List<Row> linked(Engine e,User u,String module,String field,String id){return e.all(u,module).stream().filter(x->text(x,field).equals(id)).toList();}
 public void create(Engine e,User u,String module,Map<String,Object>d){
  if(module.equals("tasks")||module.equals("evidence")){Row incident=e.ref(u,d,"case","cases");require(incident.state().equals("INVESTIGATING"),"仅调查中的案件可新增任务或证据");}
  if(module.equals("tasks"))require(!date(d,"dueDate").isBefore(LocalDate.now()),"任务截止日不能早于今天");
  if(module.equals("evidence"))require(!date(d,"collectedAt").isAfter(LocalDate.now()),"证据取得日期不能是未来");
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
    d.put("resolution",txt(i,"resolution"));d.put("submittedAt",Instant.now().toString());
   }
   case "cases.approve" -> {
    var evidence=linked(e,u,"evidence","case",r.id()).stream().filter(x->x.state().equals("SEALED")).toList();
    require(!evidence.isEmpty()&&linked(e,u,"tasks","case",r.id()).stream().allMatch(x->x.state().equals("DONE")),"结案条件已变化，请重新调查");
    e.ledger(u,"closures","ARCHIVED",Map.of("case",r.id(),"resolution",txt(d,"resolution"),"evidenceDigests",evidence.stream().map(x->text(x,"manifestDigest")).toList(),"approvedBy",u.username()));
    d.put("approvedBy",u.username());d.put("closedAt",Instant.now().toString());
   }
   case "cases.return" -> {d.put("returnReason",txt(i,"reason"));d.remove("resolution");}
   case "tasks.start" -> d.put("startedAt",Instant.now().toString());
   case "tasks.complete" -> {require(e.ref(u,d,"case","cases").state().equals("INVESTIGATING"),"案件不在调查阶段");d.put("result",txt(i,"result"));d.put("completedAt",Instant.now().toString());}
   case "tasks.reopen" -> {require(e.ref(u,d,"case","cases").state().equals("INVESTIGATING"),"案件不在调查阶段");d.put("reopenReason",txt(i,"reason"));d.remove("completedAt");}
   case "evidence.seal" -> {
    require(e.ref(u,d,"case","cases").state().equals("INVESTIGATING"),"案件不在调查阶段");
    var files=e.jdbc().queryForList("SELECT filename,digest,size_bytes FROM attachment WHERE tenant=? AND record_id=? ORDER BY id",u.tenant(),r.id());
    require(!files.isEmpty(),"封存前必须上传实际证据附件");
    d.put("manifestDigest",Auth.hash(e.encode(files)));d.put("sealedAt",Instant.now().toString());d.put("sealedBy",u.username());
   }
  }
  return null;
 }
 public Map<String,Object> metrics(Engine e,User u){return Map.of("调查中案件",e.all(u,"cases").stream().filter(r->r.state().equals("INVESTIGATING")).count(),"待结案复核",e.all(u,"cases").stream().filter(r->r.state().equals("REVIEW")).count(),"已归档案件",e.all(u,"closures").size());}
}
