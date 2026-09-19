/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.casehub;
import java.util.*;
/**
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
public final class Model {
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public record Field(String key,String label,String type,boolean required,String ref,List<String> options,String min,String max){}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public record Action(String key,String label,List<String> from,String to,String role,boolean separate,List<Field> fields){}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public record Module(String key,String label,String description,String initial,boolean editable,boolean creatable,List<Field> fields,List<Action> actions){}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public record Spec(String slug,String name,String subtitle,String accent,List<Module> modules){
  /**
   * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  public Module module(String key){return modules.stream().filter(m->m.key().equals(key)).findFirst().orElseThrow(()->new Failure(404,"模块不存在"));}
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public record User(String id,String tenant,String username,String role){}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public record Row(String id,String tenant,String module,String code,String state,int version,String creator,Map<String,Object> data,String createdAt,String updatedAt){}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public record Page(List<Row> items,long total,int page,int size){}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public static class Failure extends RuntimeException {public final int status;/**
                                                                                * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
                                                                                */
public Failure(int status,String message){super(message);this.status=status;}}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public static void require(boolean condition,String message){if(!condition)throw new Failure(409,message);}
}
