/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.casehub;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
/**
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@Component public class Catalog {
 public final Model.Spec spec;
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Catalog(ObjectMapper mapper)throws Exception{try(var in=getClass().getResourceAsStream("/catalog.json")){spec=mapper.readValue(in,Model.Spec.class);}}
}
