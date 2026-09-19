/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.casehub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.math.*;
import java.time.*;
import java.sql.Timestamp;
import static cn.zhuatech.casehub.Model.*;
/**
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@Service public class Engine {
 final JdbcTemplate db;final ObjectMapper json;final Catalog catalog;final Domain domain;
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Engine(JdbcTemplate db,ObjectMapper json,Catalog catalog,Domain domain){this.db=db;this.json=json;this.catalog=catalog;this.domain=domain;}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public JdbcTemplate jdbc(){return db;} /**
                                         * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
                                         */
public ObjectMapper mapper(){return json;} /**
                                                                                    * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
                                                                                    */
public Spec spec(){return catalog.spec;}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Map<String,Object> integrity(User u,Row evidence){return domain.integrity(this,u,evidence);}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 String encode(Object value){return json.writeValueAsString(value);}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 @SuppressWarnings("unchecked") Map<String,Object> decode(String value){return new LinkedHashMap<>(json.readValue(value,Map.class));}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Row row(java.sql.ResultSet r)throws java.sql.SQLException{return new Row(r.getString("id"),r.getString("tenant"),r.getString("module"),r.getString("code"),r.getString("state"),r.getInt("version"),r.getString("creator"),decode(r.getString("payload")),r.getTimestamp("created_at").toInstant().toString(),r.getTimestamp("updated_at").toInstant().toString());}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Row get(User u,String id){return db.query("SELECT * FROM business_record WHERE tenant=? AND id=?",(r,n)->row(r),u.tenant(),id).stream().findFirst().orElseThrow(()->new Failure(404,"记录不存在"));}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Row ref(User u,Map<String,Object> d,String key,String module){Row r=get(u,txt(d,key));require(r.module().equals(module),"关联记录类型不符: "+key);return r;}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public List<Row> all(User u,String module){return db.query("SELECT * FROM business_record WHERE tenant=? AND module=? ORDER BY sequence_no",(r,n)->row(r),u.tenant(),module);}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Page page(User u,String module,String query,String state,int page,int size){
  catalog.spec.module(module);if(page<1||page>1000000||size<1||size>100)throw new Failure(400,"分页参数错误");
  String like="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
  String sql=" FROM business_record WHERE tenant=? AND module=? AND (code LIKE ? OR payload LIKE ?) AND (?='' OR state=?)";
  Object[] args={u.tenant(),module,like,like,state,state};
  long total=db.queryForObject("SELECT COUNT(*)"+sql,Long.class,args);
  var list=new ArrayList<Object>(Arrays.asList(args));list.add(size);list.add((page-1)*size);
  return new Page(db.query("SELECT *"+sql+" ORDER BY updated_at DESC,sequence_no DESC LIMIT ? OFFSET ?",(r,n)->row(r),list.toArray()),total,page,size);
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 void lock(User u){db.queryForObject("SELECT tenant FROM tenant_guard WHERE tenant=? FOR UPDATE",String.class,u.tenant());}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public static String txt(Map<String,Object> d,String key){return Objects.toString(d.get(key),"").strip();}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public static BigDecimal num(Map<String,Object> d,String key){try{return new BigDecimal(txt(d,key));}catch(Exception e){throw new Failure(400,"数值格式错误: "+key);}}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public static BigDecimal money(BigDecimal n){return n.setScale(2,RoundingMode.HALF_UP);}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public static LocalDate date(Map<String,Object> d,String key){try{return LocalDate.parse(txt(d,key));}catch(Exception e){throw new Failure(400,"日期格式错误: "+key);}}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 void validate(User u,List<Field> fields,Map<String,Object> input){
  Set<String> allowed=new HashSet<>();for(Field f:fields)allowed.add(f.key());
  for(String key:input.keySet())if(!allowed.contains(key))throw new Failure(400,"不支持的字段: "+key);
  for(Field f:fields){
   String v=txt(input,f.key());if(v.isEmpty()){if(f.required())throw new Failure(400,f.label()+"不能为空");continue;}
   if(v.length()>2000)throw new Failure(400,f.label()+"过长");
   if(f.type().equals("number")||f.type().equals("money")){
    BigDecimal n=num(input,f.key());if(n.precision()>16||n.scale()>4)throw new Failure(400,f.label()+"数值精度过大");
    if(f.min()!=null&&n.compareTo(new BigDecimal(f.min()))<0||f.max()!=null&&n.compareTo(new BigDecimal(f.max()))>0)throw new Failure(400,f.label()+"超出范围");
   }
   if(f.type().equals("integer")){BigDecimal n=num(input,f.key());if(n.stripTrailingZeros().scale()>0||n.compareTo(new BigDecimal(f.min()==null?"0":f.min()))<0||n.compareTo(new BigDecimal(f.max()==null?"1000000":f.max()))>0)throw new Failure(400,f.label()+"须为范围内整数");}
   if(f.type().equals("date"))date(input,f.key());
   if(f.type().equals("select")&&!f.options().contains(v))throw new Failure(400,f.label()+"选项错误");
   if(f.type().equals("ref"))ref(u,input,f.key(),f.ref());
  }
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 Map<String,Object> replay(User u,String key,Object request){
  if(key==null||!key.matches("[a-zA-Z0-9_.:-]{8,80}"))throw new Failure(400,"需要 8—80 位 Idempotency-Key");
  var list=db.queryForList("SELECT fingerprint,result_json FROM idempotency_record WHERE tenant=? AND actor=? AND request_key=?",u.tenant(),u.id(),key);
  if(list.isEmpty())return null;var item=list.getFirst();
  if(!item.get("fingerprint").equals(Auth.hash(encode(request))))throw new Failure(409,"幂等键已被不同请求使用");
  return decode(item.get("result_json").toString());
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 Map<String,Object> remember(User u,String key,Object request,Row row){
  Map<String,Object> result=Map.of("record",row);
  db.update("INSERT INTO idempotency_record VALUES(?,?,?,?,?)",u.tenant(),u.id(),key,Auth.hash(encode(request)),encode(result));return result;
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 @Transactional public Map<String,Object> create(User u,String module,String code,Map<String,Object> data,String key){
  Auth.role(u,"OPERATOR");lock(u);Object request=List.of("create",module,code,data);var old=replay(u,key,request);if(old!=null)return old;
  var m=catalog.spec.module(module);if(!m.creatable())throw new Failure(403,"流水由业务动作自动生成");
  if(!code.matches("[A-Za-z0-9_-]{2,80}"))throw new Failure(400,"编号须为 2—80 位字母数字或连字符");
  validate(u,m.fields(),data);var target=new LinkedHashMap<>(data);domain.create(this,u,module,target);
  Row r=system(u,module,code,m.initial(),target);return remember(u,key,request,r);
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 @Transactional public Map<String,Object> edit(User u,String id,int version,Map<String,Object> data,String key){
  Auth.role(u,"OPERATOR");lock(u);Object request=List.of("edit",id,version,data);var old=replay(u,key,request);if(old!=null)return old;
  Row r=get(u,id);var m=catalog.spec.module(r.module());require(r.version()==version,"记录已变更，请刷新");require(m.editable()&&r.state().equals(m.initial()),"当前记录已锁定，不可修改");
  validate(u,m.fields(),data);var target=new LinkedHashMap<>(data);domain.edit(this,u,r,target);return remember(u,key,request,save(u,r,r.state(),target,"EDIT","修改资料"));
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 @Transactional public Map<String,Object> action(User u,String id,String action,int version,Map<String,Object> input,String remark,String key){
  lock(u);Object request=List.of("action",id,action,version,input,remark);var old=replay(u,key,request);if(old!=null)return old;
  Row r=get(u,id);var a=catalog.spec.module(r.module()).actions().stream().filter(x->x.key().equals(action)).findFirst().orElseThrow(()->new Failure(404,"动作不存在"));
  Auth.role(u,a.role());require(r.version()==version,"记录已变更，请刷新");require(a.from().contains(r.state()),"当前状态不允许此操作");
  if(a.separate())require(!r.creator().equals(u.id()),"提交人与审批人必须分离");
  if(remark==null||remark.isBlank()||remark.length()>2000)throw new Failure(400,"填写本次操作说明（最多 2000 字）");
  validate(u,a.fields(),input);var target=new LinkedHashMap<>(r.data());String state=domain.action(this,u,r,action,input,target);
  Row changed=save(u,r,state==null?a.to():state,target,action,remark);return remember(u,key,request,changed);
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Row system(User u,String module,String code,String state,Map<String,Object> data){
  String id=UUID.randomUUID().toString();Timestamp now=Timestamp.from(Instant.now());
  db.update("INSERT INTO business_record(id,tenant,module,code,state,version,creator,payload,created_at,updated_at) VALUES(?,?,?,?,?,1,?,?,?,?)",id,u.tenant(),module,code,state,u.id(),encode(data),now,now);
  Row r=get(u,id);audit(u,id,"CREATE",Map.of(),r,"新建");return r;
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Row ledger(User u,String module,String state,Map<String,Object> data){return system(u,module,module.toUpperCase(Locale.ROOT)+"-"+UUID.randomUUID().toString().substring(0,12),state,data);}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Row save(User u,Row r,String state,Map<String,Object> data,String action,String note){
  int count=db.update("UPDATE business_record SET state=?,version=version+1,payload=?,updated_at=? WHERE id=? AND tenant=? AND version=?",state,encode(data),Timestamp.from(Instant.now()),r.id(),u.tenant(),r.version());
  require(count==1,"并发版本冲突");Row result=get(u,r.id());audit(u,r.id(),action,r,result,note);return result;
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public void audit(User u,String id,String action,Object before,Object after,String note){db.update("INSERT INTO audit_event(id,tenant,record_id,actor,action,before_json,after_json,remark,created_at) VALUES(?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),u.tenant(),id,u.username(),action,encode(before),encode(after),note,Timestamp.from(Instant.now()));}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public List<Map<String,Object>> history(User u,String id){get(u,id);return db.queryForList("SELECT actor,action,before_json,after_json,remark,created_at FROM audit_event WHERE tenant=? AND record_id=? ORDER BY created_at,id",u.tenant(),id);}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Map<String,Object> dashboard(User u){
  var modules=new ArrayList<Map<String,Object>>();for(var m:catalog.spec.modules()){
   modules.add(Map.of("key",m.key(),"label",m.label(),"count",db.queryForObject("SELECT COUNT(*) FROM business_record WHERE tenant=? AND module=?",Long.class,u.tenant(),m.key()),"states",db.queryForList("SELECT state,COUNT(*) total FROM business_record WHERE tenant=? AND module=? GROUP BY state",u.tenant(),m.key())));
  }return Map.of("modules",modules,"metrics",domain.metrics(this,u),"recent",db.queryForList("SELECT actor,action,remark,created_at FROM audit_event WHERE tenant=? ORDER BY sequence_no DESC LIMIT 12",u.tenant()));
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public String csv(User u,String module){
  var m=catalog.spec.module(module);var result=new StringBuilder("\uFEFF编号,状态,版本");
  for(var f:m.fields())result.append(',').append(cell(f.label()));result.append(",扩展业务数据\n");
  for(Row r:all(u,module)){result.append(cell(r.code())).append(',').append(cell(r.state())).append(',').append(r.version());for(var f:m.fields())result.append(',').append(cell(txt(r.data(),f.key())));result.append(',').append(cell(encode(r.data()))).append('\n');}
  return result.toString();
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 static String cell(String v){if(v.matches("^[=+@\\-\\t\\r].*"))v="'"+v;return "\""+v.replace("\"","\"\"")+"\"";}
}
