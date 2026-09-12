/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.casehub;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
@Component public class Catalog {
 public final Model.Spec spec;
 public Catalog(ObjectMapper mapper)throws Exception{try(var in=getClass().getResourceAsStream("/catalog.json")){spec=mapper.readValue(in,Model.Spec.class);}}
}
