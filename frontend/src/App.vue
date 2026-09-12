<!-- 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ -->
<script setup>
import {ref,computed,onMounted} from 'vue';
import {stateNames,labels,canAct,format,validField} from './model.js';
const title='知华企业案件协作', subtitle='案件受理、任务调查、证据管理与结案复核';
const token=ref(sessionStorage.getItem('casehub-session')||''),user=ref(null),catalog=ref(null),dashboard=ref(null);
const username=ref(''),password=ref(''),busy=ref(false),error=ref(''),notice=ref('');
const mode=ref(location.pathname.startsWith('/admin')?'admin':'work'),section=ref('dashboard'),rows=ref([]),total=ref(0),page=ref(1),query=ref(''),state=ref('');
const selected=ref(null),history=ref([]),attachments=ref([]),dialog=ref(null),options=ref({}),users=ref([]),audit=ref([]);
const module=computed(()=>catalog.value?.modules.find(m=>m.key===section.value));
const roleNames={ADMIN:'系统管理员',REVIEWER:'审核人员',OPERATOR:'业务人员',VIEWER:'只读用户'};
const currentTitle=computed(()=>section.value==='dashboard'?'业务概览':section.value==='users'?'账号与权限':section.value==='audit'?'操作审计':module.value?.label||'工作台');
const currentDescription=computed(()=>module.value?.description||(section.value==='dashboard'?subtitle:section.value==='users'?'最小权限分配 · 账号停用或改密后，旧会话立即失效':'查看业务变更、审批以及账号管理的操作记录'));
const columns=computed(()=>module.value?.fields.slice(0,4)||[]);
const states=computed(()=>[...new Set([module.value?.initial,...(module.value?.actions||[]).flatMap(a=>[...a.from,a.to])])].filter(Boolean));
function message(text){notice.value=text;setTimeout(()=>{notice.value='';},4500);}
async function request(path,{method='GET',body,key,raw=false}={}){
 const headers={};if(token.value)headers.Authorization='Bearer '+token.value;if(body&&!(body instanceof FormData))headers['Content-Type']='application/json';if(key)headers['Idempotency-Key']=key;
 const r=await fetch('/api'+path,{method,headers,body:body?(body instanceof FormData?body:JSON.stringify(body)):undefined});
 if(!r.ok){let m='请求失败';try{m=(await r.json()).message||m;}catch{}if(r.status===401){token.value='';user.value=null;sessionStorage.removeItem('casehub-session');}throw new Error(m);}
 return raw?r.blob():r.json();
}
async function guarded(fn){if(busy.value)return;busy.value=true;error.value='';try{await fn();}catch(e){error.value=e.message;}finally{busy.value=false;}}
async function login(){await guarded(async()=>{const r=await request('/auth/login',{method:'POST',body:{username:username.value,password:password.value}});token.value=r.token;user.value=r.user;sessionStorage.setItem('casehub-session',r.token);password.value='';await load();});}
async function logout(){await guarded(async()=>{await request('/auth/logout',{method:'POST',body:{}});token.value='';user.value=null;sessionStorage.removeItem('casehub-session');});}
async function load(){user.value=await request('/me');catalog.value=await request('/catalog');if(mode.value==='admin'&&user.value.role!=='ADMIN')mode.value='work';section.value=mode.value==='admin'?'users':'dashboard';await refresh();}
async function refresh(){
 if(section.value==='dashboard'){dashboard.value=await request('/dashboard');return;}
 if(section.value==='users'){users.value=await request('/admin/users');return;}
 if(section.value==='audit'){audit.value=await request('/admin/audit');return;}
 const r=await request('/records?'+new URLSearchParams({module:section.value,q:query.value,state:state.value,page:String(page.value),size:'20'}));rows.value=r.items;total.value=r.total;for(const field of columns.value)if(field.type==='ref')await loadOptions(field);
}
async function navigate(key){if(busy.value)return;section.value=key;page.value=1;query.value='';state.value='';selected.value=null;await guarded(refresh);}
async function switchMode(next){if(busy.value)return;mode.value=next;window.history.replaceState({},'',next==='admin'?'/admin':'/work');await navigate(next==='admin'?'users':'dashboard');}
async function openDetail(row){await guarded(async()=>{selected.value=await request('/records/'+row.id);history.value=await request('/records/'+row.id+'/history');attachments.value=await request('/attachments/'+row.id);});}
async function loadOptions(field,q=''){
 const r=await request('/records?'+new URLSearchParams({module:field.ref,q,size:'100'}));options.value[field.key]=r.items;
}
async function openForm(kind,row=null,action=null){
 await guarded(async()=>{
 const fields=kind==='action'?action.fields:module.value.fields;
 const data=Object.fromEntries(fields.map(f=>[f.key,kind==='edit'?row.data[f.key]??'':'']));
 dialog.value={kind,title:kind==='action'?action.label:kind==='edit'?'编辑'+module.value.label:'新建'+module.value.label,fields,data,code:'',remark:'',row,action,key:crypto.randomUUID()};
 for(const field of fields)if(field.type==='ref')await loadOptions(field);
 });
}
async function submit(){
 await guarded(async()=>{
 const d=dialog.value;if(d.kind==='user'){await request('/admin/users',{method:'POST',body:d.data});dialog.value=null;await refresh();message('账号已创建');return;}
 if(d.kind==='password'){await request('/admin/users/'+d.row.id,{method:'PATCH',body:{password:d.data.password}});dialog.value=null;message('密码已重置，旧会话已撤销');return;}
 if(d.fields.some(f=>!validField(f,d.data[f.key])))throw new Error('请检查必填项、数值范围和整数要求');
 const payload={data:d.data};let path='/records/'+section.value,method='POST';
 if(d.kind==='create')payload.code=d.code;
 if(d.kind==='edit'){path='/records/'+d.row.id;method='PUT';payload.version=d.row.version;}
 if(d.kind==='action'){path='/records/'+d.row.id+'/actions/'+d.action.key;payload.version=d.row.version;payload.remark=d.remark;}
 const r=await request(path,{method,body:payload,key:d.key});dialog.value=null;await refresh();selected.value=r.record;history.value=await request('/records/'+r.record.id+'/history');attachments.value=await request('/attachments/'+r.record.id);message('操作完成，已保存业务记录与审计日志');
 });
}
async function exportCsv(){await guarded(async()=>{const blob=await request('/export/'+section.value,{raw:true});download(blob,'casehub-'+section.value+'.csv');});}
function download(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
async function upload(event){const file=event.target.files[0];if(!file)return;await guarded(async()=>{const body=new FormData();body.append('file',file);await request('/attachments/'+selected.value.id,{method:'POST',body});attachments.value=await request('/attachments/'+selected.value.id);history.value=await request('/records/'+selected.value.id+'/history');message('附件已保存并记录 SHA-256 摘要');});event.target.value='';}
async function downloadFile(file){await guarded(async()=>{const blob=await request('/attachments/download/'+file.id,{raw:true});download(blob,file.filename);});}
function label(key){return module.value?.fields.find(f=>f.key===key)?.label||labels[key]||catalog.value?.modules.flatMap(m=>m.fields).find(f=>f.key===key)?.label||key;}
function display(row,field){if(field.type==='ref'){const id=row.data[field.key];return options.value[field.key]?.find(x=>x.id===id)?.code||String(id||'').slice(0,8);}return format(row.data[field.key]);}
function prettyTime(value){return value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'—';}
async function saveUser(row){await guarded(async()=>{await request('/admin/users/'+row.id,{method:'PATCH',body:{role:row.role,active:String(row.active)}});await refresh();message('权限已更新，旧会话已撤销');});}
function newUser(){dialog.value={kind:'user',title:'新建账号',fields:[{key:'username',label:'账号',type:'text',required:true},{key:'password',label:'初始化密码（至少 12 位）',type:'password',required:true},{key:'role',label:'角色',type:'select',required:true,options:['OPERATOR','REVIEWER','VIEWER','ADMIN']}],data:{username:'',password:'',role:'OPERATOR'}};}
function resetPassword(row){dialog.value={kind:'password',title:'重置 '+row.username+' 的密码',row,fields:[{key:'password',label:'新密码（至少 12 位）',type:'password',required:true}],data:{password:''}};}
onMounted(async()=>{if(token.value)await guarded(load);});
</script>
<template>
 <div v-if="!user" class="login-shell">
  <div class="login-brand"><span class="brand-mark">知</span><span>知华科技 · 企业软件</span></div>
  <main class="login-card">
   <div class="eyebrow">ENTERPRISE WORKSPACE</div><h1>{{title}}</h1><p>{{subtitle}}</p>
   <form @submit.prevent="login"><label>账号<input v-model="username" autocomplete="username" required placeholder="输入管理员分配的账号"></label><label>密码<input v-model="password" type="password" autocomplete="current-password" required placeholder="输入登录密码"></label><div v-if="error" role="alert" class="error">{{error}}</div><button class="primary wide" :disabled="busy">{{busy?'正在验证…':'登录工作台'}}</button></form>
   <div class="login-note">账号由企业管理员统一分配。业务操作、审核与配置变更均留痕。</div>
  </main><footer>上海如静知华信息科技有限公司 · <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">知华科技官网</a></footer>
 </div>
 <div v-else class="app-shell" :style="{'--accent':catalog?.accent}">
  <aside class="sidebar">
   <div class="brand"><span class="brand-mark">知</span><div><strong>{{title.replace('知华','')}}</strong><small>ZHUATECH · CASEHUB</small></div></div>
   <div class="space-label">{{mode==='work'?'业务工作台':'系统管理'}}</div>
   <nav v-if="mode==='work'" aria-label="业务模块"><button :disabled="busy" :class="{active:section==='dashboard'}" @click="navigate('dashboard')"><span class="nav-icon">▦</span>业务概览</button><button v-for="(m,index) in catalog?.modules" :key="m.key" :disabled="busy" :class="{active:section===m.key}" @click="navigate(m.key)"><span class="nav-index">{{String(index+1).padStart(2,'0')}}</span>{{m.label}}</button></nav>
   <nav v-else aria-label="系统管理"><button :disabled="busy" :class="{active:section==='users'}" @click="navigate('users')">账号与权限</button><button :disabled="busy" :class="{active:section==='audit'}" @click="navigate('audit')">操作审计</button></nav>
   <div class="sidebar-bottom"><div class="workspace-tag"><span class="dot"></span>独立企业工作空间</div><p>知华科技社区源码版</p><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">深度定制与商业授权 ↗</a></div>
  </aside>
  <div class="main-shell">
   <header class="topbar"><div class="mode-tabs"><button :disabled="busy" :class="{selected:mode==='work'}" @click="switchMode('work')">业务端</button><button v-if="user.role==='ADMIN'" :disabled="busy" :class="{selected:mode==='admin'}" @click="switchMode('admin')">管理端</button></div><div class="account"><span class="avatar">{{user.username.slice(0,1).toUpperCase()}}</span><span>{{user.username}}<small>{{roleNames[user.role]}}</small></span><button class="text-button" :disabled="busy" @click="logout">退出</button></div></header>
   <main>
    <div class="page-heading"><div><div class="breadcrumb">工作空间 <span>/</span> {{currentTitle}}</div><h1>{{currentTitle}}</h1><p>{{currentDescription}}</p></div><div class="heading-actions"><button :disabled="busy" @click="guarded(refresh)">刷新</button><button v-if="module?.creatable&&user.role!=='VIEWER'" class="primary" :disabled="busy" @click="openForm('create')">＋ 新建{{module.label}}</button><button v-if="section==='users'" class="primary" :disabled="busy" @click="newUser">＋ 新建账号</button></div></div>
    <div v-if="error" class="error global-error" role="alert">{{error}}<button class="text-button" @click="error=''">关闭</button></div><div v-if="notice" class="notice" role="status">{{notice}}</div>
    <template v-if="section==='dashboard'&&dashboard">
     <div class="metric-strip"><div v-for="(value,key) in dashboard.metrics" :key="key" class="metric"><span>{{key}}</span><strong>{{format(value)}}</strong><small>当前企业业务记录汇总</small></div></div>
     <div class="dashboard-grid"><section class="panel"><div class="panel-heading"><h2>业务处理概况</h2><span class="muted">点击模块进入业务列表</span></div><button v-for="item in dashboard.modules" :key="item.key" class="module-row" :disabled="busy" @click="navigate(item.key)"><div><strong>{{item.label}}</strong><span>{{item.states.map(s=>format(s.state)+' '+s.total).join(' · ')||'暂无业务记录'}}</span></div><b>{{item.count}}<small>条记录 →</small></b></button></section><section class="panel"><div class="panel-heading"><h2>最近操作</h2><span class="muted">审计留痕</span></div><div class="activity" v-for="(item,i) in dashboard.recent.slice(0,7)" :key="i"><span class="activity-dot"></span><div><strong>{{item.actor}} <span>{{item.action}}</span></strong><p>{{item.remark}}</p><time>{{prettyTime(item.created_at)}}</time></div></div><p v-if="!dashboard.recent.length" class="empty">暂无操作记录</p></section></div>
     <div class="info-band"><strong>操作提示</strong><span>先维护基础资料，再发起业务单据。审批必须由另一位审核人员完成；自动流水仅供查阅，不能手动改写。</span></div>
    </template>
    <section v-else-if="module" class="panel">
     <form class="toolbar" @submit.prevent="page=1;guarded(refresh)"><input v-model="query" aria-label="搜索业务记录" placeholder="搜索编号、名称或业务内容"><select v-model="state" aria-label="状态筛选" @change="page=1;guarded(refresh)"><option value="">全部状态</option><option v-for="s in states" :key="s" :value="s">{{format(s)}}</option></select><button :disabled="busy">查询</button><button type="button" class="push-right" @click="exportCsv">导出 CSV</button></form>
     <div class="table-scroll"><table><thead><tr><th>业务编号</th><th v-for="f in columns" :key="f.key">{{f.label}}</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td><button class="record-link" :disabled="busy" @click="openDetail(row)">{{row.code}}</button><small class="record-version">版本 {{row.version}}</small></td><td v-for="f in columns" :key="f.key">{{display(row,f)}}</td><td><span class="status" :data-state="row.state">{{format(row.state)}}</span></td><td class="muted nowrap">{{prettyTime(row.updatedAt)}}</td><td><button class="text-button" :disabled="busy" @click="openDetail(row)">查看</button></td></tr></tbody></table><div v-if="!rows.length" class="empty"><strong>暂无符合条件的记录</strong><p>{{module.creatable?'请调整搜索条件，或新建一条业务记录。':'业务完成后，系统会自动生成可追溯流水。'}}</p></div></div>
     <div class="pagination"><span>共 {{total}} 条 · 第 {{page}} 页</span><div><button :disabled="page===1||busy" @click="page--;guarded(refresh)">上一页</button><button :disabled="page*20>=total||busy" @click="page++;guarded(refresh)">下一页</button></div></div>
    </section>
    <section v-else-if="section==='users'" class="panel">
     <div class="panel-heading"><h2>企业账号</h2><span class="muted">管理员 / 审核人员 / 业务人员 / 只读用户</span></div><div class="table-scroll"><table><thead><tr><th>账号</th><th>角色</th><th>启用状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in users" :key="row.id"><td><strong>{{row.username}}</strong><small v-if="row.id===user.id" class="record-version">当前账号</small></td><td><select v-model="row.role" :disabled="row.id===user.id" :aria-label="row.username+' 的角色'"><option v-for="(label,key) in roleNames" :key="key" :value="key">{{label}}</option></select></td><td><label class="inline-label"><input v-model="row.active" type="checkbox" :disabled="row.id===user.id">{{row.active?'正常':'停用'}}</label></td><td><button class="text-button" :disabled="row.id===user.id||busy" @click="saveUser(row)">保存权限</button><button class="text-button" @click="resetPassword(row)">重置密码</button></td></tr></tbody></table></div><div class="table-note">停用账号或调整权限时，会撤销该账号的所有现有会话。初始化密码仅在首次建库时生效。</div>
    </section>
    <section v-else-if="section==='audit'" class="panel"><div class="panel-heading"><h2>操作审计</h2><span class="muted">最近 200 条 · 业务详情可查看完整记录历史</span></div><div class="table-scroll"><table><thead><tr><th>发生时间</th><th>操作账号</th><th>动作</th><th>业务标识</th><th>操作说明</th></tr></thead><tbody><tr v-for="(item,index) in audit" :key="index"><td class="nowrap">{{prettyTime(item.created_at)}}</td><td>{{item.actor}}</td><td><span class="audit-action">{{item.action}}</span></td><td class="muted">{{item.record_id.slice(0,12)}}</td><td>{{item.remark}}</td></tr></tbody></table></div></section>
    <footer class="page-footer">上海如静知华信息科技有限公司 <span>·</span> <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">www.zhuatech.cn</a></footer>
   </main>
  </div>
  <div v-if="selected&&module" class="drawer-backdrop" @click.self="selected=null"><section class="drawer" role="dialog" aria-modal="true" aria-label="业务详情"><div class="drawer-header"><div><small>{{module.label}} / 详情</small><h2>{{selected.code}}</h2></div><button aria-label="关闭详情" @click="selected=null">✕</button></div><div class="drawer-content"><div class="detail-status"><span class="status" :data-state="selected.state">{{format(selected.state)}}</span><span class="muted">版本 {{selected.version}}</span></div><div class="detail-actions"><button v-if="module.editable&&selected.state===module.initial&&user.role!=='VIEWER'" :disabled="busy" @click="openForm('edit',selected)">编辑资料</button><button v-for="action in module.actions.filter(a=>canAct(user,selected,a))" :key="action.key" class="primary" :disabled="busy" @click="openForm('action',selected,action)">{{action.label}}</button></div><p class="muted detail-help">仅展示当前账号与单据状态允许的操作。提交后将执行关联校验并保存审计记录。</p><h3>业务数据</h3><dl class="data-grid"><template v-for="(value,key) in selected.data" :key="key"><dt>{{label(key)}}</dt><dd><pre v-if="typeof value==='object'">{{format(value)}}</pre><template v-else>{{format(value)}}</template></dd></template></dl><h3>附件与凭证</h3><div v-for="file in attachments" :key="file.id" class="file-row"><button class="text-button" @click="downloadFile(file)">{{file.filename}}</button><small>{{Math.ceil(file.size_bytes/1024)}} KB · SHA-256 {{file.digest.slice(0,12)}}…</small></div><label v-if="user.role!=='VIEWER' && !(selected.module==='evidence' && selected.state!=='REGISTERED') && !(selected.module==='cases' && selected.state==='CLOSED')" class="upload-control">＋ 上传附件<input type="file" accept=".pdf,.png,.jpg,.jpeg,.txt,.csv,.xlsx,.docx" @change="upload" :disabled="busy"></label><p class="muted">单个文件不超过 2 MB；下载需要登录。请勿上传无关个人信息。</p><h3>操作轨迹</h3><div v-for="(event,index) in history" :key="index" class="history-row"><strong>{{event.actor}} · {{event.action}}</strong><p>{{event.remark}}</p><time>{{prettyTime(event.created_at)}}</time><details><summary>查看变更快照</summary><pre>{{event.after_json}}</pre></details></div></div></section></div>
  <div v-if="dialog" class="modal-backdrop" @click.self="!busy&&(dialog=null)"><section class="modal" role="dialog" aria-modal="true" :aria-label="dialog.title"><form @submit.prevent="submit"><div class="modal-header"><h2>{{dialog.title}}</h2><button type="button" aria-label="关闭表单" @click="dialog=null" :disabled="busy">✕</button></div><div class="modal-content"><label v-if="dialog.kind==='create'">业务编号 <b>*</b><input v-model="dialog.code" required pattern="[A-Za-z0-9_\-]{2,80}" placeholder="例如 DOC-202609-001"><small>2—80 位字母、数字、连字符；同模块不可重复。</small></label><label v-for="f in dialog.fields" :key="f.key">{{f.label}} <b v-if="f.required">*</b><template v-if="f.type==='ref'"><input :aria-label="'搜索'+f.label" placeholder="输入名称或编号搜索关联记录" @input="loadOptions(f,$event.target.value).catch(e=>error=e.message)"><select v-model="dialog.data[f.key]" :required="f.required"><option value="">请选择{{f.label}}</option><option v-for="o in options[f.key]||[]" :key="o.id" :value="o.id">{{o.code}} · {{o.data.name||o.data.product||o.data.recipient||format(o.state)}}</option></select></template><select v-else-if="f.type==='select'" v-model="dialog.data[f.key]" :required="f.required"><option value="">请选择</option><option v-for="o in f.options" :key="o" :value="o">{{roleNames[o]||format(o)}}</option></select><input v-else-if="['number','money','integer'].includes(f.type)" type="number" v-model="dialog.data[f.key]" :min="f.min" :max="f.max" :step="f.type==='integer'?1:0.0001" :required="f.required"><input v-else-if="f.type==='date'||f.type==='password'" :type="f.type" v-model="dialog.data[f.key]" :required="f.required"><textarea v-else v-model="dialog.data[f.key]" :required="f.required" maxlength="2000" rows="2"></textarea></label><label v-if="dialog.kind==='action'">本次操作说明 <b>*</b><textarea v-model="dialog.remark" rows="3" required maxlength="2000" placeholder="填写业务依据、审批意见或操作说明"></textarea></label><div v-if="error" role="alert" class="error">{{error}}</div></div><div class="modal-footer"><button type="button" :disabled="busy" @click="dialog=null">取消</button><button class="primary" :disabled="busy">{{busy?'正在保存…':'确认提交'}}</button></div></form></section></div>
 </div>
</template>
